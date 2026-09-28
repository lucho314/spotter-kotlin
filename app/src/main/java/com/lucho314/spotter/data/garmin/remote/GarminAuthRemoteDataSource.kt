package com.lucho314.spotter.data.garmin.remote

import com.lucho314.spotter.core.common.TimeProvider
import com.lucho314.spotter.data.garmin.GarminApiException
import com.lucho314.spotter.domain.model.GarminError
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.parameters
import java.io.Closeable
import java.util.Base64
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Owns the cookie-jar [HttpClient] used across the SSO login -> MFA verify flow; closed once a ticket is obtained (or the flow fails/expires). */
class GarminSsoSession internal constructor(internal val client: HttpClient) : Closeable {
    override fun close() = client.close()
}

sealed interface SsoStep {
    data class Ticket(val ticket: String) : SsoStep
    data class Mfa(val session: GarminSsoSession, val method: String?) : SsoStep
}

data class DiTokens(val accessToken: String, val refreshToken: String?, val clientId: String, val expiresAtEpochSec: Long)

interface GarminAuthRemoteDataSource {
    /** Throws [GarminApiException]. */
    suspend fun login(email: String, password: String): SsoStep

    /** Returns the CAS service ticket. Throws [GarminApiException]. */
    suspend fun verifyMfa(session: GarminSsoSession, method: String?, code: String): String

    /** Throws [GarminApiException]. */
    suspend fun exchangeTicket(ticket: String): DiTokens

    /** Throws [GarminApiException]. */
    suspend fun refresh(clientId: String, refreshToken: String): DiTokens

    /** Best-effort: any error other than 401/403 returns `null` instead of throwing. Throws [GarminApiException] only for 401/403 ([GarminError.ReauthRequired]). */
    suspend fun fetchDisplayName(accessToken: String): String?
}

private const val SSO_ACCEPT = "application/json, text/plain, */*"

class KtorGarminAuthRemoteDataSource @Inject constructor(
    private val clientFactory: GarminHttpClientFactory,
    private val json: Json,
    private val timeProvider: TimeProvider,
) : GarminAuthRemoteDataSource {

    /** Cookie-less client for DI token exchange/refresh and the socialProfile call. */
    private val api by lazy { clientFactory.create(withCookies = false) }

    override suspend fun login(email: String, password: String): SsoStep {
        val session = GarminSsoSession(clientFactory.create(withCookies = true))
        try {
            val response = session.client.post(GarminEndpoints.SSO_LOGIN_URL) {
                applySsoQuery()
                applySsoHeaders()
                setBody(TextContent(json.encodeToString(SsoLoginRequest(username = email, password = password)), ContentType.Application.Json))
            }
            return handleSsoResponse(response, session)
        } catch (e: Exception) {
            session.close()
            throw e
        }
    }

    override suspend fun verifyMfa(session: GarminSsoSession, method: String?, code: String): String {
        val response = session.client.post(GarminEndpoints.SSO_MFA_VERIFY_URL) {
            applySsoQuery()
            applySsoHeaders()
            setBody(
                TextContent(
                    json.encodeToString(SsoMfaRequest(mfaMethod = method ?: "email", mfaVerificationCode = code)),
                    ContentType.Application.Json,
                ),
            )
        }
        if (response.status.value == 429) throw GarminApiException(GarminError.RateLimited)
        val body = response.bodyAsText()
        val parsed = decodeOrNull<SsoLoginResponse>(body)
            ?: throw GarminApiException(GarminError.ServiceChanged("sso_non_json:${response.status.value}"))
        val ticket = parsed.serviceTicketId
        if (parsed.responseStatus?.type == "SUCCESSFUL" && ticket != null) return ticket
        throw GarminApiException(GarminError.InvalidMfaCode)
    }

    override suspend fun exchangeTicket(ticket: String): DiTokens {
        for (clientId in GarminEndpoints.DI_CLIENT_IDS) {
            val response = api.submitForm(
                GarminEndpoints.DI_TOKEN_URL,
                formParameters = parameters {
                    append("client_id", clientId)
                    append("service_ticket", ticket)
                    append("grant_type", GarminEndpoints.DI_GRANT_SERVICE_TICKET)
                    append("service_url", GarminEndpoints.SSO_SERVICE_URL)
                },
            ) {
                applyDiHeaders(clientId)
                header(HttpHeaders.Accept, "application/json,text/html;q=0.9,*/*;q=0.8")
            }
            if (response.status.value == 429) throw GarminApiException(GarminError.RateLimited)
            if (!response.status.isSuccess2xx()) continue
            val parsed = decodeOrNull<DiTokenResponse>(response.bodyAsText()) ?: continue
            return buildDiTokens(parsed, clientId)
        }
        throw GarminApiException(GarminError.ServiceChanged("di_exchange_failed"))
    }

    override suspend fun refresh(clientId: String, refreshToken: String): DiTokens {
        val response = api.submitForm(
            GarminEndpoints.DI_TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("client_id", clientId)
                append("refresh_token", refreshToken)
            },
        ) {
            applyDiHeaders(clientId)
            header(HttpHeaders.Accept, "application/json")
        }
        return when {
            response.status.value == 400 || response.status.value == 401 -> throw GarminApiException(GarminError.ReauthRequired)
            response.status.value == 429 -> throw GarminApiException(GarminError.RateLimited)
            response.status.value in 500..599 -> throw GarminApiException(GarminError.Server(response.status.value))
            response.status.isSuccess2xx() -> {
                val parsed = decodeOrNull<DiTokenResponse>(response.bodyAsText())
                    ?: throw GarminApiException(GarminError.ServiceChanged("decode"))
                buildDiTokens(parsed, clientId)
            }
            else -> throw GarminApiException(GarminError.Server(response.status.value))
        }
    }

    override suspend fun fetchDisplayName(accessToken: String): String? {
        val response = api.get(GarminEndpoints.SOCIAL_PROFILE_URL) {
            GarminEndpoints.NATIVE_HEADERS.forEach { (k, v) -> header(k, v) }
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.Accept, "application/json")
        }
        if (response.status.value == 401 || response.status.value == 403) throw GarminApiException(GarminError.ReauthRequired)
        if (!response.status.isSuccess2xx()) return null
        val parsed = decodeOrNull<SocialProfileDto>(response.bodyAsText()) ?: return null
        return parsed.fullName ?: parsed.displayName ?: parsed.userName
    }

    private suspend fun handleSsoResponse(response: HttpResponse, session: GarminSsoSession): SsoStep {
        if (response.status.value == 429) throw GarminApiException(GarminError.RateLimited)
        if (response.status.value == 403) throw GarminApiException(GarminError.Blocked)
        val body = response.bodyAsText()
        val parsed = decodeOrNull<SsoLoginResponse>(body)
            ?: throw GarminApiException(GarminError.ServiceChanged("sso_non_json:${response.status.value}"))
        if (parsed.error?.statusCode == "429") throw GarminApiException(GarminError.RateLimited)
        val type = parsed.responseStatus?.type
        return when {
            type == "SUCCESSFUL" && parsed.serviceTicketId != null -> {
                session.close()
                SsoStep.Ticket(parsed.serviceTicketId)
            }
            type == "MFA_REQUIRED" -> {
                val method = parsed.customerMfaInfo?.mfaLastMethodUsed?.ifBlank { null } ?: "email"
                SsoStep.Mfa(session, method)
            }
            type == "INVALID_USERNAME_PASSWORD" -> {
                session.close()
                throw GarminApiException(GarminError.InvalidCredentials)
            }
            type == "CAPTCHA_REQUIRED" -> {
                session.close()
                throw GarminApiException(GarminError.CaptchaRequired)
            }
            else -> {
                session.close()
                throw GarminApiException(GarminError.ServiceChanged("sso_type:${type ?: "unknown"}"))
            }
        }
    }

    private fun buildDiTokens(response: DiTokenResponse, fallbackClientId: String): DiTokens {
        val claims = decodeJwtPayload(response.accessToken, json)
        val now = timeProvider.now().epochSecond
        val expiresAt = response.expiresIn?.let { now + it } ?: claims?.expClaim() ?: (now + 3600)
        val clientId = claims?.clientIdClaim() ?: fallbackClientId
        return DiTokens(accessToken = response.accessToken, refreshToken = response.refreshToken, clientId = clientId, expiresAtEpochSec = expiresAt)
    }

    private inline fun <reified T> decodeOrNull(text: String): T? = try {
        json.decodeFromString<T>(text)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun HttpRequestBuilder.applySsoQuery() {
        url {
            parameters.append("clientId", GarminEndpoints.SSO_CLIENT_ID)
            parameters.append("locale", GarminEndpoints.SSO_LOCALE)
            parameters.append("service", GarminEndpoints.SSO_SERVICE_URL)
        }
    }

    private fun HttpRequestBuilder.applySsoHeaders() {
        header(HttpHeaders.UserAgent, GarminEndpoints.SSO_USER_AGENT)
        header(HttpHeaders.Accept, SSO_ACCEPT)
        header(HttpHeaders.Origin, GarminEndpoints.SSO_BASE)
    }

    private fun HttpRequestBuilder.applyDiHeaders(clientId: String) {
        GarminEndpoints.NATIVE_HEADERS.forEach { (k, v) -> header(k, v) }
        val basic = Base64.getEncoder().encodeToString("$clientId:".toByteArray(Charsets.UTF_8))
        header(HttpHeaders.Authorization, "Basic $basic")
        header(HttpHeaders.CacheControl, "no-cache")
    }

    private fun HttpStatusCode.isSuccess2xx(): Boolean = value in 200..299
}
