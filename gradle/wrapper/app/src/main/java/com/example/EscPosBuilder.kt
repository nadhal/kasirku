package com.example

import java.io.ByteArrayOutputStream

/**
 * Robust helper class to construct ESC/POS command byte arrays
 * for thermal Bluetooth receipt printers.
 */
class EscPosBuilder(private val width: Int = 32) {
    private val buffer = ByteArrayOutputStream()

    fun reset(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x40))
        return this
    }

    fun alignLeft(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x00))
        return this
    }

    fun alignCenter(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x01))
        return this
    }

    fun alignRight(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x02))
        return this
    }

    fun setTextNormal(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1D, 0x21, 0x00))
        return this
    }

    fun setTextDoubleHeight(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1D, 0x21, 0x01))
        return this
    }

    fun setTextDoubleWidthAndHeight(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1D, 0x21, 0x11))
        return this
    }

    fun setBold(enabled: Boolean): EscPosBuilder {
        val b = if (enabled) 0x01.toByte() else 0x00.toByte()
        buffer.write(byteArrayOf(0x1B, 0x45, b))
        return this
    }

    fun printText(text: String): EscPosBuilder {
        try {
            // Thermal printers commonly support CP858 or GBK for standard characters
            buffer.write(text.toByteArray(charset("GBK")))
        } catch (e: Exception) {
            buffer.write(text.toByteArray(Charsets.UTF_8))
        }
        return this
    }

    fun printLine(text: String): EscPosBuilder {
        printText(text + "\n")
        return this
    }

    fun centerText(text: String): EscPosBuilder {
        val currentWidth = if (width > 0) width else 32
        val trimmed = if (text.length > currentWidth) {
            text.substring(0, currentWidth)
        } else {
            text
        }
        val leftPad = ((currentWidth - trimmed.length) / 2).coerceAtLeast(0)
        val padded = " ".repeat(leftPad) + trimmed
        printLine(padded)
        return this
    }

    fun padSides(left: String, right: String): EscPosBuilder {
        val currentWidth = if (width > 0) width else 32
        val totalLength = left.length + right.length
        if (totalLength >= currentWidth) {
            val cutLength = currentWidth - right.length - 1
            val leftTrimmed = if (cutLength > 0) {
                if (cutLength <= left.length) left.substring(0, cutLength) else left
            } else ""
            val spaceCount = currentWidth - leftTrimmed.length - right.length
            val spaces = " ".repeat(if (spaceCount > 0) spaceCount else 1)
            printLine(leftTrimmed + spaces + right)
        } else {
            val spaceCount = (currentWidth - totalLength).coerceAtLeast(0)
            val spaces = " ".repeat(spaceCount)
            printLine(left + spaces + right)
        }
        return this
    }

    fun printSeparator(char: String = "-"): EscPosBuilder {
        val currentWidth = if (width > 0) width else 32
        if (char.isNotEmpty()) {
            printLine(char.repeat(currentWidth))
        }
        return this
    }

    fun feedLines(count: Int): EscPosBuilder {
        if (count > 0) {
            buffer.write(byteArrayOf(0x1B, 0x64, count.toByte()))
        }
        return this
    }

    fun cutPaper(): EscPosBuilder {
        buffer.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        return this
    }

    fun writeRawBytes(bytes: ByteArray): EscPosBuilder {
        buffer.write(bytes)
        return this
    }

    fun build(): ByteArray {
        return buffer.toByteArray()
    }
}
