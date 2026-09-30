package app.stackd.data.progression

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Mirrors web `getPrestigeStatus`: 100k lifetime XP per level, next level = level + 1. */
data class PrestigeStatus(val level: Int, val lifetimeXp: Long, val neededXp: Long) {
    val canPrestige: Boolean get() = lifetimeXp >= neededXp
}

@Serializable
private data class PrestigeRow(
    @SerialName("lifetime_xp") val lifetimeXp: Long = 0,
    @SerialName("prestige_level") val prestigeLevel: Int = 0,
)

class PrestigeRepository(private val client: SupabaseClient) {

    suspend fun status(): PrestigeStatus? {
        val uid = client.auth.currentUserOrNull()?.id ?: return null
        val row = client.postgrest.from("profiles")
            .select(Columns.list("lifetime_xp", "prestige_level")) { filter { eq("id", uid) } }
            .decodeList<PrestigeRow>().firstOrNull() ?: return null
        return PrestigeStatus(row.prestigeLevel, row.lifetimeXp, 100_000L * (row.prestigeLevel + 1))
    }

    /** Runs `prestige_up`; returns the new prestige level, or null on failure. */
    suspend fun prestigeUp(): Int? = runCatching {
        val el = client.postgrest.rpc("prestige_up").decodeAs<JsonElement>()
        val obj = (if (el is JsonArray) el.firstOrNull() else el) as? JsonObject
        obj?.get("new_prestige")?.jsonPrimitive?.intOrNull
    }.getOrNull()
}
