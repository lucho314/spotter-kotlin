package com.lucho314.spotter.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LineChartGeometryTest {

    @Test
    fun `a single point is centered`() {
        assertThat(LineChartGeometry.xPositions(1, 0f, 100f)).containsExactly(50f)
    }

    @Test
    fun `multiple points are evenly spaced`() {
        assertThat(LineChartGeometry.xPositions(3, 0f, 100f)).containsExactly(0f, 50f, 100f).inOrder()
    }

    @Test
    fun `yPosition goes to the middle when min equals max`() {
        assertThat(LineChartGeometry.yPosition(5f, min = 5f, max = 5f, top = 0f, bottom = 100f)).isEqualTo(50f)
    }

    @Test
    fun `yPosition maps the value range onto the inverted y axis`() {
        assertThat(LineChartGeometry.yPosition(0f, min = 0f, max = 10f, top = 0f, bottom = 100f)).isEqualTo(100f)
        assertThat(LineChartGeometry.yPosition(10f, min = 0f, max = 10f, top = 0f, bottom = 100f)).isEqualTo(0f)
    }

    @Test
    fun `labelIndices keeps only the last maxLabels`() {
        assertThat(LineChartGeometry.labelIndices(12, 8)).isEqualTo(4 until 12)
        assertThat(LineChartGeometry.labelIndices(3, 8)).isEqualTo(0 until 3)
    }
}
