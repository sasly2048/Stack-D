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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stackd.core.formatHours
import app.stackd.core.parseIsoMillis
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton

/* ------------------------------ room header -------------------------------- */

/**
 * The room's identity card — title, description, pinned message, collective
 * goal progress and visibility, with an inline edit form for the host. Ported
 * from the web's `room-header.tsx`; the goal bar reads banked focus from
 * `focus_history`, the same sum the web's `getRoomStats` computes.
 */
@Composable
fun RoomHeaderPanel(
    state: RoomUiState,
    onSave: (title: String, description: String, pinned: String, goalHours: Int, visibility: String) -> Unit,
) {
    val colors = Stackd.colors
    val room = state.room ?: return
    var editing by remember { mutableStateOf(false) }

    HeaderCard {
        if (editing) {
            var title by remember { mutableStateOf(room.title.orEmpty()) }
            var description by remember { mutableStateOf(room.description.orEmpty()) }
            var pinned by remember { mutableStateOf(room.pinnedMessage.orEmpty()) }
            var goalHours by remember { mutableStateOf(state.goalHours.toString()) }
            var visibility by remember { mutableStateOf(room.visibility ?: "open") }

            OutlinedTextField(
                value = title, onValueChange = { if (it.length <= 80) title = it },
                label = { Text("Room title") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description, onValueChange = { if (it.length <= 500) description = it },
                label = { Text("What is this room for?") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pinned, onValueChange = { if (it.length <= 400) pinned = it },
                label = { Text("Pinned message") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = goalHours,
                onValueChange = { goalHours = it.filter(Char::isDigit).take(3) },
                label = { Text("Goal (hrs)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("open" to "Open", "request" to "Request", "invite" to "Invite")
                    .forEach { (key, label) ->
                        val selected = visibility == key
                        Text(
                            label.uppercase(),
                            style = MonoLabelSmall,
                            color = if (selected) colors.accent else colors.textMuted,
                            modifier = Modifier
                                .border(1.dp, if (selected) colors.accent else colors.border, RadiusMd)
                                .clickable { visibility = key }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EmberButton(
                    text = if (state.savingMeta) "Saving…" else "Save",
                    onClick = {
                        onSave(title, description, pinned, goalHours.toIntOrNull() ?: 0, visibility)
                        editing = false
                    },
                    busy = state.savingMeta,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = "Cancel",
                    onClick = { editing = false },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        room.title ?: "Untitled Room",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    room.description?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    }
                }
                if (state.isHost) {
                    Text(
                        "EDIT",
                        style = MonoLabelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.clickable { editing = true }.padding(4.dp),
                    )
                }
            }

            room.pinnedMessage?.let { pinned ->
                Spacer(Modifier.height(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = 0.05f), RadiusMd)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text("PINNED", style = MonoLabelSmall, color = colors.accent)
                    Spacer(Modifier.height(2.dp))
                    Text(pinned, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                }
            }

            val goalSec = room.collectiveGoalSeconds ?: 0
            if (goalSec > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("COLLECTIVE GOAL", style = MonoLabelSmall, color = colors.textMuted)
                    Text(
                        "${formatHours(state.bankedFocusSeconds.toInt())} / ${formatHours(goalSec.toInt())}",
                        style = MonoLabelSmall, color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                val pct = (state.bankedFocusSeconds.toFloat() / goalSec).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(pct)
                            .height(6.dp)
                            .background(colors.accent, CircleShape),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "VISIBILITY · ${(room.visibility ?: "open").uppercase()}",
                    style = MonoLabelSmall, color = colors.textMuted,
                )
                if (state.moderatorIds.isNotEmpty()) {
                    Text(
                        "MODS · ${state.moderatorIds.size}",
                        style = MonoLabelSmall, color = colors.textMuted,
                    )
                }
            }
        }
    }
}

/* -------------------------------- schedule --------------------------------- */

/**
 * Upcoming shared blocks (`room_scheduled_events`) — web's `room-schedule.tsx`.
 * The web takes a datetime-local input; on a phone "starts in N minutes" is the
 * honest equivalent, converted to an absolute ISO instant at submit.
 */
@Composable
fun SchedulePanel(
    state: RoomUiState,
    onAdd: (title: String, startsAtIso: String, durationMinutes: Int) -> Unit,
) {
    val colors = Stackd.colors
    var showForm by remember { mutableStateOf(false) }
    HeaderCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("SCHEDULE", style = MonoLabelSmall, color = colors.accent)
            if (state.isHost) {
                Text(
                    if (showForm) "CANCEL" else "+ ADD",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.clickable { showForm = !showForm }.padding(4.dp),
                )
            }
        }

        if (showForm) {
            var title by remember { mutableStateOf("") }
            var inMinutes by remember { mutableStateOf("30") }
            var duration by remember { mutableStateOf("60") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = title, onValueChange = { if (it.length <= 120) title = it },
                label = { Text("Title") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inMinutes,
                    onValueChange = { inMinutes = it.filter(Char::isDigit).take(5) },
                    label = { Text("Starts in (min)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                    label = { Text("Length (min)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            EmberButton(
                text = "Schedule",
                onClick = {
                    val startMin = inMinutes.toLongOrNull() ?: 0L
                    val startsAt = java.time.Instant.now().plusSeconds(startMin * 60).toString()
                    onAdd(title, startsAt, duration.toIntOrNull() ?: 60)
                    showForm = false
                },
                enabled = title.isNotBlank(),
            )
        }

        if (state.schedule.isEmpty() && !showForm) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing scheduled.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
        }
        state.schedule.forEach { ev ->
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    ev.title, style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val startMs = parseIsoMillis(ev.startsAt)
                val whenText = if (startMs != null) {
                    val diffMin = (startMs - System.currentTimeMillis()) / 60_000
                    if (diffMin >= 0) "in ${diffMin}m" else "${-diffMin}m ago"
                } else {
                    ""
                }
                Text(
                    "$whenText · ${ev.durationMinutes}m",
                    style = MonoLabelSmall, color = colors.textMuted,
                )
            }
        }
    }
}

/** Same chrome as RoomPanels' private Panel — duplicated locally, it's 8 lines. */
@Composable
private fun HeaderCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(16.dp),
        content = content,
    )
}
