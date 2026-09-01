package app.stackd.feature.room

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.appContainer
import app.stackd.core.formatDuration
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.ErrorBanner
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.NoticeBanner
import app.stackd.core.ui.SectionLabel
import app.stackd.feature.room.session.BreachDetector
import app.stackd.feature.room.session.FocusScore

/**
 * The room screen. The [RoomViewModel] owns all session state and realtime; this
 * composable renders the current phase and bridges the two things a ViewModel
 * can't hold: the live sensor loop (via [BreachDetector]) and the Android
 * lifecycle (leaving the app is itself a breach).
 */
@Composable
fun RoomRoute(
    code: String,
    onExit: () -> Unit,
    vm: RoomViewModel = viewModel(
        factory = stackdViewModel { RoomViewModel(it, code) },
    ),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Sensor + lifecycle bridge. Bound only while this user is armed — the
    // detector's own listeners are the battery cost, so they exist exactly when
    // detection should. Rebinding on every timer tick is avoided by keying the
    // effect on `armed` alone, matching the web hook's "Optim 01".
    DisposableEffect(state.armed) {
        if (!state.armed) return@DisposableEffect onDispose { }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val detector = BreachDetector(
            sensorManager = sensorManager,
            vibrate = { ms -> vibrate(context, ms) },
        ).apply {
            mode = vm.enforcementMode
            onBreach = vm::onBreach
            onCalibrated = vm::onCalibrated
            onCapability = { cap ->
                vm.onSensorWarning(
                    when {
                        !cap.anyMotionGuard -> "No motion sensors — this device can't detect tilt or shake."
                        !cap.tiltAvailable -> "No orientation sensor — tilt and lift won't be detected."
                        !cap.shakeAvailable -> "No accelerometer — shaking won't be detected."
                        else -> null
                    },
                )
            }
        }
        detector.start()

        // Leaving the app foreground breaks the stack — the web's tab-hidden.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) detector.onAppBackgrounded()
            if (event == Lifecycle.Event.ON_RESUME) vm.reconcile()
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            detector.stop()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    RoomScreen(
        state = state,
        onStart = vm::startRitual,
        onEnd = vm::endSession,
        onAbort = vm::abortSession,
        onExit = onExit,
        onToggleReady = vm::toggleReady,
        onRespondJoin = vm::respondToJoinRequest,
        onAddWorkspace = vm::addWorkspaceItem,
        onToggleWorkspace = vm::toggleWorkspaceDone,
        onDeleteWorkspace = vm::deleteWorkspaceItem,
        onSaveMeta = vm::saveRoomMeta,
        onAddSchedule = vm::addScheduledEvent,
        onSaveSessionMeta = vm::saveSessionMeta,
    )
}

@Composable
fun RoomScreen(
    state: RoomUiState,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onAbort: () -> Unit,
    onExit: () -> Unit,
    onToggleReady: () -> Unit = {},
    onRespondJoin: (String, Boolean) -> Unit = { _, _ -> },
    onAddWorkspace: (String, String, String?) -> Unit = { _, _, _ -> },
    onToggleWorkspace: (String) -> Unit = {},
    onDeleteWorkspace: (String) -> Unit = {},
    onSaveMeta: (String, String, String, Int, String) -> Unit = { _, _, _, _, _ -> },
    onAddSchedule: (String, String, Int) -> Unit = { _, _, _ -> },
    onSaveSessionMeta: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
      app.stackd.core.ui.ResponsiveColumn(
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text("ROOM / ${state.code}", style = MonoLabel, color = colors.textMuted)
        Spacer(Modifier.height(24.dp))

        when (state.phase) {
            RoomPhase.LOADING -> Loading()
            RoomPhase.ERROR -> ErrorBanner(state.error ?: "Something went wrong.", onRetry = onExit)
            RoomPhase.LOBBY -> Lobby(
                state, onStart, onAbort, onExit, onToggleReady, onRespondJoin,
                onSaveMeta, onAddSchedule,
            )
            RoomPhase.COUNTDOWN -> Countdown(state)
            RoomPhase.ACTIVE -> Active(
                state, onEnd, onAbort,
                onToggleReady, onAddWorkspace, onToggleWorkspace, onDeleteWorkspace,
            )
            RoomPhase.ENDED -> Ended(state, onExit, onSaveSessionMeta)
        }
      }
      // Celebration burst over a clean, high finish — not on aborted/compromised
      // sessions, where confetti would read as mockery. One-shot; self-stops.
      if (state.phase == RoomPhase.ENDED &&
          state.room?.statusEnum?.wire != "aborted" &&
          state.result?.tier?.key.let { it == "flow" || it == "pristine" }
      ) {
          app.stackd.core.ui.Confetti(modifier = Modifier.fillMaxSize())
      }
    }
}

@Composable
private fun Loading() {
    SectionLabel("ENTERING")
    Spacer(Modifier.height(12.dp))
    Text("Claiming your seat…", style = MaterialTheme.typography.bodyMedium, color = Stackd.colors.textMuted)
}

@Composable
private fun Lobby(
    state: RoomUiState,
    onStart: () -> Unit,
    onAbort: () -> Unit,
    onExit: () -> Unit,
    onToggleReady: () -> Unit,
    onRespondJoin: (String, Boolean) -> Unit,
    onSaveMeta: (String, String, String, Int, String) -> Unit,
    onAddSchedule: (String, String, Int) -> Unit,
) {
    val colors = Stackd.colors
    SectionLabel("LOBBY")
    Spacer(Modifier.height(12.dp))
    Text(
        state.room?.title?.takeIf { it.isNotBlank() } ?: "Waiting to begin",
        style = MaterialTheme.typography.headlineMedium,
        color = colors.textPrimary,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "${(state.room?.targetDurationSeconds ?: 0) / 60} min · ${state.present.size} in the room",
        style = MonoLabelSmall,
        color = colors.textMuted,
    )
    Spacer(Modifier.height(12.dp))
    CopyInviteButton(state.code)
    Spacer(Modifier.height(8.dp))
    QrInvite(state.code)
    Spacer(Modifier.height(24.dp))
    RoomHeaderPanel(state, onSaveMeta)
    Spacer(Modifier.height(16.dp))
    if (state.isModerator) {
        JoinRequestsPanel(state.joinRequests, onRespondJoin)
        Spacer(Modifier.height(16.dp))
    }
    PresenceRoster(state, onToggleReady)
    Spacer(Modifier.height(16.dp))
    SchedulePanel(state, onAddSchedule)
    Spacer(Modifier.height(16.dp))
    if (state.milestones.isNotEmpty()) {
        MilestoneTimeline(state.milestones)
        Spacer(Modifier.height(16.dp))
    }
    Spacer(Modifier.height(12.dp))

    if (state.isHost) {
        EmberButton(text = "Start Session", onClick = onStart)
        Spacer(Modifier.height(12.dp))
        GhostButton(text = "Abort Room", onClick = onAbort)
    } else {
        NoticeBanner("Waiting for the host to start the session.")
        Spacer(Modifier.height(12.dp))
        GhostButton(text = "Leave", onClick = onExit)
    }
}

/** Copies {WEB_BASE_URL}/room/{code} — the web's copyCode, minus the toast. */
@Composable
private fun CopyInviteButton(code: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var copied by remember { androidx.compose.runtime.mutableStateOf(false) }
    GhostButton(
        text = if (copied) "Invite link copied ✓" else "Copy invite link",
        onClick = {
            val link = "${app.stackd.BuildConfig.WEB_BASE_URL}/room/$code"
            clipboard.setText(androidx.compose.ui.text.AnnotatedString(link))
            copied = true
        },
    )
    // Reset the label a couple seconds after a copy, matching the web's 2s flip.
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2000)
            copied = false
        }
    }
}

/** Show-QR toggle → the web's inline invite QR. Collapsed by default so the
 *  lobby stays compact; the link is the same one CopyInviteButton copies. */
@Composable
private fun QrInvite(code: String) {
    var show by remember { androidx.compose.runtime.mutableStateOf(false) }
    GhostButton(
        text = if (show) "Hide QR" else "Show invite QR",
        onClick = { show = !show },
    )
    if (show) {
        Spacer(Modifier.height(12.dp))
        app.stackd.core.ui.QrCode(
            content = "${app.stackd.BuildConfig.WEB_BASE_URL}/room/$code",
            size = 180.dp,
        )
    }
}

@Composable
private fun Countdown(state: RoomUiState) {
    val colors = Stackd.colors
    SectionLabel("STARTING")
    Spacer(Modifier.height(24.dp))
    Text(
        state.countdown?.toString() ?: "…",
        style = MaterialTheme.typography.displayLarge,
        color = colors.accent,
        fontWeight = FontWeight.ExtraBold,
    )
    Spacer(Modifier.height(12.dp))
    Text("Stack your phones face-down.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
}

@Composable
private fun Active(
    state: RoomUiState,
    onEnd: () -> Unit,
    onAbort: () -> Unit,
    onToggleReady: () -> Unit,
    onAddWorkspace: (String, String, String?) -> Unit,
    onToggleWorkspace: (String) -> Unit,
    onDeleteWorkspace: (String) -> Unit,
) {
    val colors = Stackd.colors

    // Progress ring + remaining time.
    val progress = if ((state.room?.targetDurationSeconds ?: 0) > 0) {
        (state.elapsedSeconds.toFloat() / state.room!!.targetDurationSeconds).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.border,
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arc, style = Stroke(stroke),
            )
            drawArc(
                color = if (state.iBreached) colors.breach else colors.accent,
                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arc, style = Stroke(stroke),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatDuration(state.remainingSeconds.toInt()),
                style = MaterialTheme.typography.displayMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (state.calibrating) "ARMING…" else if (state.iBreached) "BREACHED" else "HOLDING",
                style = MonoLabelSmall,
                color = if (state.iBreached) colors.breach else colors.textMuted,
            )
        }
    }

    state.sensorWarning?.let {
        Spacer(Modifier.height(12.dp))
        ErrorBanner(it)
    }
    if (state.iBreached) {
        Spacer(Modifier.height(12.dp))
        ErrorBanner("Your stack broke. You're out for this session, but it's still running for the others.")
    }

    Spacer(Modifier.height(20.dp))
    SharedGoalBar(state)
    if (state.goalHours > 0) Spacer(Modifier.height(16.dp))
    PresenceRoster(state, onToggleReady)
    Spacer(Modifier.height(16.dp))
    LiveActivityRail(state)
    Spacer(Modifier.height(16.dp))
    WorkspacePanel(
        items = state.workspace,
        onAdd = onAddWorkspace,
        onToggle = onToggleWorkspace,
        onDelete = onDeleteWorkspace,
    )
    Spacer(Modifier.height(16.dp))
    if (state.milestones.isNotEmpty()) {
        MilestoneTimeline(state.milestones)
        Spacer(Modifier.height(16.dp))
    }

    if (state.isHost) {
        EmberButton(text = "End Now", onClick = onEnd)
        Spacer(Modifier.height(12.dp))
        GhostButton(text = "Abort", onClick = onAbort)
    }
}

@Composable
private fun Ended(
    state: RoomUiState,
    onExit: () -> Unit,
    onSaveSessionMeta: (String, String) -> Unit,
) {
    val colors = Stackd.colors
    val result = state.result
    SectionLabel(if (state.room?.statusEnum?.wire == "aborted") "ABORTED" else "SESSION COMPLETE")
    Spacer(Modifier.height(24.dp))

    if (result != null) {
        // Ceremony beat: the score never snaps — it counts up with the same
        // ease-out quartic the web's useCountUp applies, plus one haptic tick
        // at the reveal.
        val context = androidx.compose.ui.platform.LocalContext.current
        var shown by remember(result) { androidx.compose.runtime.mutableIntStateOf(0) }
        androidx.compose.runtime.LaunchedEffect(result) {
            vibrate(context, 30)
            val durationMs = 1800L
            val start = System.currentTimeMillis()
            while (true) {
                val p = ((System.currentTimeMillis() - start).toFloat() / durationMs).coerceAtMost(1f)
                val eased = 1f - (1f - p) * (1f - p) * (1f - p) * (1f - p)
                shown = (result.score * eased).toInt()
                if (p >= 1f) break
                kotlinx.coroutines.delay(16)
            }
            shown = result.score
        }
        Text(
            shown.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = Color(result.tier.hex),
            fontWeight = FontWeight.ExtraBold,
        )
        Text("/100 · ${result.tier.label.uppercase()}", style = MonoLabelSmall, color = Color(result.tier.hex))
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Stat("XP EARNED", "+${result.xp}")
            Stat("FOCUS", formatDuration(result.focusSecondsInt))
            Stat("PENALTY", result.penalty.toString())
        }

        // Recap card — the web's session-recap-card breakdown.
        val myBreaks = state.breaks.filter { it.userId == state.meId }
        val severe = myBreaks.count { it.isSevere }
        val minor = myBreaks.size - severe
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.textPrimary.copy(alpha = 0.03f), app.stackd.core.theme.Radius2Xl)
                .border(1.dp, colors.border, app.stackd.core.theme.Radius2Xl)
                .padding(16.dp),
        ) {
            Text("RECAP", style = MonoLabelSmall, color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            RecapLine("Target", formatDuration((state.room?.targetDurationSeconds ?: 0L).toInt()))
            RecapLine("Held for", formatDuration(result.focusSecondsInt))
            RecapLine("Breaches", if (myBreaks.isEmpty()) "None — clean stack" else "$minor minor · $severe severe")
            if (result.penalty > 0) RecapLine("Penalty", "-${result.penalty} pts")
            RecapLine("Tier multiplier", "×${result.tier.multiplier}")
        }

        if (state.resultQueuedOffline) {
            Spacer(Modifier.height(16.dp))
            NoticeBanner("Saved offline — it'll sync when you're back online.")
        }

        // Breach log — every break this session, most recent first. Data is
        // already in state; the web renders the same list under BREACH_LOG.
        if (myBreaks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.textPrimary.copy(alpha = 0.03f), app.stackd.core.theme.Radius2Xl)
                    .border(1.dp, colors.border, app.stackd.core.theme.Radius2Xl)
                    .padding(16.dp),
            ) {
                Text("BREACH LOG", style = MonoLabelSmall, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                myBreaks.sortedByDescending { it.at }.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            b.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (b.isSevere) colors.breach else colors.textMuted,
                        )
                        Text(
                            if (b.isSevere) "SEVERE" else "MINOR",
                            style = MonoLabelSmall,
                            color = if (b.isSevere) colors.breach else colors.textMuted,
                        )
                    }
                }
            }
        }

        // Post-session notes + tags, attached to this history row. Only after
        // finalize returns an id — the RPC needs a real row to stamp.
        if (state.historyId != null) {
            Spacer(Modifier.height(20.dp))
            SessionMetaForm(
                saving = state.savingSessionMeta,
                saved = state.sessionMetaSaved,
                onSave = onSaveSessionMeta,
            )
        }
    } else {
        Text("Tallying your session…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
    }

    Spacer(Modifier.height(28.dp))
    EmberButton(text = "Back to Dashboard", onClick = onExit)
}

/** Notes + comma-tags for the finished session — web's SessionMetaForm. */
@Composable
private fun SessionMetaForm(
    saving: Boolean,
    saved: Boolean,
    onSave: (String, String) -> Unit,
) {
    val colors = Stackd.colors
    var notes by remember { androidx.compose.runtime.mutableStateOf("") }
    var tags by remember { androidx.compose.runtime.mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), app.stackd.core.theme.Radius2Xl)
            .border(1.dp, colors.border, app.stackd.core.theme.Radius2Xl)
            .padding(16.dp),
    ) {
        Text("MARK THIS SESSION", style = MonoLabelSmall, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(
            value = notes,
            onValueChange = { if (it.length <= 2000) notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Tags, comma-separated") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        EmberButton(
            text = if (saving) "Marking…" else if (saved) "Marked ✓" else "Mark it",
            onClick = { onSave(notes, tags) },
            enabled = !saving && (notes.isNotBlank() || tags.isNotBlank()),
            busy = saving,
        )
    }
}

@Composable
private fun RecapLine(label: String, value: String) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
    }
}

@Composable
private fun Stat(label: String, value: String) {
    val colors = Stackd.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MonoLabelSmall, color = colors.textMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold)
    }
}

/** Fires a haptic pulse, matching the web's `navigator.vibrate`. */
private fun vibrate(context: Context, ms: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(ms)
    }
}
