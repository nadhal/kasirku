package com.example

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class EscPosBuilderTest {

    @Test
    fun testResetCommand() {
        val builder = EscPosBuilder().reset()
        assertArrayEquals(byteArrayOf(0x1B, 0x40), builder.build())
    }

    @Test
    fun testAlignLeftCommand() {
        val builder = EscPosBuilder().alignLeft()
        assertArrayEquals(byteArrayOf(0x1B, 0x61, 0x00), builder.build())
    }
}
