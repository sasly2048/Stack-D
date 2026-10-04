package app.stackd.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import app.stackd.StackdApplication
import app.stackd.core.AppContainer
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/**
 * One live line per Explore row ("1 of 3 done", "#2 of 10"), keyed by menu label.
 *
 * Starts from the last cached map so a reopen is instant, then revalidates each
 * row independently — a slow or failing call only leaves its own row on the
 * static description. Rows derivable from the dashboard's own state (Atlas,
 * Timeline) cost nothing extra and are merged on top.
 */
@Composable
fun rememberMenuStatuses(state: DashboardUiState): Map<String, String> {
    val container = (LocalContext.current.applicationContext as? StackdApplication)?.container
    val uid = container?.auth?.currentUserId
    val key = "menu-status:$uid"
    val remote by produceState(container?.cache?.get<Map<String, String>>(key).orEmpty(), container, uid) {
        if (container == null || uid == null) return@produceState
        loadMenuStatuses(container, uid) { label, status ->
            value = value + (label to status)
            container.cache.put(key, value)
        }
    }
    val local = remember(state.loading, state.history, state.aiRecommendation) {
        if (state.loading) emptyMap() else buildMap {
            val n = state.history.size
            // recentSessions caps at 50, so past that the honest count is "50+".
            put("Timeline", if (n == 0) "No sessions yet" else if (n >= 50) "50+ sessions" else plural(n, "session"))
            val rec = state.aiRecommendation ?: app.stackd.feature.insights.recommendNextSession(state.history)
            put("Atlas", "Next: ${rec.durationMinutes} min · ${rec.topic}")
        }
    }
    return remote + local
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

@Serializable
private data class IdOnly(val id: String)

@Serializable
private data class UnlockOnly(@SerialName("achievement_id") val achievementId: String)

/** Same reads the destination screens make; each row reports via [emit] as soon as it lands. */
private suspend fun loadMenuStatuses(c: AppContainer, uid: String, emit: (String, String) -> Unit) = coroutineScope {
    fun row(block: suspend () -> Unit) = launch { runCatching { block() } }

    row {
        val all = c.progression.listChallenges(uid)
        if (all.isNotEmpty()) emit("Challenges", "${all.count { it.completedAt != null }} of ${all.size} done")
    }
    row {
        val s = c.progression.activeSeason() ?: return@row
        val start = runCatching { OffsetDateTime.parse(s.startsAt).toInstant() }.getOrElse { Instant.parse(s.startsAt) }
        emit("Seasons", "${s.name} · day ${Duration.between(start, Instant.now()).toDays() + 1}")
    }
    row {
        val board = c.leaderboard.topIndividuals()
        if (board.isEmpty()) return@row
        val i = board.indexOfFirst { it.id == uid }
        emit(
            "Leaderboard",
            when {
                i < 0 -> "Outside the top ${board.size}"
                board.size >= 100 -> "#${i + 1} · top 100"
                else -> "#${i + 1} of ${board.size}"
            },
        )
    }
    row {
        // AchievementsScreen's two reads, ids only.
        val total = c.client.postgrest.from("achievements").select(Columns.list("id")).decodeList<IdOnly>().size
        val unlocked = c.client.postgrest.from("user_achievements")
            .select(Columns.list("achievement_id")) { filter { eq("user_id", uid) } }
            .decodeList<UnlockOnly>().size
        if (total > 0) emit("Achievements", "$unlocked of $total")
    }
    row {
        val rows = c.friends.listFriends(uid)
        val friends = rows.count { it.direction == "friend" }
        val incoming = rows.count { it.direction == "incoming" }
        emit(
            "Friends",
            when {
                incoming > 0 && friends > 0 -> "${plural(friends, "friend")} · $incoming waiting"
                incoming > 0 -> plural(incoming, "request")
                friends > 0 -> plural(friends, "friend")
                else -> "Find people"
            },
        )
    }
    row {
        val n = c.groups.listMyCircles(uid).size
        emit("Circles", if (n == 0) "Start or join one" else "In ${plural(n, "circle")}")
    }
    row {
        val incoming = c.partners.listPartners(uid).count { it.incoming }
        if (incoming > 0) emit("Partners", "${plural(incoming, "invite")} waiting")
    }
    row {
        val ent = c.premium.myEntitlement()
        emit("Premium", if (ent.isElite) "Elite member" else if (ent.isPro) "Pro member" else "Free plan")
        if (!ent.isElite && !ent.isAdmin) {
            emit("Memory Vault", "Elite · keep what mattered")
            emit("Time Capsule", "Elite · notes to your future self")
            return@row
        }
        launch {
            runCatching {
                val n = c.vault.listVault(uid).size
                emit("Memory Vault", if (n == 0) "Nothing kept yet" else if (n == 1) "1 memory" else "$n memories")
            }
        }
        launch {
            runCatching {
                val sealed = c.vault.listCapsules(uid).count { it.openedAt == null }
                emit("Time Capsule", if (sealed == 0) "Nothing sealed yet" else "$sealed sealed")
            }
        }
    }
}
