package com.lucho314.spotter.data.mapper

import com.lucho314.spotter.domain.model.AuthUser
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * Precedence for the display name: `full_name` -> `name` -> `display_name` -> local part of the
 * email -> a generic fallback. Pure function so it is trivially testable without a real session.
 */
fun buildDisplayName(metadata: JsonObject?, email: String?): String {
    val fromMetadata = metadata?.stringOrNull("full_name")
        ?: metadata?.stringOrNull("name")
        ?: metadata?.stringOrNull("display_name")
    if (!fromMetadata.isNullOrBlank()) return fromMetadata
    val fromEmail = email?.substringBefore("@")?.takeIf { it.isNotBlank() }
    return fromEmail ?: "Atleta"
}

private fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.jsonPrimitive?.content

private fun buildAvatarUrl(metadata: JsonObject?): String? =
    metadata?.stringOrNull("avatar_url") ?: metadata?.stringOrNull("picture")

fun UserInfo.toDomain(): AuthUser = AuthUser(
    id = id,
    email = email,
    displayName = buildDisplayName(userMetadata, email),
    avatarUrl = buildAvatarUrl(userMetadata),
)
