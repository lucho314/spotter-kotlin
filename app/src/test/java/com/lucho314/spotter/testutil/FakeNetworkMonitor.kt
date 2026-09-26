package com.lucho314.spotter.testutil

import com.lucho314.spotter.core.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow

class FakeNetworkMonitor(online: Boolean = true) : NetworkMonitor {
    val onlineFlow = MutableStateFlow(online)
    override val isOnline = onlineFlow
}
