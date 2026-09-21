package app.stackd.feature.room

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stackd.core.formatHours
import app.stackd.core.parseIsoMillis
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.SectionLabel
import app.stackd.data.room.JoinRequest
import app.stackd.data.room.Milestone
import app.stackd.data.room.ParticipantRow
import app.stackd.data.room.RoomEvent
import app.stackd.data.room.WorkspaceItem

/** Card wrapper shared by the room panels — matches the dashboard tiles. */
@Composable
private fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
        content = content,
    )
}

/** How long since a heartbeat before a participant reads as disconnected. */
private const val DISCONNECT_MS = 45_000L

/* --------------------------------- roster --------------------------------- */

/**
 * The presence roster — per-participant status derived in the same priority
 * order the web uses: broke > disconnected > stacking > ready > idle.
 */
@Composable
fun PresenceRoster(state: RoomUiState, onToggleReady: () -> Unit) {
    val colors = Stackd.colors
    val now = System.currentTimeMillis()
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionLabel("PRESENCE", color = colors.textMuted)
            Text("${state.present.size} in room", style = MonoLabelSmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        state.present.forEach { p ->
            val status = rosterStatus(p, state, now)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(Modifier.size(6.dp).background(status.color, CircleShape))
                Text(
                    p.displayName + if (p.userId == state.meId) " · you" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (p.breached) colors.textMuted else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(status.label, style = MonoLabelSmall, color = status.color)
            }
        }
        // Ready toggle only in the lobby.
        if (state.room?.statusEnum?.wire == "lobby") {
            Spacer(Modifier.height(12.dp))
            if (state.iAmReady) {
                GhostButton(text = "Ready ✓", onClick = onToggleReady)
            } else {
                EmberButton(text = "I'm Ready", onClick = onToggleReady)
            }
        }
    }
}

private data class RosterStatus(val label: String, val color: Color)

@Composable
private fun rosterStatus(p: ParticipantRow, state: RoomUiState, now: Long): RosterStatus {
    val colors = Stackd.colors
    val active = state.room?.statusEnum?.wire == "active"
    val hbAge = parseIsoMillis(p.lastHeartbeat)?.let { now - it }
    return when {
        p.breached -> RosterStatus("Broke stack", colors.breach)
        active && hbAge != null && hbAge > DISCONNECT_MS ->
            RosterStatus("Disconnected", colors.accentGlow)
        active -> RosterStatus("Stacking", colors.live)
        p.userId in state.readyIds -> RosterStatus("Ready", colors.accent)
        else -> RosterStatus("Waiting", colors.textMuted)
    }
}

/* ------------------------------ shared goal ------------------------------- */

@Composable
fun SharedGoalBar(state: RoomUiState) {
    if (state.goalHours <= 0) return
    val colors = Stackd.colors
    val goalSec = state.goalHours * 3600L
    val done = state.collectiveFocusSeconds
    val pct = (done.toFloat() / goalSec).coerceIn(0f, 1f)
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            SectionLabel("SHARED GOAL", color = colors.textMuted)
            Text(
                "${(done / 3600)}h / ${state.goalHours}h",
                style = MonoLabelSmall,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(4.dp)
                .background(colors.textPrimary.copy(alpha = 0.06f), RadiusMd),
        ) {
            Box(Modifier.fillMaxWidth(pct).height(4.dp).background(colors.accent, RadiusMd))
        }
        if (pct >= 1f) {
            Spacer(Modifier.height(8.dp))
            Text("🎉 Goal reached — set a new one.", style = MonoLabelSmall, color = colors.accent)
        }
    }
}

/* ----------------------------- activity rail ------------------------------ */

@Composable
fun LiveActivityRail(state: RoomUiState) {
    val colors = Stackd.colors
    val now = System.currentTimeMillis()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
            SectionLabel("LIVE_FEED", color = colors.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        if (state.events.isEmpty()) {
            Text("The room is quiet…", style = MonoLabelSmall, color = colors.textMuted)
        } else
        Column(
            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
        ) {
            state.events.forEach { e ->
                val (glyph, label) = eventGlyphLabel(e.kind)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(glyph, style = MonoLabelSmall, color = eventColor(e.kind))
                    Text(
                        "${e.actorName ?: "Someone"} $label",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(timeAgo(parseIsoMillis(e.createdAt), now), style = MonoLabelSmall, color = colors.textMuted)
                }
            }
        }
    }
}

@Composable
private fun eventColor(kind: String): Color = when (kind) {
    "breach" -> Stackd.colors.breach
    "goal_hit", "completed" -> Stackd.colors.accent
    else -> Stackd.colors.textMuted
}

/** Kind → (glyph, label), ported from the web's dictionaries. */
private fun eventGlyphLabel(kind: String): Pair<String, String> = when (kind) {
    "joined" -> "→" to "joined the room"
    "left" -> "←" to "left the room"
    "started" -> "▶" to "started the session"
    "breach" -> "✕" to "broke the stack"
    "completed" -> "◆" to "finished the session"
    "pinned" -> "★" to "pinned a message"
    "goal_hit" -> "◎" to "hit the goal"
    "ready" -> "✓" to "is ready"
    "unready" -> "·" to "is no longer ready"
    "all_ready" -> "◎" to "— everyone ready"
    "disconnected" -> "⚠" to "disconnected"
    "reconnected" -> "↺" to "reconnected"
    "join_requested" -> "?" to "requested to join"
    "join_approved" -> "✓" to "was approved"
    "join_denied" -> "✕" to "was denied"
    "moderator_added" -> "+" to "became a moderator"
    "moderator_removed" -> "-" to "is no longer a moderator"
    "paused" -> "⏸" to "paused"
    "resumed" -> "▶" to "resumed"
    else -> "·" to kind
}

/* ------------------------------- milestones ------------------------------- */

@Composable
fun MilestoneTimeline(milestones: List<Milestone>) {
    val colors = Stackd.colors
    val now = System.currentTimeMillis()
    Panel {
        SectionLabel("MILESTONES", color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        if (milestones.isEmpty()) {
            Text(
                "The room's story writes itself once it begins.",
                style = MonoLabelSmall,
                color = colors.textMuted,
            )
        } else
        Column(modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
            milestones.forEach { m ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(m.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                        Text(
                            "${m.kind} · ${timeAgo(parseIsoMillis(m.reachedAt), now)} ago",
                            style = MonoLabelSmall,
                            color = colors.textMuted,
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------------- join requests ------------------------------ */

@Composable
fun JoinRequestsPanel(
    requests: List<JoinRequest>,
    onRespond: (String, Boolean) -> Unit,
) {
    if (requests.isEmpty()) return
    val colors = Stackd.colors
    Panel {
        SectionLabel("JOIN_REQUESTS · ${requests.size}", color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        requests.forEach { r ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.displayName, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    r.message?.takeIf { it.isNotBlank() }?.let {
                        Text("\"$it\"", style = MonoLabelSmall, color = colors.textMuted)
                    }
                }
                Text("APPROVE", style = MonoLabelSmall, color = colors.live,
                    modifier = Modifier.clickable { onRespond(r.id, true) })
                Text("DENY", style = MonoLabelSmall, color = colors.breach,
                    modifier = Modifier.clickable { onRespond(r.id, false) })
            }
        }
    }
}

/* ------------------------------- workspace -------------------------------- */

@Composable
fun WorkspacePanel(
    items: List<WorkspaceItem>,
    onAdd: (kind: String, content: String, url: String?) -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = Stackd.colors
    var kind by remember { mutableStateOf("note") }
    var content by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Panel {
        SectionLabel("WORKSPACE", color = colors.textMuted)
        Spacer(Modifier.height(10.dp))
        // Kind tabs.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("note", "todo", "link").forEach { k ->
                val sel = kind == k
                Text(
                    k.uppercase(),
                    style = MonoLabelSmall,
                    color = if (sel) colors.textPrimary else colors.textMuted,
                    modifier = Modifier
                        .clickable { kind = k }
                        .background(
                            if (sel) colors.textPrimary.copy(alpha = 0.08f) else Color.Transparent,
                            RadiusMd,
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            placeholder = { Text("Capture as you focus…", color = colors.textMuted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RadiusMd,
        )
        if (kind == "link") {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://…", color = colors.textMuted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RadiusMd,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            )
        }
        Spacer(Modifier.height(8.dp))
        EmberButton(
            text = "Add",
            onClick = {
                onAdd(kind, content, url.takeIf { kind == "link" && it.isNotBlank() })
                content = ""; url = ""
            },
            enabled = content.isNotBlank(),
        )
        if (items.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Nothing yet. Capture as you focus.", style = MonoLabelSmall, color = colors.textMuted)
        } else {
            Spacer(Modifier.height(12.dp))
            Column(modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val glyph = when (item.kind) {
                            "todo" -> if (item.done) "☑" else "☐"
                            "link" -> "→"
                            else -> "◆"
                        }
                        Text(
                            glyph,
                            style = MonoLabelSmall,
                            color = colors.textMuted,
                            modifier = if (item.kind == "todo") Modifier.clickable { onToggle(item.id) } else Modifier,
                        )
                        Text(
                            item.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.done) colors.textMuted else colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("×", style = MonoLabel, color = colors.textMuted,
                            modifier = Modifier.clickable { onDelete(item.id) })
                    }
                }
            }
        }
    }
}

/* -------------------------------- helpers --------------------------------- */

/** Relative time, matching the web's rail: Ns / Nm / Nh / Nd. */
private fun timeAgo(atMillis: Long?, now: Long): String {
    if (atMillis == null) return ""
    val s = ((now - atMillis) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        else -> "${s / 86_400}d"
    }
}
