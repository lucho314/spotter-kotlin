package com.lucho314.spotter.data.garmin.fit

/** FIT protocol CRC-16 (see the FIT SDK's `crc.py`/`CRC.java` reference table). */
object FitCrc {
    private val TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    fun update(crc: Int, byte: Byte): Int {
        val b = byte.toInt() and 0xFF
        var tmp = TABLE[crc and 0xF]
        var c = (crc shr 4) and 0x0FFF
        c = c xor tmp xor TABLE[b and 0xF]
        tmp = TABLE[c and 0xF]
        c = (c shr 4) and 0x0FFF
        return c xor tmp xor TABLE[(b shr 4) and 0xF]
    }

    fun compute(bytes: ByteArray, from: Int = 0, toExclusive: Int = bytes.size): Int {
        var crc = 0
        for (i in from until toExclusive) crc = update(crc, bytes[i])
        return crc
    }
}
