package com.lucho314.spotter.domain.repository

/**
 * Wraps every local (Room) table so [com.lucho314.spotter.domain.usecase.SignOutUseCase] can clear
 * them without the domain layer importing Room/Android directly (ADR: the domain layer stays
 * platform-agnostic).
 */
interface LocalDataRepository {
    /** Deletes ALL local storage: the active workout, the offline outbox and the read cache. Only meant for sign-out. */
    suspend fun clearAll()
}
