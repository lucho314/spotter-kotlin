package com.lucho314.spotter.data.garmin.fit

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class FitCrcTest {

    @Test
    fun `matches the official SDK's CRC accumulated over random byte vectors`() {
        val random = Random(42)
        repeat(25) {
            val bytes = ByteArray(random.nextInt(1, 200)) { random.nextInt(256).toByte() }
            var expected = 0
            for (b in bytes) expected = com.garmin.fit.CRC.get16(expected, b)
            assertThat(FitCrc.compute(bytes)).isEqualTo(expected)
        }
    }

    @Test
    fun `crc of an empty array is 0`() {
        assertThat(FitCrc.compute(ByteArray(0))).isEqualTo(0)
    }

    @Test
    fun `appending the crc (little-endian) and recomputing gives 0`() {
        val bytes = "hello fit".toByteArray()
        val crc = FitCrc.compute(bytes)
        val withCrc = bytes + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
        assertThat(FitCrc.compute(withCrc)).isEqualTo(0)
    }
}
