package com.cricstudio.app.streaming

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Compact AMF0 encoder/decoder implementing the subset required for RTMP 1.0 publishing
 * (connect, releaseStream, FCPublish, createStream, publish, onMetaData, _result, _error, onStatus).
 */
object Amf0Codec {
    private const val TYPE_NUMBER = 0x00
    private const val TYPE_BOOLEAN = 0x01
    private const val TYPE_STRING = 0x02
    private const val TYPE_OBJECT = 0x03
    private const val TYPE_NULL = 0x05
    private const val TYPE_UNDEFINED = 0x06
    private const val TYPE_ECMA_ARRAY = 0x08
    private const val TYPE_OBJECT_END = 0x09
    private const val TYPE_STRICT_ARRAY = 0x0A

    fun encodeValues(vararg values: Any?): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        values.forEach { writeValue(dos, it) }
        return baos.toByteArray()
    }

    fun writeValue(dos: DataOutputStream, value: Any?) {
        when (value) {
            null -> dos.writeByte(TYPE_NULL)
            is Number -> {
                dos.writeByte(TYPE_NUMBER)
                dos.writeDouble(value.toDouble())
            }
            is Boolean -> {
                dos.writeByte(TYPE_BOOLEAN)
                dos.writeByte(if (value) 1 else 0)
            }
            is String -> {
                val bytes = value.toByteArray(StandardCharsets.UTF_8)
                dos.writeByte(TYPE_STRING)
                dos.writeShort(bytes.size)
                dos.write(bytes)
            }
            is EcmaMap -> {
                dos.writeByte(TYPE_ECMA_ARRAY)
                dos.writeInt(value.entries.size)
                for ((k, v) in value.entries) {
                    writeKey(dos, k)
                    writeValue(dos, v)
                }
                dos.writeShort(0)
                dos.writeByte(TYPE_OBJECT_END)
            }
            is Map<*, *> -> {
                dos.writeByte(TYPE_OBJECT)
                for ((k, v) in value) {
                    writeKey(dos, k.toString())
                    writeValue(dos, v)
                }
                dos.writeShort(0)
                dos.writeByte(TYPE_OBJECT_END)
            }
            else -> dos.writeByte(TYPE_NULL)
        }
    }

    private fun writeKey(dos: DataOutputStream, key: String) {
        val bytes = key.toByteArray(StandardCharsets.UTF_8)
        dos.writeShort(bytes.size)
        dos.write(bytes)
    }

    fun decodeAll(data: ByteArray): List<Any?> {
        val buf = ByteBuffer.wrap(data)
        val out = mutableListOf<Any?>()
        while (buf.hasRemaining()) {
            out.add(readValue(buf))
        }
        return out
    }

    private fun readValue(buf: ByteBuffer): Any? {
        if (!buf.hasRemaining()) return null
        return when (buf.get().toInt() and 0xFF) {
            TYPE_NUMBER -> if (buf.remaining() >= 8) buf.double else 0.0
            TYPE_BOOLEAN -> if (buf.hasRemaining()) buf.get().toInt() != 0 else false
            TYPE_STRING -> readString(buf)
            TYPE_OBJECT -> readObject(buf)
            TYPE_NULL, TYPE_UNDEFINED -> null
            TYPE_ECMA_ARRAY -> {
                if (buf.remaining() >= 4) buf.int // approximate count
                readObject(buf)
            }
            TYPE_STRICT_ARRAY -> {
                val len = if (buf.remaining() >= 4) buf.int else 0
                val list = ArrayList<Any?>(len.coerceIn(0, 256))
                repeat(len.coerceIn(0, 256)) { list.add(readValue(buf)) }
                list
            }
            else -> {
                buf.position(buf.limit())
                null
            }
        }
    }

    private fun readString(buf: ByteBuffer): String {
        if (buf.remaining() < 2) return ""
        val len = buf.short.toInt() and 0xFFFF
        val actual = len.coerceAtMost(buf.remaining())
        val bytes = ByteArray(actual)
        buf.get(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readObject(buf: ByteBuffer): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        while (buf.remaining() >= 3) {
            val keyLen = buf.short.toInt() and 0xFFFF
            if (keyLen == 0) {
                val endMarker = buf.get().toInt() and 0xFF
                if (endMarker == TYPE_OBJECT_END) break
            } else {
                val actual = keyLen.coerceAtMost(buf.remaining())
                val keyBytes = ByteArray(actual)
                buf.get(keyBytes)
                val key = String(keyBytes, StandardCharsets.UTF_8)
                map[key] = readValue(buf)
            }
        }
        return map
    }

    data class EcmaMap(val entries: Map<String, Any?>)
}
