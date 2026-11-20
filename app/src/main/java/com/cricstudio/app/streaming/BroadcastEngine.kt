package com.cricstudio.app.streaming

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.cricstudio.app.compositor.BroadcastCanvasRenderer
import com.cricstudio.app.model.BroadcastStatus
import com.cricstudio.app.model.CompositorSnapshot
import com.cricstudio.app.model.DestinationStatus
import com.cricstudio.app.model.StreamDestination
import com.cricstudio.app.model.StreamQuality
import com.cricstudio.app.model.StreamTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end hardware H.264 + AAC live broadcast engine.
 * Renders the dynamic 16:9 BroadcastCanvasRenderer directly onto MediaCodec's InputSurface
 * at 30fps and fans out encoded AVC/AAC packets to all enabled RTMP/RTMPS destinations.
 */
class BroadcastEngine(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderer = BroadcastCanvasRenderer(appContext)

    private val _telemetry = MutableStateFlow(StreamTelemetry())
    val telemetry: StateFlow<StreamTelemetry> = _telemetry.asStateFlow()

    private val latestSnapshot = AtomicReference<CompositorSnapshot?>(null)
    private val running = AtomicBoolean(false)

    private var videoEncoder: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var audioPipeline: AudioMixerPipeline? = null

    private var renderThread: Thread? = null
    private var videoDrainThread: Thread? = null
    private var telemetryJob: Job? = null
    private val destJobs = ConcurrentHashMap<String, Job>()
    private val publishers = ConcurrentHashMap<String, RtmpPublisher>()

    @Volatile private var cachedAvcConfig: ByteArray? = null
    @Volatile private var cachedAudioConfig: ByteArray? = null
    @Volatile private var sessionStartMs = 0L
    @Volatile private var activeQuality = StreamQuality.HD_720P

    private val encodedFrameCounter = AtomicInteger(0)
    private val droppedFrameCounter = AtomicInteger(0)
    private val latestMicLevel = AtomicReference(0f)

    fun updateSnapshot(snapshot: CompositorSnapshot) {
        latestSnapshot.set(snapshot)
    }

    fun enqueueTtsPcm(stereo44kSamples: ShortArray) {
        audioPipeline?.enqueueTtsPcm(stereo44kSamples)
    }

    fun setMicEnabled(enabled: Boolean) {
        audioPipeline?.setMicEnabled(enabled)
        _telemetry.update { it.copy(micEnabled = enabled) }
    }

    fun start(
        destinations: List<StreamDestination>,
        quality: StreamQuality,
        hasMicPermission: Boolean,
        micEnabled: Boolean,
    ) {
        if (running.get()) stop()
        val enabledDests = destinations.filter { it.enabled && it.url.isNotBlank() }
        if (enabledDests.isEmpty()) {
            _telemetry.update {
                it.copy(
                    status = BroadcastStatus.ERROR,
                    lastError = "Add and enable at least one RTMP destination with a stream key.",
                )
            }
            return
        }

        running.set(true)
        activeQuality = quality
        sessionStartMs = System.currentTimeMillis()
        cachedAvcConfig = null
        cachedAudioConfig = null
        encodedFrameCounter.set(0)
        droppedFrameCounter.set(0)

        val initialStatuses = enabledDests.associate { d ->
            d.id to DestinationStatus(d.id, d.name, BroadcastStatus.CONNECTING, "Connecting...")
        }
        _telemetry.value = StreamTelemetry(
            status = BroadcastStatus.CONNECTING,
            micEnabled = micEnabled && hasMicPermission,
            quality = quality,
            destinationStatuses = initialStatuses,
            logs = listOf(stamp("Starting ${quality.label} H.264/AAC broadcast...")),
        )

        try {
            startVideoEncoder(quality)
            startAudioPipeline(hasMicPermission, micEnabled)
        } catch (e: Exception) {
            appendLog("Encoder initialization failed: ${e.message}")
            _telemetry.update {
                it.copy(
                    status = BroadcastStatus.ERROR,
                    lastError = "Hardware encoder error: ${e.message ?: "unsupported format"}",
                )
            }
            stop()
            return
        }

        enabledDests.forEach { dest ->
            destJobs[dest.id] = scope.launch { runDestinationLoop(dest, quality) }
        }
        startTelemetryTicker()
    }

    fun stop() {
        running.set(false)
        telemetryJob?.cancel()
        telemetryJob = null

        destJobs.values.forEach { it.cancel() }
        destJobs.clear()
        publishers.values.forEach { runCatching { it.close() } }
        publishers.clear()

        audioPipeline?.stop()
        audioPipeline = null

        renderThread?.interrupt()
        videoDrainThread?.interrupt()
        runCatching { videoEncoder?.signalEndOfInputStream() }
        runCatching { videoEncoder?.stop() }
        runCatching { videoEncoder?.release() }
        runCatching { inputSurface?.release() }
        videoEncoder = null
        inputSurface = null

        _telemetry.update {
            it.copy(
                status = BroadcastStatus.IDLE,
                bitrateKbps = 0,
                fps = 0,
                elapsedSeconds = 0L,
                micLevel = 0f,
                destinationStatuses = it.destinationStatuses.mapValues { (_, ds) ->
                    ds.copy(status = BroadcastStatus.IDLE, message = "Stopped", bitrateKbps = 0)
                },
            )
        }
        appendLog("Broadcast stopped")
    }

    private fun startVideoEncoder(quality: StreamQuality) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, quality.width, quality.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, quality.videoBitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, quality.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2) // YouTube Live requires 2s GOP
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            }
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = codec.createInputSurface()
        codec.start()

        videoEncoder = codec
        inputSurface = surface
        appendLog("H.264 encoder ready (${quality.width}×${quality.height} @ ${quality.fps}fps)")

        startVideoDrainLoop(codec)
        startCanvasRenderLoop(surface, quality)
    }

    private fun startAudioPipeline(hasMicPermission: Boolean, micEnabled: Boolean) {
        val pipeline = AudioMixerPipeline(
            onAudioConfig = { cfg ->
                cachedAudioConfig = cfg
                publishers.values.forEach { pub ->
                    if (pub.isConnected) runCatching { pub.sendAudioSequenceHeader(cfg) }
                }
            },
            onAudioFrame = { aac, tsMs ->
                publishers.values.forEach { pub ->
                    if (pub.isConnected) {
                        runCatching { pub.sendAudioFrame(aac, tsMs) }
                    }
                }
            },
            onMicLevel = { lvl -> latestMicLevel.set(lvl) },
            onLog = { msg -> appendLog(msg) },
        )
        audioPipeline = pipeline
        pipeline.start(hasMicPermission, micEnabled)
    }

    private fun startCanvasRenderLoop(surface: Surface, quality: StreamQuality) {
        renderThread = Thread({
            val frameIntervalNs = 1_000_000_000L / quality.fps
            var nextFrameNs = System.nanoTime()

            while (running.get()) {
                val snap = latestSnapshot.get()
                if (snap != null && surface.isValid) {
                    val canvas = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            surface.lockHardwareCanvas()
                        } else {
                            surface.lockCanvas(null)
                        }
                    } catch (_: Exception) {
                        runCatching { surface.lockCanvas(null) }.getOrNull()
                    }
                    if (canvas != null) {
                        try {
                            val now = System.currentTimeMillis()
                            val elapsed = ((now - sessionStartMs) / 1000L).coerceAtLeast(0L)
                            renderer.drawFrame(
                                canvas = canvas,
                                width = quality.width,
                                height = quality.height,
                                snapshot = snap.copy(
                                    isStreaming = true,
                                    elapsedSeconds = elapsed,
                                    micLevel = latestMicLevel.get(),
                                ),
                                nowMs = now,
                            )
                        } finally {
                            runCatching { surface.unlockCanvasAndPost(canvas) }
                        }
                    }
                }

                nextFrameNs += frameIntervalNs
                val sleepNs = nextFrameNs - System.nanoTime()
                if (sleepNs > 1_000_000L) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                    } catch (_: InterruptedException) {
                        break
                    }
                } else if (sleepNs < -frameIntervalNs * 3) {
                    nextFrameNs = System.nanoTime()
                }
            }
        }, "CricStudio-CanvasRender").apply {
            priority = Thread.NORM_PRIORITY + 2
            start()
        }
    }

    private fun startVideoDrainLoop(codec: MediaCodec) {
        videoDrainThread = Thread({
            val info = MediaCodec.BufferInfo()
            var basePtsUs = -1L
            while (running.get()) {
                try {
                    val outIdx = codec.dequeueOutputBuffer(info, 25_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val cfg = buildAvcConfigFromFormat(codec.outputFormat)
                            if (cfg != null) {
                                cachedAvcConfig = cfg
                                publishers.values.forEach { pub ->
                                    if (pub.isConnected) runCatching { pub.sendVideoSequenceHeader(cfg) }
                                }
                            }
                        }
                        outIdx >= 0 -> {
                            val buf = codec.getOutputBuffer(outIdx)
                            if (buf != null && info.size > 0) {
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                val raw = ByteArray(info.size)
                                buf.get(raw)

                                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    val cfg = buildAvcConfigFromAnnexB(raw)
                                    if (cfg != null) {
                                        cachedAvcConfig = cfg
                                        publishers.values.forEach { pub ->
                                            if (pub.isConnected) runCatching { pub.sendVideoSequenceHeader(cfg) }
                                        }
                                    }
                                } else {
                                    if (basePtsUs < 0L) basePtsUs = info.presentationTimeUs
                                    val tsMs = ((info.presentationTimeUs - basePtsUs) / 1000L).toInt().coerceAtLeast(0)
                                    val isKey = (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                                    val avcc = annexBToAvcc(raw)
                                    if (avcc.isNotEmpty()) {
                                        encodedFrameCounter.incrementAndGet()
                                        publishers.values.forEach { pub ->
                                            if (pub.isConnected) {
                                                runCatching { pub.sendVideoFrame(avcc, isKey, tsMs) }
                                                    .onFailure { droppedFrameCounter.incrementAndGet() }
                                            }
                                        }
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                        }
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }, "CricStudio-VideoDrain").apply {
            start()
        }
    }

    private suspend fun runDestinationLoop(dest: StreamDestination, quality: StreamQuality) {
        var attempt = 0
        val backoffs = listOf(2_000L, 4_000L, 8_000L, 15_000L)

        while (running.get() && scope.isActive) {
            val status = if (attempt == 0) BroadcastStatus.CONNECTING else BroadcastStatus.RECONNECTING
            updateDestinationStatus(dest, status, if (attempt == 0) "Connecting to ${dest.platform.label}..." else "Reconnecting (attempt ${attempt + 1})...")

            val publisher = RtmpPublisher(
                rawUrl = dest.url,
                rawStreamKey = dest.streamKey,
                onLog = { msg -> appendLog("[${dest.name}] $msg") },
            )
            try {
                publisher.connectAndPublish(
                    width = quality.width,
                    height = quality.height,
                    videoBitrateKbps = quality.videoBitrateBps / 1000,
                    fps = quality.fps,
                )
                publishers[dest.id] = publisher
                attempt = 0

                // Push cached sequence headers immediately so server locks onto H.264/AAC
                cachedAvcConfig?.let { publisher.sendVideoSequenceHeader(it) }
                cachedAudioConfig?.let { publisher.sendAudioSequenceHeader(it) }

                updateDestinationStatus(dest, BroadcastStatus.LIVE, "Streaming live")
                while (running.get() && publisher.isConnected) {
                    delay(500L)
                }
                if (running.get()) {
                    throw IllegalStateException("Connection dropped by server")
                }
            } catch (e: Exception) {
                publishers.remove(dest.id)
                runCatching { publisher.close() }
                if (!running.get()) break

                val msg = e.message ?: "Network error"
                appendLog("[${dest.name}] Error: $msg")
                val fatalKeyError = msg.contains("rejected", ignoreCase = true) ||
                    msg.contains("BadName", ignoreCase = true) ||
                    msg.contains("Stream key is required", ignoreCase = true)

                if (fatalKeyError) {
                    updateDestinationStatus(dest, BroadcastStatus.ERROR, msg)
                    _telemetry.update { it.copy(lastError = "${dest.name}: $msg") }
                    break
                } else {
                    updateDestinationStatus(dest, BroadcastStatus.RECONNECTING, "Disconnected: $msg")
                    val waitMs = backoffs[attempt.coerceAtMost(backoffs.lastIndex)]
                    attempt++
                    delay(waitMs)
                }
            } finally {
                publishers.remove(dest.id)
                runCatching { publisher.close() }
            }
        }
    }

    private fun startTelemetryTicker() {
        telemetryJob?.cancel()
        telemetryJob = scope.launch {
            var lastSampleMs = System.currentTimeMillis()
            while (running.get() && isActive) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                val dtSec = ((now - lastSampleMs) / 1000.0).coerceAtLeast(0.25)
                lastSampleMs = now

                val fps = (encodedFrameCounter.getAndSet(0) / dtSec).toInt()
                var totalKbps = 0
                val destMap = _telemetry.value.destinationStatuses.toMutableMap()
                publishers.forEach { (id, pub) ->
                    val kbps = ((pub.drainBytesSent() * 8.0) / (dtSec * 1000.0)).toInt()
                    totalKbps += kbps
                    destMap[id]?.let { existing ->
                        destMap[id] = existing.copy(bitrateKbps = kbps)
                    }
                }

                val anyLive = destMap.values.any { it.status == BroadcastStatus.LIVE }
                val anyReconnecting = destMap.values.any { it.status == BroadcastStatus.RECONNECTING }
                val allError = destMap.isNotEmpty() && destMap.values.all { it.status == BroadcastStatus.ERROR }
                val overall = when {
                    anyLive -> BroadcastStatus.LIVE
                    anyReconnecting -> BroadcastStatus.RECONNECTING
                    allError -> BroadcastStatus.ERROR
                    else -> BroadcastStatus.CONNECTING
                }

                _telemetry.update {
                    it.copy(
                        status = overall,
                        bitrateKbps = totalKbps,
                        fps = fps,
                        droppedFrames = droppedFrameCounter.get(),
                        elapsedSeconds = ((now - sessionStartMs) / 1000L).coerceAtLeast(0L),
                        micLevel = latestMicLevel.get(),
                        destinationStatuses = destMap,
                    )
                }
            }
        }
    }

    private fun updateDestinationStatus(dest: StreamDestination, status: BroadcastStatus, message: String) {
        _telemetry.update { current ->
            val updated = current.destinationStatuses.toMutableMap()
            updated[dest.id] = DestinationStatus(
                destinationId = dest.id,
                name = dest.name,
                status = status,
                message = message,
                bitrateKbps = if (status == BroadcastStatus.LIVE) updated[dest.id]?.bitrateKbps ?: 0 else 0,
            )
            val overall = when {
                updated.values.any { it.status == BroadcastStatus.LIVE } -> BroadcastStatus.LIVE
                updated.values.any { it.status == BroadcastStatus.RECONNECTING } -> BroadcastStatus.RECONNECTING
                updated.values.all { it.status == BroadcastStatus.ERROR } -> BroadcastStatus.ERROR
                else -> BroadcastStatus.CONNECTING
            }
            current.copy(
                status = overall,
                destinationStatuses = updated,
                lastError = if (status == BroadcastStatus.ERROR) message else current.lastError,
            )
        }
    }

    private fun appendLog(msg: String) {
        _telemetry.update { it.copy(logs = (listOf(stamp(msg)) + it.logs).take(30)) }
    }

    private fun stamp(msg: String): String {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        return "$ts  $msg"
    }

    // ---- H.264 Annex-B -> AVCC / AVCDecoderConfigurationRecord helpers ----

    private fun buildAvcConfigFromFormat(format: MediaFormat): ByteArray? {
        val csd0 = format.getByteBuffer("csd-0") ?: return null
        val csd1 = format.getByteBuffer("csd-1")
        val b0 = ByteArray(csd0.remaining()).also { csd0.duplicate().get(it) }
        val b1 = csd1?.let { ByteArray(it.remaining()).also { arr -> it.duplicate().get(arr) } }
        val nalus = splitAnnexBNalus(b0) + (b1?.let { splitAnnexBNalus(it) } ?: emptyList())
        return buildAvcDecoderConfig(nalus)
    }

    private fun buildAvcConfigFromAnnexB(data: ByteArray): ByteArray? {
        return buildAvcDecoderConfig(splitAnnexBNalus(data))
    }

    private fun buildAvcDecoderConfig(nalus: List<ByteArray>): ByteArray? {
        val sps = nalus.firstOrNull { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 7 } ?: return null
        val pps = nalus.firstOrNull { it.isNotEmpty() && (it[0].toInt() and 0x1F) == 8 } ?: return null
        if (sps.size < 4) return null

        val out = ByteArrayOutputStream()
        out.write(1)                  // configurationVersion
        out.write(sps[1].toInt() and 0xFF) // AVCProfileIndication
        out.write(sps[2].toInt() and 0xFF) // profile_compatibility
        out.write(sps[3].toInt() and 0xFF) // AVCLevelIndication
        out.write(0xFF)               // lengthSizeMinusOne = 3 (4-byte lengths)
        out.write(0xE1)               // numOfSequenceParameterSets = 1
        out.write((sps.size shr 8) and 0xFF)
        out.write(sps.size and 0xFF)
        out.write(sps)
        out.write(1)                  // numOfPictureParameterSets = 1
        out.write((pps.size shr 8) and 0xFF)
        out.write(pps.size and 0xFF)
        out.write(pps)
        return out.toByteArray()
    }

    private fun annexBToAvcc(data: ByteArray): ByteArray {
        val nalus = splitAnnexBNalus(data)
        if (nalus.isEmpty()) return ByteArray(0)
        val out = ByteArrayOutputStream(data.size + 16)
        for (nalu in nalus) {
            if (nalu.isEmpty()) continue
            val type = nalu[0].toInt() and 0x1F
            if (type == 7 || type == 8) continue // SPS/PPS already sent in sequence header
            out.write((nalu.size shr 24) and 0xFF)
            out.write((nalu.size shr 16) and 0xFF)
            out.write((nalu.size shr 8) and 0xFF)
            out.write(nalu.size and 0xFF)
            out.write(nalu)
        }
        return out.toByteArray()
    }

    private fun splitAnnexBNalus(data: ByteArray): List<ByteArray> {
        val starts = mutableListOf<Int>()
        var i = 0
        while (i < data.size - 2) {
            if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                if (data[i + 2] == 1.toByte()) {
                    starts.add(i)
                    i += 3
                    continue
                } else if (i + 3 < data.size && data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()) {
                    starts.add(i)
                    i += 4
                    continue
                }
            }
            i++
        }
        if (starts.isEmpty()) return listOf(data)
        val result = ArrayList<ByteArray>(starts.size)
        for (idx in starts.indices) {
            val start = starts[idx]
            val headerLen = if (data[start + 2] == 1.toByte()) 3 else 4
            val contentStart = start + headerLen
            val contentEnd = if (idx + 1 < starts.size) starts[idx + 1] else data.size
            if (contentEnd > contentStart) {
                result.add(data.copyOfRange(contentStart, contentEnd))
            }
        }
        return result
    }
}
