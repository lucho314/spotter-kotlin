package com.lucho314.spotter.domain.model

import java.time.Instant

data class DashboardStats(
    val sessionsThisWeek: Int,
    val lastSessionAt: Instant?,
    val latestPr: PersonalRecord?,
    val pendingSyncCount: Int,
)
