package com.lucho314.spotter.data.garmin.remote

import com.google.common.truth.Truth.assertThat
import com.lucho314.spotter.data.garmin.GarminApiException
import com.lucho314.spotter.domain.model.GarminError
import com.lucho314.spotter.testutil.FakeTimeProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, listOf("application/json"))

private fun MockEngine.factory(): GarminHttpClientFactory = GarminHttpClientFactory { withCookies ->
    HttpClient(this) {
        expectSuccess = false
        if (withCookies) install(HttpCookies) { storage = AcceptAllCookiesStorage() }
    }
}

/** Header+payload only (no real signature) - decodeJwtPayload never verifies it, only rejects `alg: none`. */
private fun fakeJwt(payload: String): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
    val body = encoder.encodeToString(payload.toByteArray())
    return "$header.$body.sig"
}

class KtorGarminAuthRemoteDataSourceTest {

    // Mirrors SupabaseModule.provideJson()'s config, which is what production actually injects here.
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }
    private val timeProvider = FakeTimeProvider(instant = Instant.ofEpochSecond(1_000_000))

    private fun dataSource(handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        KtorGarminAuthRemoteDataSource(MockEngine(handler).factory(), json, timeProvider)

    @Test
    fun `SUCCESSFUL login returns a Ticket, with the exact query params and headers`() = runTest {
        var capturedUrl = ""
        var capturedUserAgent: String? = null
        var capturedOrigin: String? = null
        val ds = dataSource { request ->
            capturedUrl = request.url.toString()
            capturedUserAgent = request.headers[HttpHeaders.UserAgent]
            capturedOrigin = request.headers[HttpHeaders.Origin]
            respond(
                """{"responseStatus":{"type":"SUCCESSFUL"},"serviceTicketId":"ST-12345"}""",
                HttpStatusCode.OK,
                jsonHeaders(),
            )
        }

        val step = ds.login("a@b.com", "secret") as SsoStep.Ticket

        assertThat(step.ticket).isEqualTo("ST-12345")
        assertThat(capturedUrl).contains("clientId=${GarminEndpoints.SSO_CLIENT_ID}")
        assertThat(capturedUrl).contains("locale=${GarminEndpoints.SSO_LOCALE}")
        assertThat(capturedUserAgent).isEqualTo(GarminEndpoints.SSO_USER_AGENT)
        assertThat(capturedOrigin).isEqualTo(GarminEndpoints.SSO_BASE)
    }

    @Test
    fun `the login body has exactly the 4 expected JSON keys`() = runTest {
        var capturedBody = ""
        val ds = dataSource { request ->
            capturedBody = (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond("""{"responseStatus":{"type":"SUCCESSFUL"},"serviceTicketId":"ST-1"}""", HttpStatusCode.OK, jsonHeaders())
        }

        ds.login("a@b.com", "secret")

        val parsed = json.parseToJsonElement(capturedBody).jsonObject
        assertThat(parsed.keys).containsExactly("username", "password", "rememberMe", "captchaToken")
    }

    @Test
    fun `MFA_REQUIRED returns a session that carries the login response's cookie into verifyMfa`() = runTest {
        var calls = 0
        var verifyCookieHeader: String? = null
        val ds = dataSource { request ->
            calls++
            if (calls == 1) {
                respond(
                    """{"responseStatus":{"type":"MFA_REQUIRED"},"customerMfaInfo":{"mfaLastMethodUsed":"sms"}}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, listOf("application/json")) + headersOf("Set-Cookie", listOf("session=abc123; Path=/")),
                )
            } else {
                verifyCookieHeader = request.headers[HttpHeaders.Cookie]
                respond("""{"responseStatus":{"type":"SUCCESSFUL"},"serviceTicketId":"ST-mfa"}""", HttpStatusCode.OK, jsonHeaders())
            }
        }

        val step = ds.login("a@b.com", "secret") as SsoStep.Mfa
        assertThat(step.method).isEqualTo("sms")

        val ticket = ds.verifyMfa(step.session, step.method, "123456")

        assertThat(ticket).isEqualTo("ST-mfa")
        assertThat(verifyCookieHeader).contains("session=abc123")
    }

    @Test
    fun `INVALID_USERNAME_PASSWORD maps to InvalidCredentials`() = runTest {
        val ds = dataSource { respond("""{"responseStatus":{"type":"INVALID_USERNAME_PASSWORD"}}""", HttpStatusCode.OK, jsonHeaders()) }

        val error = runCatching { ds.login("a@b.com", "wrong") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.InvalidCredentials)
    }

    @Test
    fun `CAPTCHA_REQUIRED maps to CaptchaRequired`() = runTest {
        val ds = dataSource { respond("""{"responseStatus":{"type":"CAPTCHA_REQUIRED"}}""", HttpStatusCode.OK, jsonHeaders()) }

        val error = runCatching { ds.login("a@b.com", "pw") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.CaptchaRequired)
    }

    @Test
    fun `HTTP 429 maps to RateLimited`() = runTest {
        val ds = dataSource { respond("""{}""", HttpStatusCode.TooManyRequests, jsonHeaders()) }

        val error = runCatching { ds.login("a@b.com", "pw") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.RateLimited)
    }

    @Test
    fun `an embedded error status-code of 429 also maps to RateLimited`() = runTest {
        val ds = dataSource { respond("""{"error":{"status-code":"429"}}""", HttpStatusCode.OK, jsonHeaders()) }

        val error = runCatching { ds.login("a@b.com", "pw") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.RateLimited)
    }

    @Test
    fun `HTTP 403 maps to Blocked`() = runTest {
        val ds = dataSource { respond("""{}""", HttpStatusCode.Forbidden, jsonHeaders()) }

        val error = runCatching { ds.login("a@b.com", "pw") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.Blocked)
    }

    @Test
    fun `a non-JSON body maps to ServiceChanged with the http status`() = runTest {
        val ds = dataSource { respond("not json at all", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, listOf("text/html"))) }

        val error = runCatching { ds.login("a@b.com", "pw") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.ServiceChanged("sso_non_json:200"))
    }

    @Test
    fun `an unknown response type maps to ServiceChanged with that type`() = runTest {
        val ds = dataSource { respond("""{"responseStatus":{"type":"SOMETHING_NEW"}}""", HttpStatusCode.OK, jsonHeaders()) }

        val error = runCatching { ds.login("a@b.com", "pw") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.ServiceChanged("sso_type:SOMETHING_NEW"))
    }

    @Test
    fun `no GarminApiException message or toString leaks email, password or ticket`() = runTest {
        val ds = dataSource { respond("""{"responseStatus":{"type":"INVALID_USERNAME_PASSWORD"}}""", HttpStatusCode.OK, jsonHeaders()) }

        val error = runCatching { ds.login("secret-email@b.com", "super-secret-password") }.exceptionOrNull() as GarminApiException
        assertThat(error.message).doesNotContain("secret-email")
        assertThat(error.message).doesNotContain("super-secret-password")
        assertThat(error.toString()).doesNotContain("secret-email")
        assertThat(error.toString()).doesNotContain("super-secret-password")
    }

    @Test
    fun `exchangeTicket falls back to the second client id when the first fails, using Basic auth and the right form fields`() = runTest {
        var calls = 0
        val seenAuthHeaders = mutableListOf<String?>()
        val seenBodies = mutableListOf<String>()
        val ds = dataSource { request ->
            calls++
            seenAuthHeaders += request.headers[HttpHeaders.Authorization]
            seenBodies += (request.body as io.ktor.http.content.OutgoingContent.ByteArrayContent).bytes().decodeToString()
            if (calls == 1) {
                respond("""{"error":"bad client"}""", HttpStatusCode.BadRequest, jsonHeaders())
            } else {
                respond("""{"access_token":"acc-1","refresh_token":"ref-1","expires_in":3600}""", HttpStatusCode.OK, jsonHeaders())
            }
        }

        val tokens = ds.exchangeTicket("ST-abc")

        assertThat(tokens.accessToken).isEqualTo("acc-1")
        assertThat(tokens.expiresAtEpochSec).isEqualTo(timeProvider.instant.epochSecond + 3600)
        val expectedBasic = "Basic " + Base64.getEncoder().encodeToString("${GarminEndpoints.DI_CLIENT_IDS[1]}:".toByteArray())
        assertThat(seenAuthHeaders[1]).isEqualTo(expectedBasic)
        assertThat(seenBodies[0]).contains("client_id=${GarminEndpoints.DI_CLIENT_IDS[0]}")
        assertThat(seenBodies[0]).contains("service_ticket=ST-abc")
    }

    @Test
    fun `exchangeTicket falls back to the exp claim of the JWT when expires_in is missing`() = runTest {
        val jwt = fakeJwt("""{"exp":${timeProvider.instant.epochSecond + 999}, "client_id":"claimed-client"}""")
        val ds = dataSource { respond("""{"access_token":"$jwt"}""", HttpStatusCode.OK, jsonHeaders()) }

        val tokens = ds.exchangeTicket("ST-abc")

        assertThat(tokens.expiresAtEpochSec).isEqualTo(timeProvider.instant.epochSecond + 999)
        assertThat(tokens.clientId).isEqualTo("claimed-client")
    }

    @Test
    fun `exchangeTicket returns ServiceChanged when every client id fails`() = runTest {
        val ds = dataSource { respond("""{}""", HttpStatusCode.BadRequest, jsonHeaders()) }

        val error = runCatching { ds.exchangeTicket("ST-abc") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.ServiceChanged("di_exchange_failed"))
    }

    @Test
    fun `exchangeTicket stops at the first 429 without trying the remaining client ids`() = runTest {
        var calls = 0
        val ds = dataSource {
            calls++
            respond("""{}""", HttpStatusCode.TooManyRequests, jsonHeaders())
        }

        val error = runCatching { ds.exchangeTicket("ST-abc") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.RateLimited)
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `refresh 400 maps to ReauthRequired`() = runTest {
        val ds = dataSource { respond("""{}""", HttpStatusCode.BadRequest, jsonHeaders()) }

        val error = runCatching { ds.refresh("client-1", "refresh-1") }.exceptionOrNull() as GarminApiException
        assertThat(error.error).isEqualTo(GarminError.ReauthRequired)
    }
}

private operator fun io.ktor.http.Headers.plus(other: io.ktor.http.Headers): io.ktor.http.Headers =
    io.ktor.http.Headers.build {
        appendAll(this@plus)
        appendAll(other)
    }
