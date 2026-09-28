package com.lucho314.spotter.testutil

import com.lucho314.spotter.data.garmin.remote.DiTokens
import com.lucho314.spotter.data.garmin.remote.GarminAuthRemoteDataSource
import com.lucho314.spotter.data.garmin.remote.GarminSsoSession
import com.lucho314.spotter.data.garmin.remote.SsoStep
import kotlinx.coroutines.CompletableDeferred

class FakeGarminAuthRemoteDataSource : GarminAuthRemoteDataSource {
    var loginResult: SsoStep = SsoStep.Ticket("ticket-1")
    var loginException: Throwable? = null

    var verifyMfaResult: String = "ticket-1"
    var verifyMfaException: Throwable? = null

    var exchangeTicketResult: DiTokens = DiTokens("access-1", "refresh-1", "client-1", Long.MAX_VALUE)
    var exchangeTicketException: Throwable? = null

    var refreshResult: DiTokens = DiTokens("access-2", "refresh-2", "client-1", Long.MAX_VALUE)
    var refreshException: Throwable? = null

    var fetchDisplayNameResult: String? = "Ada"
    var fetchDisplayNameException: Throwable? = null

    var refreshCallCount = 0
        private set

    /** Set to make [refresh] suspend until this completes - used to test [com.lucho314.spotter.data.garmin.GarminTokenManager]'s Mutex. */
    var refreshGate: CompletableDeferred<Unit>? = null

    override suspend fun login(email: String, password: String): SsoStep {
        loginException?.let { throw it }
        return loginResult
    }

    override suspend fun verifyMfa(session: GarminSsoSession, method: String?, code: String): String {
        verifyMfaException?.let { throw it }
        return verifyMfaResult
    }

    override suspend fun exchangeTicket(ticket: String): DiTokens {
        exchangeTicketException?.let { throw it }
        return exchangeTicketResult
    }

    override suspend fun refresh(clientId: String, refreshToken: String): DiTokens {
        refreshCallCount++
        refreshGate?.await()
        refreshException?.let { throw it }
        return refreshResult
    }

    override suspend fun fetchDisplayName(accessToken: String): String? {
        fetchDisplayNameException?.let { throw it }
        return fetchDisplayNameResult
    }
}
