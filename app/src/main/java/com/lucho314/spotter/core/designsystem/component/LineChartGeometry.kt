package com.lucho314.spotter.core.designsystem.component

/** Pure layout math for [LineChart], kept free of Compose/Android types so it's unit-testable on the JVM. */
object LineChartGeometry {

    /** Evenly spaced x positions between [left] and [right]; a single point is centered. */
    fun xPositions(count: Int, left: Float, right: Float): List<Float> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf((left + right) / 2f)
        val step = (right - left) / (count - 1)
        return (0 until count).map { left + step * it }
    }

    /** Maps [value] in `[min, max]` to a y position in `[bottom, top]` (inverted axis); the midpoint if `max == min`. */
    fun yPosition(value: Float, min: Float, max: Float, top: Float, bottom: Float): Float {
        if (max == min) return (top + bottom) / 2f
        val ratio = (value - min) / (max - min)
        return bottom - ratio * (bottom - top)
    }

    /** Indices of the last [maxLabels] points out of [count], to avoid overlapping x-axis labels. */
    fun labelIndices(count: Int, maxLabels: Int): IntRange =
        (count - maxLabels).coerceAtLeast(0) until count
}
