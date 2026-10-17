package com.humming.keyboard

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reading a stream with a hard size cap, so an oversized response is rejected without buffering all of it. */
object BoundedRead {
    /** Reads up to [maxBytes] + 1 bytes from [input]; a result longer than [maxBytes] means the source was too large. */
    fun read(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() <= maxBytes) {
            val n = input.read(buf, 0, minOf(buf.size, maxBytes + 1 - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
