package com.lucho314.spotter.data.export

/**
 * ARGB [Int] colors mirroring [com.lucho314.spotter.core.designsystem.theme.SpotterColors], so
 * `data/export` renderers (`android.graphics.Canvas`/`Paint`) never need to import Compose.
 */
object ExportPalette {
    const val BACKGROUND = 0xFF0E0E0E.toInt()
    const val SURFACE_LOW = 0xFF131313.toInt()
    const val SURFACE_CONTAINER = 0xFF1A1A1A.toInt()
    const val SURFACE_HIGH = 0xFF20201F.toInt()
    const val LIME = 0xFFD1FC00.toInt()
    const val CYAN = 0xFF00E3FD.toInt()
    const val ON_SURFACE = 0xFFFFFFFF.toInt()
    const val ON_SURFACE_VARIANT = 0xFFADAAAA.toInt()
    const val OUTLINE = 0xFF767575.toInt()
    const val OUTLINE_VARIANT = 0xFF484847.toInt()
}
