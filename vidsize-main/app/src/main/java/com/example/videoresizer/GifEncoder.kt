package com.example.videoresizer

import java.io.OutputStream

/**
 * Minimal, dependency-free GIF89a encoder: writes a multi-frame animated
 * GIF using one global color table shared by every frame (a single palette
 * for the whole clip, computed by [GifExporter] before encoding starts).
 *
 * Implements the standard GIF LZW compression scheme directly against the
 * spec — variable code width starting at (paletteBits + 1), clear/end
 * control codes, a 12-bit/4096-entry dictionary ceiling with a clear-code
 * reset when it fills — rather than pulling in a third-party GIF library,
 * since this is the only place in the app that needs one.
 */
object GifEncoder {

    /**
     * @param width/height pixel size shared by every frame.
     * @param palette RGB packed as 0xRRGGBB, 1-256 entries.
     * @param frames indexed-color pixel buffers (one byte per pixel, row-major,
     *        each sized width*height), values referring into [palette].
     * @param delayCentiseconds per-frame delay in 1/100s, same for every frame.
     */
    fun encode(
        out: OutputStream,
        width: Int,
        height: Int,
        palette: IntArray,
        frames: List<ByteArray>,
        delayCentiseconds: Int,
        loopForever: Boolean = true
    ) {
        // Fans the one flat delay out across every frame and delegates to
        // the per-frame-delay variant below — GifExporter's existing call
        // site (named args, unchanged) still resolves to THIS overload, so
        // its output is byte-for-byte identical to before this was added.
        encode(out, width, height, palette, frames, List(frames.size) { delayCentiseconds }, loopForever)
    }

    /**
     * Per-frame-delay variant (added Batch 55 for GifCompressor): lets the
     * caller give each output frame its own delay instead of one flat value
     * for the whole animation. Used when frames have been merged/dropped
     * (e.g. GifCompressor's near-duplicate-frame dedup) and the surviving
     * frame needs to cover the dropped frames' original screen-time so
     * total playback duration/speed is preserved. [delays] must have one
     * entry per frame in [frames].
     */
    fun encode(
        out: OutputStream,
        width: Int,
        height: Int,
        palette: IntArray,
        frames: List<ByteArray>,
        delays: List<Int>,
        loopForever: Boolean = true
    ) {
        require(palette.isNotEmpty() && palette.size <= 256) { "palette must have 1-256 entries" }
        require(width > 0 && height > 0) { "width/height must be positive" }
        require(delays.size == frames.size) { "delays must have one entry per frame" }

        val colorBits = bitsForPaletteSize(palette.size)
        val tableSize = 1 shl colorBits

        writeAsciiHeader(out)
        writeLogicalScreenDescriptor(out, width, height, colorBits)
        writeGlobalColorTable(out, palette, tableSize)
        if (loopForever) writeNetscapeLoopExtension(out)

        for (i in frames.indices) {
            writeGraphicControlExtension(out, delays[i])
            writeImageDescriptor(out, width, height)
            writeLzwImageData(out, frames[i], colorBits)
        }

        out.write(0x3B) // GIF trailer
    }

    /** GIF's minimum LZW root code size is 2 bits even for a <=4 color palette. */
    private fun bitsForPaletteSize(paletteSize: Int): Int {
        var bits = 2
        while ((1 shl bits) < paletteSize) bits++
        return bits.coerceIn(2, 8)
    }

    private fun writeAsciiHeader(out: OutputStream) {
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
    }

    private fun writeLogicalScreenDescriptor(out: OutputStream, width: Int, height: Int, colorBits: Int) {
        writeShortLE(out, width)
        writeShortLE(out, height)
        // Packed byte: [global color table flag(1)][color resolution(3)][sort flag(1)][size of GCT(3)]
        val packed = 0x80 or ((colorBits - 1) shl 4) or (colorBits - 1)
        out.write(packed)
        out.write(0) // background color index
        out.write(0) // pixel aspect ratio (unused)
    }

    private fun writeGlobalColorTable(out: OutputStream, palette: IntArray, tableSize: Int) {
        for (i in 0 until tableSize) {
            val rgb = if (i < palette.size) palette[i] else 0
            out.write((rgb shr 16) and 0xFF)
            out.write((rgb shr 8) and 0xFF)
            out.write(rgb and 0xFF)
        }
    }

    /** Application extension telling every GIF viewer to loop the animation forever. */
    private fun writeNetscapeLoopExtension(out: OutputStream) {
        out.write(0x21) // extension introducer
        out.write(0xFF) // application extension label
        out.write(11)   // fixed block size for the identifier below
        out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        out.write(3)    // sub-block size
        out.write(1)    // loop sub-block id
        writeShortLE(out, 0) // 0 = loop indefinitely
        out.write(0)    // block terminator
    }

    private fun writeGraphicControlExtension(out: OutputStream, delayCentiseconds: Int) {
        out.write(0x21) // extension introducer
        out.write(0xF9) // graphic control label
        out.write(4)    // block size
        // Packed byte: reserved(3) + disposal method(3)=1 "do not dispose" + user input(1) + transparent color(1)
        out.write(0x04)
        writeShortLE(out, delayCentiseconds.coerceIn(1, 65535))
        out.write(0) // transparent color index (unused, no transparency)
        out.write(0) // block terminator
    }

    private fun writeImageDescriptor(out: OutputStream, width: Int, height: Int) {
        out.write(0x2C) // image separator
        writeShortLE(out, 0) // left
        writeShortLE(out, 0) // top
        writeShortLE(out, width)
        writeShortLE(out, height)
        out.write(0x00) // no local color table, not interlaced
    }

    private fun writeShortLE(out: OutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    /**
     * Standard GIF/LZW compression of one frame's indexed pixel buffer.
     * Root codes 0 until (2^minCodeSize - 1) are the palette indices
     * themselves; new codes are assigned starting right after the reserved
     * clear/end codes, and the dictionary resets with a fresh clear code
     * once it hits the 4096-entry ceiling (12-bit max per the GIF spec).
     */
    private fun writeLzwImageData(out: OutputStream, pixels: ByteArray, colorBits: Int) {
        val minCodeSize = colorBits.coerceIn(2, 8)
        out.write(minCodeSize)

        val clearCode = 1 shl minCodeSize
        val endCode = clearCode + 1

        var codeSize = minCodeSize + 1
        var maxCode = (1 shl codeSize) - 1
        var nextCode = endCode + 1
        var dictionary = HashMap<Long, Int>()

        val bitWriter = GifLzwBitWriter(out)
        bitWriter.writeCode(clearCode, codeSize)

        if (pixels.isNotEmpty()) {
            var prefixCode = pixels[0].toInt() and 0xFF
            for (i in 1 until pixels.size) {
                val k = pixels[i].toInt() and 0xFF
                val key = (prefixCode.toLong() shl 8) or k.toLong()
                val existing = dictionary[key]
                if (existing != null) {
                    prefixCode = existing
                    continue
                }
                bitWriter.writeCode(prefixCode, codeSize)
                if (nextCode < 4096) {
                    dictionary[key] = nextCode
                    nextCode++
                    if (nextCode > maxCode && codeSize < 12) {
                        codeSize++
                        maxCode = (1 shl codeSize) - 1
                    }
                } else {
                    // Dictionary full — reset with a fresh clear code, per
                    // the standard GIF LZW convention.
                    bitWriter.writeCode(clearCode, codeSize)
                    dictionary = HashMap()
                    codeSize = minCodeSize + 1
                    maxCode = (1 shl codeSize) - 1
                    nextCode = endCode + 1
                }
                prefixCode = k
            }
            bitWriter.writeCode(prefixCode, codeSize)
        }
        bitWriter.writeCode(endCode, codeSize)
        bitWriter.flush()

        out.write(0) // terminates the image data's sub-block sequence
    }
}

/**
 * Packs variable-width LZW codes into bytes LSB-first (per GIF spec), and
 * buffers the resulting bytes into GIF data sub-blocks — max 255 bytes
 * each, every sub-block preceded by its own length byte — flushing as they
 * fill up.
 */
private class GifLzwBitWriter(private val out: OutputStream) {
    private var bitBuffer = 0
    private var bitCount = 0
    private val subBlock = ByteArray(255)
    private var subBlockLength = 0

    fun writeCode(code: Int, codeSize: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += codeSize
        while (bitCount >= 8) {
            emitByte((bitBuffer and 0xFF).toByte())
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    private fun emitByte(b: Byte) {
        subBlock[subBlockLength] = b
        subBlockLength++
        if (subBlockLength == 255) flushSubBlock()
    }

    private fun flushSubBlock() {
        if (subBlockLength > 0) {
            out.write(subBlockLength)
            out.write(subBlock, 0, subBlockLength)
            subBlockLength = 0
        }
    }

    /** Flushes any partial byte (zero-padded, per spec) and any pending sub-block. */
    fun flush() {
        if (bitCount > 0) {
            emitByte((bitBuffer and 0xFF).toByte())
            bitBuffer = 0
            bitCount = 0
        }
        flushSubBlock()
    }
}
