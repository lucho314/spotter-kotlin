package com.lucho314.spotter.data.garmin.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import javax.inject.Inject

private const val REQUEST_TIMEOUT_MS = 60_000L
private const val CONNECT_TIMEOUT_MS = 15_000L

fun interface GarminHttpClientFactory {
    /** [withCookies]: the SSO login/MFA flow needs a cookie jar; every other Garmin call is stateless. */
    fun create(withCookies: Boolean): HttpClient
}

/**
 * `socketTimeoutMillis` is set explicitly for the same reason as `SupabaseAiImportRemoteDataSource`:
 * Ktor 3.5.1's OkHttp engine only overrides `OkHttpClient.Builder.readTimeout` when
 * `socketTimeoutMillis` is set - otherwise OkHttp's own 10s default applies regardless of
 * `requestTimeoutMillis`.
 */
class OkHttpGarminHttpClientFactory @Inject constructor() : GarminHttpClientFactory {
    override fun create(withCookies: Boolean): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        followRedirects = true
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
        if (withCookies) {
            install(HttpCookies) { storage = AcceptAllCookiesStorage() }
        }
    }
}
