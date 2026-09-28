package com.lucho314.spotter.data.garmin.fit

/** The handful of FIT base types this hand-written encoder needs, with their wire size and "invalid" sentinel. */
enum class FitBaseType(val id: Int, val size: Int, val invalid: Long) {
    ENUM(0x00, 1, 0xFF),
    UINT8(0x02, 1, 0xFF),
    UINT16(0x84, 2, 0xFFFF),
    UINT32(0x86, 4, 0xFFFFFFFFL),
    UINT32Z(0x8C, 4, 0L),
}

data class FitFieldDef(val num: Int, val type: FitBaseType)
