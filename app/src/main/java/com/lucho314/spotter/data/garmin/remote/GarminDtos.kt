package com.lucho314.spotter.data.garmin.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SsoLoginRequest(
    val username: String,
    val password: String,
    val rememberMe: Boolean = true,
    val captchaToken: String = "",
)

@Serializable
internal data class SsoMfaRequest(
    val mfaMethod: String,
    val mfaVerificationCode: String,
    val rememberMyBrowser: Boolean = true,
    val reconsentList: List<String> = emptyList(),
    val mfaSetup: Boolean = false,
)

@Serializable
internal data class SsoResponseStatus(val type: String? = null)

@Serializable
internal data class SsoMfaInfo(val mfaLastMethodUsed: String? = null)

@Serializable
internal data class SsoErrorBody(@SerialName("status-code") val statusCode: String? = null)

@Serializable
internal data class SsoLoginResponse(
    val responseStatus: SsoResponseStatus? = null,
    val serviceTicketId: String? = null,
    val customerMfaInfo: SsoMfaInfo? = null,
    val error: SsoErrorBody? = null,
)

@Serializable
internal data class DiTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
internal data class SocialProfileDto(
    val fullName: String? = null,
    val displayName: String? = null,
    val userName: String? = null,
)

@Serializable
internal data class ImportMessage(val code: Int? = null, val content: String? = null)

@Serializable
internal data class ImportItem(val internalId: Long? = null, val messages: List<ImportMessage>? = null)

@Serializable
internal data class UploadUuid(val uuid: String? = null)

@Serializable
internal data class DetailedImportResult(
    val uploadId: Long? = null,
    val uploadUuid: UploadUuid? = null,
    val successes: List<ImportItem> = emptyList(),
    val failures: List<ImportItem> = emptyList(),
)

@Serializable
internal data class UploadResponse(val detailedImportResult: DetailedImportResult? = null)
