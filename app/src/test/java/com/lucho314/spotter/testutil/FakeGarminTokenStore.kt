package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.garmin.local.GarminTokenStore
import com.lucho314.spotter.data.garmin.local.StoredGarminTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeGarminTokenStore(initial: StoredGarminTokens? = null) : GarminTokenStore {
    private val state = MutableStateFlow(initial)
    override val tokens: Flow<StoredGarminTokens?> = state

    override suspend fun load(): StoredGarminTokens? = state.value

    override suspend fun save(tokens: StoredGarminTokens) {
        state.value = tokens
    }

    override suspend fun clear() {
        state.value = null
    }
}
