package com.cricstudio.app.streaming

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.Random
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

/**
 * Pure-Kotlin RTMP 1.0 / RTMPS (TLS) live publisher compatible with YouTube Live,
 * Facebook Live, Twitch, X, and custom RTMP ingest servers.
 */
class RtmpPublisher(
    private val rawUrl: String,
    private val rawStreamKey: String,
    private val onLog: (String) -> Unit = {},
) {
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private val writeLock = Any()

    private val connected = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val bytesSent = AtomicLong(0L)

    private var outChunkSize = 4096
    private var inChunkSize = 128
    private var publishStreamId = 1

    private var readerThread: Thread? = null
    private val pendingTx = ConcurrentHashMap<Int, CompletableFuture<List<Any?>>>()
    private val publishStartFuture = CompletableFuture<Result<Unit>>()

    data class ParsedEndpoint(
        val scheme: String,
        val host: String,
        val port: Int,
        val app: String,
        val streamKey: String,
        val tcUrl: String,
    )

    companion object {
        private const val CHUNK_SIZE_OUT = 4096
        private const val CSID_PROTOCOL = 2
        private const val CSID_COMMAND = 3
        private const val CSID_AUDIO = 4
        private const val CSID_VIDEO = 6

        private const val MSG_SET_CHUNK_SIZE = 1
        private const val MSG_ABORT = 2
        private const val MSG_ACK = 3
        private const val MSG_USER_CONTROL = 4
        private const val MSG_WINDOW_ACK_SIZE = 5
        private const val MSG_SET_PEER_BW = 6
        private const val MSG_AUDIO = 8
        private const val MSG_VIDEO = 9
        private const val MSG_DATA_AMF0 = 18
        private const val MSG_COMMAND_AMF0 = 20

        fun parseEndpoint(rawUrl: String, rawKey: String): ParsedEndpoint {
            val cleanedUrl = rawUrl.trim()
            val cleanedKey = rawKey.trim()
            val uri = URI(cleanedUrl)
            val scheme = (uri.scheme ?: "rtmp").lowercase()
            require(scheme == "rtmp" || scheme == "rtmps") {
                "URL must start with rtmp:// or rtmps://"
            }
            val host = uri.host?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Missing RTMP host in URL")
            val defaultPort = if (scheme == "rtmps") 443 else 1935
            val port = if (uri.port > 0) uri.port else defaultPort

            val rawPath = (uri.path ?: "").trim('/')
            val app: String
            val finalKey: String
            if (cleanedKey.isNotEmpty()) {
                app = rawPath.ifEmpty { "live2" }
                finalKey = cleanedKey
            } else {
                val slash = rawPath.lastIndexOf('/')
                if (slash > 0) {
                    app = rawPath.substring(0, slash)
                    finalKey = rawPath.substring(slash + 1)
                } else {
                    throw IllegalArgumentException("Stream key is required")
                }
            }
            val portSuffix = if (port == defaultPort) "" else ":$port"
            val tcUrl = "$scheme://$host$portSuffix/$app"
            return ParsedEndpoint(scheme, host, port, app, finalKey, tcUrl)
        }
    }

    val isConnected: Boolean
        get() = connected.get() && !closed.get()

    fun drainBytesSent(): Long = bytesSent.getAndSet(0L)

    fun connectAndPublish(width: Int, height: Int, videoBitrateKbps: Int, fps: Int) {
        val ep = parseEndpoint(rawUrl, rawStreamKey)
        onLog("Connecting to ${ep.host}:${ep.port}/${ep.app}...")

        val rawSocket = Socket()
        rawSocket.tcpNoDelay = true
        rawSocket.soTimeout = 15_000
        rawSocket.connect(InetSocketAddress(ep.host, ep.port), 10_000)

        val activeSocket: Socket = if (ep.scheme == "rtmps") {
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            factory.createSocket(rawSocket, ep.host, ep.port, true).apply {
                soTimeout = 15_000
            }
        } else {
            rawSocket
        }

        socket = activeSocket
        input = DataInputStream(BufferedInputStream(activeSocket.getInputStream(), 32_768))
        output = DataOutputStream(BufferedOutputStream(activeSocket.getOutputStream(), 65_536))

        performHandshake()
        onLog("RTMP handshake complete")

        // Start server reader thread before sending AMF0 commands
        startReaderLoop()

        // Increase outbound chunk size for lower overhead
        sendSetChunkSize(CHUNK_SIZE_OUT)
        outChunkSize = CHUNK_SIZE_OUT

        // 1. connect
        val connectProps = linkedMapOf<String, Any?>(
            "app" to ep.app,
            "type" to "nonprivate",
            "flashVer" to "FMLE/3.0 (compatible; CricStudio/1.0)",
            "tcUrl" to ep.tcUrl,
            "fpad" to false,
            "capabilities" to 15.0,
            "audioCodecs" to 0x0400.toDouble(), // SUPPORT_SND_AAC
            "videoCodecs" to 0x0080.toDouble(), // SUPPORT_VID_H264
            "videoFunction" to 1.0,
        )
        val connectReply = sendCommandAndAwait(1, "connect", connectProps)
        checkCommandResult("connect", connectReply)
        onLog("Connected to ${ep.app}")

        // 2. releaseStream & FCPublish (expected by YouTube/FMLE)
        sendCommand(CSID_COMMAND, 0, Amf0Codec.encodeValues("releaseStream", 2.0, null, ep.streamKey))
        sendCommand(CSID_COMMAND, 0, Amf0Codec.encodeValues("FCPublish", 3.0, null, ep.streamKey))

        // 3. createStream
        val createReply = sendCommandAndAwait(4, "createStream", null)
        checkCommandResult("createStream", createReply)
        publishStreamId = (createReply.getOrNull(3) as? Number)?.toInt()?.takeIf { it > 0 } ?: 1

        // 4. publish
        sendCommand(
            csId = CSID_COMMAND,
            streamId = publishStreamId,
            payload = Amf0Codec.encodeValues("publish", 0.0, null, ep.streamKey, "live"),
        )
        val publishResult = try {
            publishStartFuture.get(10, TimeUnit.SECONDS)
        } catch (_: Exception) {
            Result.failure(IOException("Timed out waiting for server to accept stream key"))
        }
        publishResult.getOrThrow()

        // 5. @setDataFrame / onMetaData
        val meta = Amf0Codec.EcmaMap(
            linkedMapOf(
                "duration" to 0.0,
                "width" to width.toDouble(),
                "height" to height.toDouble(),
                "videodatarate" to videoBitrateKbps.toDouble(),
                "framerate" to fps.toDouble(),
                "videocodecid" to 7.0,
                "audiodatarate" to 128.0,
                "audiosamplerate" to 44100.0,
                "audiosamplesize" to 16.0,
                "stereo" to true,
                "audiocodecid" to 10.0,
                "encoder" to "CricStudio Android RTMP",
            ),
        )
        sendMessage(
            csId = CSID_COMMAND,
            timestampMs = 0,
            msgType = MSG_DATA_AMF0,
            streamId = publishStreamId,
            payload = Amf0Codec.encodeValues("@setDataFrame", "onMetaData", meta),
        )

        activeSocket.soTimeout = 0
        connected.set(true)
        onLog("Publishing live stream to ${ep.host}")
    }

    fun sendVideoSequenceHeader(avcConfigRecord: ByteArray) {
        if (!isConnected) return
        val payload = ByteArray(5 + avcConfigRecord.size)
        payload[0] = 0x17.toByte() // keyframe (1) + AVC (7)
        payload[1] = 0x00.toByte() // AVC sequence header
        payload[2] = 0x00.toByte()
        payload[3] = 0x00.toByte()
        payload[4] = 0x00.toByte()
        System.arraycopy(avcConfigRecord, 0, payload, 5, avcConfigRecord.size)
        sendMessage(CSID_VIDEO, 0, MSG_VIDEO, publishStreamId, payload)
    }

    fun sendVideoFrame(avccData: ByteArray, isKeyFrame: Boolean, timestampMs: Int) {
        if (!isConnected) return
        val payload = ByteArray(5 + avccData.size)
        payload[0] = (if (isKeyFrame) 0x17 else 0x27).toByte()
        payload[1] = 0x01.toByte() // AVC NALU
        payload[2] = 0x00.toByte()
        payload[3] = 0x00.toByte()
        payload[4] = 0x00.toByte()
        System.arraycopy(avccData, 0, payload, 5, avccData.size)
        sendMessage(CSID_VIDEO, timestampMs.coerceAtLeast(0), MSG_VIDEO, publishStreamId, payload)
    }

    fun sendAudioSequenceHeader(audioSpecificConfig: ByteArray) {
        if (!isConnected) return
        val payload = ByteArray(2 + audioSpecificConfig.size)
        payload[0] = 0xAF.toByte() // AAC (10) | 44kHz (3) | 16-bit (1) | Stereo (1)
        payload[1] = 0x00.toByte() // AAC sequence header
        System.arraycopy(audioSpecificConfig, 0, payload, 2, audioSpecificConfig.size)
        sendMessage(CSID_AUDIO, 0, MSG_AUDIO, publishStreamId, payload)
    }

    fun sendAudioFrame(rawAac: ByteArray, timestampMs: Int) {
        if (!isConnected) return
        val payload = ByteArray(2 + rawAac.size)
        payload[0] = 0xAF.toByte()
        payload[1] = 0x01.toByte() // AAC raw
        System.arraycopy(rawAac, 0, payload, 2, rawAac.size)
        sendMessage(CSID_AUDIO, timestampMs.coerceAtLeast(0), MSG_AUDIO, publishStreamId, payload)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        connected.set(false)
        runCatching {
            if (socket?.isConnected == true && socket?.isClosed == false) {
                sendCommand(CSID_COMMAND, 0, Amf0Codec.encodeValues("FCUnpublish", 0.0, null, rawStreamKey))
                sendCommand(CSID_COMMAND, 0, Amf0Codec.encodeValues("deleteStream", 0.0, null, publishStreamId.toDouble()))
            }
        }
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket?.close() }
        readerThread?.interrupt()
        pendingTx.values.forEach { it.cancel(true) }
        pendingTx.clear()
        if (!publishStartFuture.isDone) {
            publishStartFuture.complete(Result.failure(IOException("Stream closed")))
        }
    }

    private fun performHandshake() {
        val out = output ?: throw IOException("Output stream unavailable")
        val inp = input ?: throw IOException("Input stream unavailable")

        val c1 = ByteArray(1536)
        Random().nextBytes(c1)
        for (i in 0 until 8) c1[i] = 0

        synchronized(writeLock) {
            out.writeByte(0x03) // C0: RTMP version 3
            out.write(c1)       // C1
            out.flush()
        }

        val s0 = inp.readByte().toInt() and 0xFF
        if (s0 != 0x03) throw IOException("Unsupported RTMP server version: $s0")

        val s1 = ByteArray(1536)
        inp.readFully(s1)
        val s2 = ByteArray(1536)
        inp.readFully(s2)

        synchronized(writeLock) {
            out.write(s1) // C2 echoes S1
            out.flush()
        }
    }

    private fun sendSetChunkSize(size: Int) {
        val payload = ByteArray(4)
        payload[0] = ((size shr 24) and 0x7F).toByte()
        payload[1] = ((size shr 16) and 0xFF).toByte()
        payload[2] = ((size shr 8) and 0xFF).toByte()
        payload[3] = (size and 0xFF).toByte()
        sendMessage(CSID_PROTOCOL, 0, MSG_SET_CHUNK_SIZE, 0, payload)
    }

    private fun sendCommandAndAwait(txId: Int, name: String, cmdObj: Any?): List<Any?> {
        val future = CompletableFuture<List<Any?>>()
        pendingTx[txId] = future
        sendCommand(CSID_COMMAND, 0, Amf0Codec.encodeValues(name, txId.toDouble(), cmdObj))
        return try {
            future.get(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            pendingTx.remove(txId)
            throw IOException("RTMP '$name' timed out: ${e.message ?: "no response"}", e)
        }
    }

    private fun checkCommandResult(command: String, values: List<Any?>) {
        val tag = values.firstOrNull() as? String
        if (tag == "_error") {
            val info = values.getOrNull(3) as? Map<*, *>
            val desc = info?.get("description")?.toString() ?: info?.get("code")?.toString() ?: "Rejected by server"
            throw IOException("RTMP $command failed: $desc")
        }
    }

    private fun sendCommand(csId: Int, streamId: Int, payload: ByteArray) {
        sendMessage(csId, 0, MSG_COMMAND_AMF0, streamId, payload)
    }

    private fun sendMessage(csId: Int, timestampMs: Int, msgType: Int, streamId: Int, payload: ByteArray) {
        val out = output ?: throw IOException("Not connected")
        synchronized(writeLock) {
            if (closed.get()) throw IOException("Publisher closed")
            val useExtTs = timestampMs >= 0xFFFFFF
            val ts24 = if (useExtTs) 0xFFFFFF else timestampMs

            var offset = 0
            var firstChunk = true
            while (offset < payload.size || firstChunk) {
                if (firstChunk) {
                    // Type 0 full header (12 bytes + optional 4-byte ext timestamp)
                    out.writeByte(csId and 0x3F)
                    out.writeByte((ts24 shr 16) and 0xFF)
                    out.writeByte((ts24 shr 8) and 0xFF)
                    out.writeByte(ts24 and 0xFF)
                    out.writeByte((payload.size shr 16) and 0xFF)
                    out.writeByte((payload.size shr 8) and 0xFF)
                    out.writeByte(payload.size and 0xFF)
                    out.writeByte(msgType and 0xFF)
                    // Little-endian stream ID
                    out.writeByte(streamId and 0xFF)
                    out.writeByte((streamId shr 8) and 0xFF)
                    out.writeByte((streamId shr 16) and 0xFF)
                    out.writeByte((streamId shr 24) and 0xFF)
                    if (useExtTs) out.writeInt(timestampMs)
                    firstChunk = false
                } else {
                    // Type 3 continuation header (1 byte + optional ext ts)
                    out.writeByte(0xC0 or (csId and 0x3F))
                    if (useExtTs) out.writeInt(timestampMs)
                }

                val chunkLen = (payload.size - offset).coerceAtMost(outChunkSize)
                if (chunkLen > 0) {
                    out.write(payload, offset, chunkLen)
                    offset += chunkLen
                }
            }
            out.flush()
            bytesSent.addAndGet(payload.size.toLong() + 12L)
        }
    }

    private data class ChunkStreamState(
        var timestamp: Int = 0,
        var msgLength: Int = 0,
        var msgType: Int = 0,
        var streamId: Int = 0,
        var buffer: ByteArrayOutputStream = ByteArrayOutputStream(),
    )

    private fun startReaderLoop() {
        readerThread = Thread({
            val inp = input ?: return@Thread
            val states = HashMap<Int, ChunkStreamState>()
            try {
                while (!closed.get()) {
                    val basic = inp.readUnsignedByte()
                    val fmt = (basic shr 6) and 0x03
                    var csId = basic and 0x3F
                    if (csId == 0) csId = 64 + inp.readUnsignedByte()
                    else if (csId == 1) csId = 64 + inp.readUnsignedByte() + 256 * inp.readUnsignedByte()

                    val st = states.getOrPut(csId) { ChunkStreamState() }
                    when (fmt) {
                        0 -> {
                            val ts = readUint24(inp)
                            st.msgLength = readUint24(inp)
                            st.msgType = inp.readUnsignedByte()
                            st.streamId = readIntLE(inp)
                            st.timestamp = if (ts == 0xFFFFFF) inp.readInt() else ts
                            st.buffer = ByteArrayOutputStream(st.msgLength.coerceAtLeast(16))
                        }
                        1 -> {
                            val delta = readUint24(inp)
                            st.msgLength = readUint24(inp)
                            st.msgType = inp.readUnsignedByte()
                            st.timestamp += if (delta == 0xFFFFFF) inp.readInt() else delta
                            st.buffer = ByteArrayOutputStream(st.msgLength.coerceAtLeast(16))
                        }
                        2 -> {
                            val delta = readUint24(inp)
                            st.timestamp += if (delta == 0xFFFFFF) inp.readInt() else delta
                        }
                        3 -> {
                            // Continuation chunk; no header fields
                        }
                    }

                    val remaining = st.msgLength - st.buffer.size()
                    if (remaining > 0) {
                        val toRead = remaining.coerceAtMost(inChunkSize)
                        val chunk = ByteArray(toRead)
                        inp.readFully(chunk)
                        st.buffer.write(chunk)
                    }

                    if (st.buffer.size() >= st.msgLength) {
                        val msgBytes = st.buffer.toByteArray()
                        st.buffer = ByteArrayOutputStream()
                        handleIncomingMessage(st.msgType, msgBytes)
                    }
                }
            } catch (e: Exception) {
                if (!closed.get()) {
                    connected.set(false)
                    if (!publishStartFuture.isDone) {
                        publishStartFuture.complete(Result.failure(e))
                    }
                    pendingTx.values.forEach { it.completeExceptionally(e) }
                }
            }
        }, "CricStudio-RtmpReader").apply {
            isDaemon = true
            start()
        }
    }

    private fun handleIncomingMessage(msgType: Int, data: ByteArray) {
        when (msgType) {
            MSG_SET_CHUNK_SIZE -> {
                if (data.size >= 4) {
                    val sz = ((data[0].toInt() and 0x7F) shl 24) or
                        ((data[1].toInt() and 0xFF) shl 16) or
                        ((data[2].toInt() and 0xFF) shl 8) or
                        (data[3].toInt() and 0xFF)
                    if (sz in 1..65536) inChunkSize = sz
                }
            }
            MSG_USER_CONTROL -> {
                if (data.size >= 6) {
                    val eventType = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    if (eventType == 6) { // PingRequest -> reply PingResponse (7)
                        val pong = data.copyOf()
                        pong[0] = 0
                        pong[1] = 7
                        runCatching { sendMessage(CSID_PROTOCOL, 0, MSG_USER_CONTROL, 0, pong) }
                    }
                }
            }
            MSG_WINDOW_ACK_SIZE -> {
                runCatching { sendMessage(CSID_PROTOCOL, 0, MSG_WINDOW_ACK_SIZE, 0, data.copyOf(4)) }
            }
            MSG_COMMAND_AMF0 -> {
                val values = runCatching { Amf0Codec.decodeAll(data) }.getOrDefault(emptyList())
                val cmd = values.firstOrNull() as? String ?: return
                val txId = (values.getOrNull(1) as? Number)?.toInt() ?: 0
                when (cmd) {
                    "_result", "_error" -> {
                        pendingTx.remove(txId)?.complete(values)
                    }
                    "onStatus" -> {
                        val info = values.getOrNull(3) as? Map<*, *>
                        val code = info?.get("code")?.toString().orEmpty()
                        val desc = info?.get("description")?.toString().orEmpty()
                        onLog("Server status: $code ${desc.take(60)}".trim())
                        when {
                            code.contains("Publish.Start", ignoreCase = true) -> {
                                publishStartFuture.complete(Result.success(Unit))
                            }
                            code.contains("BadName", ignoreCase = true) ||
                                code.contains("Rejected", ignoreCase = true) ||
                                code.contains("Failed", ignoreCase = true) ||
                                code.contains("Error", ignoreCase = true) -> {
                                publishStartFuture.complete(
                                    Result.failure(IOException("Stream key rejected ($code): $desc")),
                                )
                            }
                        }
                    }
                }
            }
            MSG_ABORT, MSG_ACK, MSG_SET_PEER_BW -> Unit
        }
    }

    private fun readUint24(inp: DataInputStream): Int {
        val b0 = inp.readUnsignedByte()
        val b1 = inp.readUnsignedByte()
        val b2 = inp.readUnsignedByte()
        return (b0 shl 16) or (b1 shl 8) or b2
    }

    private fun readIntLE(inp: DataInputStream): Int {
        val b0 = inp.readUnsignedByte()
        val b1 = inp.readUnsignedByte()
        val b2 = inp.readUnsignedByte()
        val b3 = inp.readUnsignedByte()
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }
}
