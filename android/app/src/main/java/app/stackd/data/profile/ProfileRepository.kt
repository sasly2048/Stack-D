package app.stackd.data.profile

import app.stackd.data.room.FocusHistoryRow
import app.stackd.data.room.ProfileRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

/**
 * Reads the caller's profile and recent sessions.
 *
 * Every method here is subject to RLS — there is no elevated path on Android —
 * so a query that returns zero rows means the policy said no, not that the data
 * is missing. Where a table is locked down to definer-only access (season
 * standings, for instance) the RPC must be used instead of the table.
 */
class ProfileRepository(private val client: SupabaseClient) {

    suspend fun getProfile(userId: String): ProfileRow? =
        client.postgrest.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull()

    /**
     * Display name with the web's fallback chain: profile name, then the local
     * part of the email, then "Anon". Kept in one place so the roster, the
     * breach feed and the results screen can't disagree about what to call
     * someone.
     */
    suspend fun displayNameFor(userId: String, email: String?): String =
        getProfile(userId)?.displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore("@")?.takeIf { it.isNotBlank() }
            ?: "Anon"

    /**
     * The dashboard history: recent sessions with the embedded room reference so
     * each row can link back to its room by code. The `room:rooms(...)` join is
     * PostgREST's foreign-table embed, matching the web dashboard's select
     * string exactly.
     */
    suspend fun recentSessions(userId: String, limit: Long = 50): List<FocusHistoryRow> =
        client.postgrest.from("focus_history")
            .select(
                io.github.jan.supabase.postgrest.query.Columns.raw(
                    "id, room_id, score, xp_earned, duration_seconds, breaches_count, " +
                        "tier, created_at, room:rooms(id, code, status, started_at)",
                ),
            ) {
                filter { eq("profile_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(limit)
            }
            .decodeList()

    /**
     * Rooms the caller is in that are running right now — the dashboard's
     * "LIVE_NOW" rail. RLS already scopes `rooms` to host-or-participant, so a
     * direct query on `rooms` is equivalent to the web's participant→rooms join
     * and one round-trip shorter. Only started rooms qualify: an active row with
     * no `started_at` is mid-transition and has no elapsed time to show.
     */
    suspend fun activeSessions(): List<app.stackd.data.room.RoomRow> =
        client.postgrest.from("rooms")
            .select {
                filter {
                    eq("status", "active")
                    filterNot("started_at", io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS, null)
                }
                order("started_at", Order.DESCENDING)
            }
            .decodeList()

    suspend fun sessionForRoom(userId: String, roomId: String): FocusHistoryRow? =
        client.postgrest.from("focus_history")
            .select {
                filter {
                    eq("profile_id", userId)
                    eq("room_id", roomId)
                }
            }
            .decodeSingleOrNull()
}
