package com.lucho314.spotter.data.garmin.remote

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Decodes a JWT's payload without verifying its signature (Garmin issued it; Spotter only reads
 * `exp`/`client_id` as a fallback when the token endpoint's own response omits them). Rejects
 * `alg: none` tokens and returns `null` for anything malformed instead of throwing - this is a
 * best-effort read, not a trust boundary.
 */
internal fun decodeJwtPayload(token: String, json: Json): JsonObject? = try {
    val parts = token.split(".")
    if (parts.size < 2) {
        null
    } else {
        val header = json.parseToJsonElement(String(decodeBase64Url(parts[0]), Charsets.UTF_8)).jsonObject
        val alg = header["alg"]?.jsonPrimitive?.contentOrNull
        if (alg.equals("none", ignoreCase = true)) {
            null
        } else {
            json.parseToJsonElement(String(decodeBase64Url(parts[1]), Charsets.UTF_8)).jsonObject
        }
    }
} catch (e: Exception) {
    null
}

internal fun JsonObject.expClaim(): Long? = this["exp"]?.jsonPrimitive?.longOrNull

internal fun JsonObject.clientIdClaim(): String? = this["client_id"]?.jsonPrimitive?.contentOrNull

private fun decodeBase64Url(segment: String): ByteArray {
    val padded = segment.padEnd((segment.length + 3) / 4 * 4, '=')
    return Base64.getUrlDecoder().decode(padded)
}
