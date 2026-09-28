package com.lucho314.spotter.data.garmin.remote

/**
 * All the fixed Garmin URLs/headers/ids this integration depends on, centralized so they're easy
 * to update if Garmin changes something (plan section 0, finding 2: no OAuth1/S3 consumer key -
 * garth's flow stopped working in 2026; this instead mirrors python-garminconnect's "mobile +
 * requests" strategy - plain HTTP with fixed headers, no bot-detection evasion of any kind).
 */
internal object GarminEndpoints {
    const val SSO_BASE = "https://sso.garmin.com"
    const val SSO_LOGIN_URL = "$SSO_BASE/mobile/api/login"
    const val SSO_MFA_VERIFY_URL = "$SSO_BASE/mobile/api/mfa/verifyCode"
    const val SSO_CLIENT_ID = "GCM_IOS_DARK"
    const val SSO_SERVICE_URL = "https://mobile.integration.garmin.com/gcm/ios"
    const val SSO_LOCALE = "en-US"
    const val SSO_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"

    const val DI_TOKEN_URL = "https://diauth.garmin.com/di-oauth2-service/oauth/token"
    const val DI_GRANT_SERVICE_TICKET = "https://connectapi.garmin.com/di-oauth2-service/oauth/grant/service_ticket"
    val DI_CLIENT_IDS = listOf(
        "GARMIN_CONNECT_MOBILE_ANDROID_DI_2025Q2",
        "GARMIN_CONNECT_MOBILE_ANDROID_DI_2024Q4",
        "GARMIN_CONNECT_MOBILE_ANDROID_DI",
        "GARMIN_CONNECT_MOBILE_IOS_DI",
    )

    const val CONNECT_API = "https://connectapi.garmin.com"
    const val UPLOAD_URL = "$CONNECT_API/upload-service/upload"
    const val SOCIAL_PROFILE_URL = "$CONNECT_API/userprofile-service/socialProfile"

    val NATIVE_HEADERS: Map<String, String> = mapOf(
        "User-Agent" to "GCM-Android-5.23",
        "X-Garmin-User-Agent" to "com.garmin.android.apps.connectmobile/5.23; ; Google/sdk_gphone64_arm64/google; Android/33; Dalvik/2.1.0",
        "X-Garmin-Paired-App-Version" to "10861",
        "X-Garmin-Client-Platform" to "Android",
        "X-App-Ver" to "10861",
        "X-Lang" to "en",
        "X-GCExperience" to "GC5",
        "Accept-Language" to "en-US,en;q=0.9",
    )

    const val REFRESH_MARGIN_SECONDS = 900L
}
