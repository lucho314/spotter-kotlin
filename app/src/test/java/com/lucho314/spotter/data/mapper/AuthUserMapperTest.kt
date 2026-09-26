package com.lucho314.spotter.data.mapper

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

class AuthUserMapperTest {

    private fun metadata(vararg pairs: Pair<String, String>) =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    @Test
    fun `full_name takes precedence over everything else`() {
        val metadata = metadata(
            "full_name" to "Ada Lovelace",
            "name" to "Ada",
            "display_name" to "AdaL",
        )

        assertThat(buildDisplayName(metadata, "ada@example.com")).isEqualTo("Ada Lovelace")
    }

    @Test
    fun `name is used when full_name is absent`() {
        val metadata = metadata("name" to "Ada", "display_name" to "AdaL")

        assertThat(buildDisplayName(metadata, "ada@example.com")).isEqualTo("Ada")
    }

    @Test
    fun `display_name is used when full_name and name are absent`() {
        val metadata = metadata("display_name" to "AdaL")

        assertThat(buildDisplayName(metadata, "ada@example.com")).isEqualTo("AdaL")
    }

    @Test
    fun `falls back to the email local part when metadata has no names`() {
        assertThat(buildDisplayName(JsonObject(emptyMap()), "ada@example.com")).isEqualTo("ada")
    }

    @Test
    fun `falls back to a generic name when there is no metadata or email`() {
        assertThat(buildDisplayName(null, null)).isEqualTo("Atleta")
    }

    @Test
    fun `blank metadata values are treated as absent`() {
        val metadata = metadata("full_name" to "   ")

        assertThat(buildDisplayName(metadata, "ada@example.com")).isEqualTo("ada")
    }
}
