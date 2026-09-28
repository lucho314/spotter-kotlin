package com.lucho314.spotter.data.garmin.fit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FitWriterTest {

    @Test
    fun `header is exactly 14 bytes - size, protocol, profile version LE, data size LE, dot-FIT tag and its own CRC`() {
        val writer = FitWriter()
        writer.define(0, 0, listOf(FitFieldDef(0, FitBaseType.UINT8)))
        writer.write(0, listOf(1L))
        val bytes = writer.finish()

        assertThat(bytes[0]).isEqualTo(0x0E.toByte())
        assertThat(bytes[1]).isEqualTo(0x20.toByte())
        assertThat(bytes[2]).isEqualTo(0xE1.toByte()) // profile version 21217 LE
        assertThat(bytes[3]).isEqualTo(0x52.toByte())
        assertThat(bytes[8]).isEqualTo(0x2E.toByte()) // ".FIT"
        assertThat(bytes[9]).isEqualTo(0x46.toByte())
        assertThat(bytes[10]).isEqualTo(0x49.toByte())
        assertThat(bytes[11]).isEqualTo(0x54.toByte())

        val headerCrc = FitCrc.compute(bytes, 0, 12)
        val storedHeaderCrc = (bytes[12].toInt() and 0xFF) or ((bytes[13].toInt() and 0xFF) shl 8)
        assertThat(storedHeaderCrc).isEqualTo(headerCrc)

        // data size = definition record (1 field: 3 header bytes + globalNum(2) + numFields(1) + field(3) = 9) + data record (1 + 1 byte value) = 11
        val dataSize = (bytes[4].toInt() and 0xFF) or ((bytes[5].toInt() and 0xFF) shl 8) or
            ((bytes[6].toInt() and 0xFF) shl 16) or ((bytes[7].toInt() and 0xFF) shl 24)
        assertThat(dataSize).isEqualTo(bytes.size - 14 - 2)
    }

    @Test
    fun `an event definition record matches the exact expected bytes`() {
        val writer = FitWriter()
        writer.define(
            1, 21,
            listOf(FitFieldDef(253, FitBaseType.UINT32), FitFieldDef(0, FitBaseType.ENUM), FitFieldDef(1, FitBaseType.ENUM)),
        )
        writer.write(1, listOf(0L, 0L, 0L))
        val bytes = writer.finish()

        val definitionRecord = bytes.copyOfRange(14, 14 + 15)
        val expected = byteArrayOf(
            0x41, 0x00, 0x00, 0x15, 0x00, 0x03,
            0xFD.toByte(), 0x04, 0x86.toByte(),
            0x00, 0x01, 0x00,
            0x01, 0x01, 0x00,
        )
        assertThat(definitionRecord).isEqualTo(expected)
    }

    @Test
    fun `data record values are little-endian, null becomes each type's invalid sentinel`() {
        val writer = FitWriter()
        writer.define(
            0, 0,
            listOf(
                FitFieldDef(0, FitBaseType.ENUM),
                FitFieldDef(1, FitBaseType.UINT16),
                FitFieldDef(2, FitBaseType.UINT32),
                FitFieldDef(3, FitBaseType.UINT32Z),
            ),
        )
        writer.write(0, listOf(null, null, null, null))
        val bytes = writer.finish()

        // definition record: 3 (header) + 2 (globalNum) + 1 (numFields) + 4*3 (fields) = 18 bytes
        val dataRecordStart = 14 + 18
        assertThat(bytes[dataRecordStart]).isEqualTo(0x00.toByte()) // local header, localNum 0
        assertThat(bytes[dataRecordStart + 1]).isEqualTo(0xFF.toByte()) // ENUM invalid
        assertThat(bytes[dataRecordStart + 2]).isEqualTo(0xFF.toByte()) // UINT16 invalid low byte
        assertThat(bytes[dataRecordStart + 3]).isEqualTo(0xFF.toByte()) // UINT16 invalid high byte
        val uint32Bytes = bytes.copyOfRange(dataRecordStart + 4, dataRecordStart + 8)
        assertThat(uint32Bytes).isEqualTo(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        val uint32zBytes = bytes.copyOfRange(dataRecordStart + 8, dataRecordStart + 12)
        assertThat(uint32zBytes).isEqualTo(byteArrayOf(0, 0, 0, 0))
    }

    @Test
    fun `the whole file's CRC, including its own trailing CRC, sums to 0`() {
        val writer = FitWriter()
        writer.define(0, 0, listOf(FitFieldDef(0, FitBaseType.UINT16)))
        writer.write(0, listOf(1234L))
        val bytes = writer.finish()

        assertThat(FitCrc.compute(bytes)).isEqualTo(0)
    }

    @Test
    fun `write without a matching define throws`() {
        val writer = FitWriter()
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            writer.write(0, listOf(1L))
        }
    }

    @Test
    fun `write with the wrong number of values throws`() {
        val writer = FitWriter()
        writer.define(0, 0, listOf(FitFieldDef(0, FitBaseType.UINT8), FitFieldDef(1, FitBaseType.UINT8)))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            writer.write(0, listOf(1L))
        }
    }

    @Test
    fun `a value out of range for its type throws`() {
        val writer = FitWriter()
        writer.define(0, 0, listOf(FitFieldDef(0, FitBaseType.UINT8)))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            writer.write(0, listOf(256L))
        }
    }

    @Test
    fun `finish can only be called once`() {
        val writer = FitWriter()
        writer.define(0, 0, listOf(FitFieldDef(0, FitBaseType.UINT8)))
        writer.write(0, listOf(1L))
        writer.finish()
        org.junit.Assert.assertThrows(IllegalStateException::class.java) { writer.finish() }
    }
}
