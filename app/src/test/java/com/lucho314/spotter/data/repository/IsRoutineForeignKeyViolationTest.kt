package com.lucho314.spotter.data.repository

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.core.common.AppError
import org.junit.Test

class IsRoutineForeignKeyViolationTest {

    @Test
    fun `Server(23503) is a routine foreign-key violation`() {
        assertThat(isRoutineForeignKeyViolation(AppError.Server(code = "23503"))).isTrue()
    }

    @Test
    fun `a different Server code is not`() {
        assertThat(isRoutineForeignKeyViolation(AppError.Server(code = "42501"))).isFalse()
        assertThat(isRoutineForeignKeyViolation(AppError.Server(code = null))).isFalse()
    }

    @Test
    fun `non-Server errors are not`() {
        assertThat(isRoutineForeignKeyViolation(AppError.Network)).isFalse()
        assertThat(isRoutineForeignKeyViolation(AppError.Unauthorized)).isFalse()
        assertThat(isRoutineForeignKeyViolation(AppError.Unknown(null))).isFalse()
    }
}
