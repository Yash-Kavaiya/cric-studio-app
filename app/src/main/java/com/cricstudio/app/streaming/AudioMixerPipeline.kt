package com.cricstudio.app.streaming

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Continuous 44.1 kHz stereo PCM audio mixer + hardware AAC-LC encoder.
 * Mixes live microphone input (when permitted & enabled) with synthesized TTS commentary PCM
 * and outputs steady silent frames whenever silent so YouTube Live never stalls.
 */
class AudioMixerPipeline(
    private val onAudioConfig: (ByteArray) -> Unit,
    private val onAudioFrame: (ByteArray, Int) -> Unit,
    private val onMicLevel: (Float) -> Unit,
    private val onLog: (String) -> Unit,
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        const val SAMPLES_PER_FRAME = 1024
        const val BYTES_PER_FRAME = SAMPLES_PER_FRAME * CHANNELS * 2 // 4096 bytes
        const val AUDIO_BITRATE = 128_000
    }

    private val running = AtomicBoolean(false)
    private val micEnabled = AtomicBoolean(true)
    private val ttsQueue = ConcurrentLinkedQueue<ShortArray>()
    private var ttsOffset = 0
    private var currentTtsChunk: ShortArray? = null

    private var encoder: MediaCodec? = null
    private var audioRecord: AudioRecord? = null
    private var mixThread: Thread? = null
    private var drainThread: Thread? = null
    private var startTimeUs = 0L

    fun setMicEnabled(enabled: Boolean) {
        micEnabled.set(enabled)
        if (!enabled) onMicLevel(0f)
    }

    fun isMicEnabled(): Boolean = micEnabled.get()

    /** Enqueue stereo 44.1kHz 16-bit PCM samples (e.g. decoded from TTS WAV). */
    fun enqueueTtsPcm(stereo44kSamples: ShortArray) {
        if (!running.get() || stereo44kSamples.isEmpty()) return
        if (ttsQueue.size > 40) ttsQueue.poll()
        ttsQueue.add(stereo44kSamples)
    }

    @SuppressLint("MissingPermission")
    fun start(hasMicPermission: Boolean, initialMicEnabled: Boolean) {
        if (!running.compareAndSet(false, true)) return
        micEnabled.set(initialMicEnabled && hasMicPermission)
        startTimeUs = System.nanoTime() / 1000L

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BYTES_PER_FRAME * 2)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        encoder = codec

        if (hasMicPermission) {
            runCatching {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(SAMPLES_PER_FRAME * 4)
                val rec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf * 2,
                )
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    rec.startRecording()
                    audioRecord = rec
                    onLog("Microphone capture active (44.1 kHz)")
                } else {
                    rec.release()
                }
            }.onFailure {
                onLog("Mic unavailable (${it.message}); streaming TTS + studio audio track")
            }
        } else {
            onLog("Mic permission not granted; streaming TTS + studio audio track")
        }

        startDrainLoop(codec)
        startMixLoop(codec)
    }

    fun stop() {
        if (!running.compareAndSet(false, false)) return
        ttsQueue.clear()
        currentTtsChunk = null
        ttsOffset = 0
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        mixThread?.interrupt()
        drainThread?.interrupt()
        runCatching { encoder?.stop() }
        runCatching { encoder?.release() }
        encoder = null
        onMicLevel(0f)
    }

    private fun startMixLoop(codec: MediaCodec) {
        mixThread = Thread({
            val micMono = ShortArray(SAMPLES_PER_FRAME)
            val mixedStereo = ByteArray(BYTES_PER_FRAME)
            val frameDurationNs = (SAMPLES_PER_FRAME * 1_000_000_000L) / SAMPLE_RATE
            var frameIndex = 0L
            val loopStartNs = System.nanoTime()

            while (running.get()) {
                var micRead = 0
                val rec = audioRecord
                if (rec != null && micEnabled.get()) {
                    micRead = runCatching { rec.read(micMono, 0, SAMPLES_PER_FRAME) }.getOrDefault(0)
                }

                // Compute VU meter level + mix Mic (mono->stereo) with queued TTS stereo PCM
                var sumSq = 0.0
                val bb = ByteBuffer.wrap(mixedStereo).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until SAMPLES_PER_FRAME) {
                    val mSample = if (i < micRead && micEnabled.get()) micMono[i].toInt() else 0
                    val ttsL = nextTtsSample()
                    val ttsR = nextTtsSample()
                    val outL = (mSample + ttsL).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    val outR = (mSample + ttsR).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    bb.putShort(outL.toShort())
                    bb.putShort(outR.toShort())
                    val peak = maxOf(abs(outL), abs(outR)).toDouble() / 32768.0
                    sumSq += peak * peak
                }
                val rms = sqrt(sumSq / SAMPLES_PER_FRAME).toFloat()
                onMicLevel((rms * 3.2f).coerceIn(0f, 1f))

                try {
                    val idx = codec.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val inBuf = codec.getInputBuffer(idx)
                        if (inBuf != null) {
                            inBuf.clear()
                            inBuf.put(mixedStereo)
                            val ptsUs = (frameIndex * SAMPLES_PER_FRAME * 1_000_000L) / SAMPLE_RATE
                            codec.queueInputBuffer(idx, 0, BYTES_PER_FRAME, ptsUs, 0)
                        }
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }

                frameIndex++
                // Pace audio strictly to wall clock when mic isn't blocking reads
                val targetNs = loopStartNs + frameIndex * frameDurationNs
                val sleepNs = targetNs - System.nanoTime()
                if (sleepNs > 1_000_000L) {
                    try {
                        Thread.sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }, "CricStudio-AudioMixer").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun startDrainLoop(codec: MediaCodec) {
        drainThread = Thread({
            val info = MediaCodec.BufferInfo()
            var configSent = false
            while (running.get()) {
                try {
                    val outIdx = codec.dequeueOutputBuffer(info, 20_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val csd = codec.outputFormat.getByteBuffer("csd-0")
                            val cfg = if (csd != null && csd.remaining() > 0) {
                                ByteArray(csd.remaining()).also { csd.duplicate().get(it) }
                            } else {
                                byteArrayOf(0x12, 0x10) // AAC-LC 44.1kHz stereo fallback
                            }
                            onAudioConfig(cfg)
                            configSent = true
                        }
                        outIdx >= 0 -> {
                            val outBuf = codec.getOutputBuffer(outIdx)
                            if (outBuf != null && info.size > 0) {
                                outBuf.position(info.offset)
                                outBuf.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                outBuf.get(bytes)
                                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    onAudioConfig(bytes)
                                    configSent = true
                                } else {
                                    if (!configSent) {
                                        onAudioConfig(byteArrayOf(0x12, 0x10))
                                        configSent = true
                                    }
                                    val tsMs = (info.presentationTimeUs / 1000L).toInt().coerceAtLeast(0)
                                    onAudioFrame(bytes, tsMs)
                                }
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                        }
                    }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }, "CricStudio-AudioDrain").apply {
            start()
        }
    }

    private fun nextTtsSample(): Int {
        var chunk = currentTtsChunk
        while (chunk == null || ttsOffset >= chunk.size) {
            chunk = ttsQueue.poll()
            currentTtsChunk = chunk
            ttsOffset = 0
            if (chunk == null) return 0
        }
        return chunk[ttsOffset++].toInt()
    }
}
