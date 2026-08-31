package app.stackd.data.trust

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Trust & safety — web's `trust.functions.ts` + `moderation.functions.ts`.
 *
 * Every call is a direct RLS-scoped table op. `user_reports` and `user_blocks`
 * are owner-scoped for reads/writes; the host moderation view rides two extra
 * policies ("host reads/resolves room reports") that admit the host of the
 * reported room.
 *
 * KNOWN SERVER GAP: `resolveReport` writes `user_reports.status`, and the RLS
 * "host resolves room reports" UPDATE policy exists — but `authenticated` is
 * only GRANTed SELECT/INSERT on the table (migration 20260723033539), never
 * UPDATE. So resolve/dismiss is rejected at the privilege layer for the web
 * too, not just Android. The write below is correct and will start working the
 * moment a `GRANT UPDATE (status) ON public.user_reports TO authenticated`
 * lands; until then it surfaces the failure instead of pretending it worked.
 */

/* --------------------------------- Types --------------------------------- */

@Serializable
internal data class BlockRow(
    @SerialName("blocked_id") val blockedId: String,
    @SerialName("created_at") val createdAt: String,
)

data class BlockedUser(val userId: String, val displayName: String?, val createdAt: String)

@Serializable
internal data class ReportRow(
    val id: String,
    val kind: String,
    val reason: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("reporter_id") val reporterId: String? = null,
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("target_room_id") val targetRoomId: String? = null,
)

/** A report the caller filed — the Trust screen's "your reports" list. */
data class MyReport(
    val id: String,
    val kind: String,
    val reason: String?,
    val status: String,
    val createdAt: String,
)

/** A report on a room the caller hosts — the moderation dashboard. */
data class HostReport(
    val id: String,
    val kind: String,
    val reason: String?,
    val status: String,
    val createdAt: String,
    val targetUserId: String?,
    val targetRoomId: String?,
    val roomCode: String?,
    val reporterName: String?,
    val targetName: String?,
)

@Serializable
internal data class ProfileNameRow(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
)

@Serializable
internal data class HostRoomRow(val id: String, val code: String)

class TrustRepository(private val client: SupabaseClient) {

    /* ------------------------------- Reports ------------------------------ */

    /** Files a report against a user or a room. Exactly one target is required. */
    suspend fun fileReport(
        reporterId: String,
        kind: String,
        reason: String? = null,
        targetUserId: String? = null,
        targetRoomId: String? = null,
    ): String {
        require(targetUserId != null || targetRoomId != null) { "no_target" }
        return client.postgrest.from("user_reports").insert(
            buildJsonObject {
                put("reporter_id", reporterId)
                put("kind", kind.take(40))
                reason?.takeIf { it.isNotBlank() }?.let { put("reason", it.take(500)) }
                targetUserId?.let { put("target_user_id", it) }
                targetRoomId?.let { put("target_room_id", it) }
            },
        ) { select(Columns.list("id")) }.decodeSingle<ReportRow>().id
    }

    /**
     * Files a report against a room named by its human code (what a user can
     * actually read off a lobby). Resolves the code to an id first; returns
     * null if no such room exists, so the UI can say "no room by that code"
     * rather than filing a dangling report.
     */
    suspend fun reportRoomByCode(reporterId: String, code: String, kind: String, reason: String?): Boolean {
        val roomId = client.postgrest.from("rooms")
            .select(Columns.list("id", "code")) {
                filter { eq("code", code.trim().uppercase()) }
                limit(1)
            }
            .decodeList<HostRoomRow>()
            .firstOrNull()?.id ?: return false
        fileReport(reporterId, kind, reason, targetRoomId = roomId)
        return true
    }

    suspend fun listMyReports(userId: String): List<MyReport> =
        client.postgrest.from("user_reports")
            .select(Columns.list("id", "kind", "reason", "status", "created_at")) {
                filter { eq("reporter_id", userId) }
                order("created_at", Order.DESCENDING)
                limit(50)
            }
            .decodeList<ReportRow>()
            .map { MyReport(it.id, it.kind, it.reason, it.status, it.createdAt) }

    /* -------------------------------- Blocks ------------------------------ */

    suspend fun listBlocks(userId: String): List<BlockedUser> {
        val blocks = client.postgrest.from("user_blocks")
            .select(Columns.list("blocked_id", "created_at")) {
                filter { eq("blocker_id", userId) }
            }
            .decodeList<BlockRow>()
        if (blocks.isEmpty()) return emptyList()

        val names = client.postgrest.from("profiles")
            .select(Columns.list("id", "display_name")) {
                filter { isIn("id", blocks.map { it.blockedId }) }
            }
            .decodeList<ProfileNameRow>()
            .associate { it.id to it.displayName }

        return blocks.map { BlockedUser(it.blockedId, names[it.blockedId], it.createdAt) }
    }

    suspend fun blockUser(userId: String, targetId: String) {
        require(targetId != userId) { "self" }
        client.postgrest.from("user_blocks").insert(
            buildJsonObject {
                put("blocker_id", userId)
                put("blocked_id", targetId)
            },
        )
    }

    suspend fun unblockUser(userId: String, targetId: String) {
        client.postgrest.from("user_blocks").delete {
            filter {
                eq("blocker_id", userId)
                eq("blocked_id", targetId)
            }
        }
    }

    /* ----------------------------- Moderation ----------------------------- */

    /** Reports filed on rooms the caller hosts, newest first. */
    suspend fun listRoomReports(userId: String): List<HostReport> {
        val rooms = client.postgrest.from("rooms")
            .select(Columns.list("id", "code")) { filter { eq("host_id", userId) } }
            .decodeList<HostRoomRow>()
        if (rooms.isEmpty()) return emptyList()
        val codeByRoom = rooms.associate { it.id to it.code }

        val reports = client.postgrest.from("user_reports")
            .select(
                Columns.list(
                    "id", "kind", "reason", "status", "created_at",
                    "reporter_id", "target_user_id", "target_room_id",
                ),
            ) {
                filter { isIn("target_room_id", rooms.map { it.id }) }
                order("created_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList<ReportRow>()
        if (reports.isEmpty()) return emptyList()

        val ids = reports.flatMap { listOfNotNull(it.reporterId, it.targetUserId) }.distinct()
        val names = if (ids.isEmpty()) emptyMap() else
            client.postgrest.from("profiles")
                .select(Columns.list("id", "display_name")) { filter { isIn("id", ids) } }
                .decodeList<ProfileNameRow>()
                .associate { it.id to it.displayName }

        return reports.map { r ->
            HostReport(
                id = r.id,
                kind = r.kind,
                reason = r.reason,
                status = r.status,
                createdAt = r.createdAt,
                targetUserId = r.targetUserId,
                targetRoomId = r.targetRoomId,
                roomCode = r.targetRoomId?.let { codeByRoom[it] },
                reporterName = r.reporterId?.let { names[it] },
                targetName = r.targetUserId?.let { names[it] },
            )
        }
    }

    /**
     * Resolves or dismisses a report. See the class note: the backing GRANT is
     * missing, so this currently fails at the privilege layer on both clients.
     * Kept as the real write so it works untouched once the grant lands.
     */
    suspend fun resolveReport(id: String, status: String) {
        require(status == "resolved" || status == "dismissed")
        client.postgrest.from("user_reports").update(
            { set("status", status) },
        ) {
            filter { eq("id", id) }
        }
    }
}
