package app.stackd.feature.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight

import android.content.Context
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import app.stackd.core.ui.breathing
import app.stackd.core.ui.NoticeBanner
import app.stackd.core.ui.SectionLabel
import app.stackd.feature.room.session.FocusScore

/**
 * The room screen. The [RoomViewModel] owns all session state, realtime, and
 * (via the foreground service) the live sensor loop; this composable just
 * renders the current phase. Breach detection deliberately does NOT live here
 * anymore — a Composable-bound detector dies when the screen locks, which is
 * precisely when a face-down stack needs watching.
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
    val lifecycleOwner = LocalLifecycleOwner.current

    // Breach detection lives in FocusSessionService now, so it keeps guarding
    // with the screen off — the phone-stacking model: lock the phone face-down
    // and lifting it is a breach. That means locking the screen is NO LONGER a
    // breach (the old ON_STOP → onAppBackgrounded path is gone). The only
    // lifecycle concern left here is correcting timer drift after the OS froze
    // the app in the background.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.reconcile()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Android 13+ hides the session countdown notification unless the app holds
    // POST_NOTIFICATIONS — declared but never requested before. Ask once, in the
    // lobby, before the session's foreground service posts it.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { /* Declining is fine: the session still runs, just without the shade timer. */ }
        LaunchedEffect(state.phase == RoomPhase.LOBBY) {
            if (state.phase == RoomPhase.LOBBY &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS,
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // A live session must not end on an accidental back gesture. Leaving is
    // allowed — the service keeps guarding and records breaches without this
    // screen, and the floating timer pill brings the user back — but it's a
    // confirmed choice, not a swipe.
    var confirmLeave by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled = state.phase == RoomPhase.ACTIVE) {
        confirmLeave = true
    }
    if (confirmLeave) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave the room screen?") },
            text = {
                Text(
                    "Your session keeps running and the stack stays guarded — " +
                        "lifting the phone still counts. The timer pill brings you back.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeave = false; onExit() }) {
                    Text("Leave screen")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmLeave = false }) {
                    Text("Keep focusing")
                }
            },
        )
    }

    // "X broke the stack" toasts for other participants' severe breaks.
    val snackbarHost = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.breachToasts.collect { msg -> snackbarHost.showSnackbar(msg) }
    }

    Box(Modifier.fillMaxSize()) {
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
            onInteraction = vm::onInteraction,
            onRequestJoin = vm::requestJoin,
            onRegenerateRecap = vm::regenerateRecap,
        )
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )

        // Cinematic post-session ceremony over everything, once the rich summary
        // has loaded and until the user taps Continue.
        state.ceremony?.let { summary ->
            if (!state.ceremonyDismissed) {
                SessionCeremony(summary = summary, onContinue = vm::dismissCeremony)
            }
        }
    }
}

/** Approval-room gate — web JoinRequestGate. */
@Composable
private fun JoinGate(gate: String, onRequest: (String) -> Unit, onExit: () -> Unit) {
    val colors = Stackd.colors
    var note by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Text("APPROVAL REQUIRED", style = MonoLabel, color = colors.textMuted)
        Spacer(Modifier.height(12.dp))
        Text(
            "This room is invite-by-request.",
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        when (gate) {
            "pending" -> Text(
                "Request sent. You'll enter automatically once the host approves.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            "denied" -> Text(
                "The host declined this request.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            else -> {
                Text("Ask the host to let you in.", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                Spacer(Modifier.height(16.dp))
                app.stackd.core.ui.StackdField(
                    label = "Note for the host",
                    value = note,
                    onValueChange = { note = it.take(280) },
                    placeholder = "Optional",
                )
                Spacer(Modifier.height(16.dp))
                app.stackd.core.ui.EmberButton(
                    text = if (gate == "sending") "Sending…" else "Request to join",
                    onClick = { onRequest(note) },
                    enabled = gate != "sending",
                )
                if (gate == "failed") {
                    Spacer(Modifier.height(8.dp))
                    Text("Couldn't send the request. Try again.", style = MonoLabelSmall, color = colors.breach)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        app.stackd.core.ui.GhostButton(text = "Back", onClick = onExit)
    }
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
    onInteraction: () -> Unit = {},
    onRequestJoin: (String) -> Unit = {},
    onRegenerateRecap: () -> Unit = {},
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
        // Back only where leaving is harmless. Mid-session a stray tap here
        // would count as a breach, and system back already confirms first.
        val canLeave = state.phase == RoomPhase.LOBBY || state.phase == RoomPhase.ENDED ||
            state.phase == RoomPhase.ERROR
        app.stackd.core.ui.ScreenHeader("ROOM / ${state.code}", if (canLeave) onExit else null) {
            ConnectionBadge(state.connection)
        }
        Spacer(Modifier.height(24.dp))

        when (state.phase) {
            RoomPhase.LOADING -> Loading()
            RoomPhase.ERROR -> if (state.joinGate != null) {
                JoinGate(state.joinGate, onRequestJoin, onExit)
            } else {
                ErrorBanner(state.error ?: "Something went wrong.", onRetry = onExit)
            }
            RoomPhase.LOBBY -> Lobby(
                state, onStart, onAbort, onExit, onToggleReady, onRespondJoin,
                onSaveMeta, onAddSchedule,
            )
            RoomPhase.COUNTDOWN -> Countdown(state)
            RoomPhase.PLACING -> Placing(onAbort, state.startingSession)
            RoomPhase.ACTIVE -> Active(
                state, onEnd, onAbort,
                onToggleReady, onAddWorkspace, onToggleWorkspace, onDeleteWorkspace,
                onInteraction = onInteraction,
            )
            RoomPhase.ENDED -> Ended(state, onExit, onSaveSessionMeta, onRegenerateRecap)
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

/**
 * Live shared breach feed during an active session — every participant's
 * breaks as they land over realtime, not just the caller's and not only at the
 * end. This is the shared-accountability surface the web renders in-session;
 * without it a stacker can't see anyone else break.
 */
@Composable
private fun LiveBreachFeed(breaks: List<app.stackd.data.room.BreakRow>) {
    if (breaks.isEmpty()) return
    val colors = Stackd.colors
    Text("BREACH LOG", style = MonoLabelSmall, color = colors.textMuted)
    Spacer(Modifier.height(6.dp))
    breaks.sortedByDescending { it.at }.forEach { b ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                b.displayName,
                style = MonoLabelSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${b.reason} · ${b.severity}",
                style = MonoLabelSmall,
                color = if (b.isSevere) colors.breach else colors.textMuted,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

/**
 * Realtime health dot + label in the room header. LIVE is a calm accent dot;
 * CONNECTING/RECONNECTING use the breach palette so a silently-dead socket is
 * visible rather than looking live. Surfaces RoomViewModel's channel status.
 */
@Composable
private fun ConnectionBadge(connection: ConnectionState) {
    val colors = Stackd.colors
    val (dot, label) = when (connection) {
        ConnectionState.LIVE -> colors.accent to "LIVE"
        ConnectionState.CONNECTING -> colors.textMuted to "CONNECTING"
        ConnectionState.RECONNECTING -> colors.breach to "RECONNECTING"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(7.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dot),
        )
        Text(label, style = MonoLabelSmall, color = dot)
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
        style = MaterialTheme.typography.displaySmall.copy(fontFamily = app.stackd.core.theme.SerifFamily),
        color = colors.textPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "${(state.room?.targetDurationSeconds ?: 0) / 60} min · ${state.present.size} in the room",
        style = MonoLabelSmall,
        color = colors.textMuted,
    )
    // Rhythm: 8dp inside a group, 16dp between cards, 24dp between groups.
    Spacer(Modifier.height(24.dp))
    InviteRow(state.code)
    Spacer(Modifier.height(24.dp))
    // The one decision this screen exists for sits above the fold; setup
    // panels below are optional detail.
    if (state.isHost) {
        EmberButton(text = "Start Session", onClick = onStart)
    } else {
        NoticeBanner("Waiting for the host to start the session.")
    }
    Spacer(Modifier.height(24.dp))
    RoomHeaderPanel(state, onSaveMeta)
    Spacer(Modifier.height(16.dp))
    // Only when there's something to show — the empty panel still added its
    // spacer, leaving a doubled gap above the roster.
    if (state.isModerator && state.joinRequests.isNotEmpty()) {
        JoinRequestsPanel(state.joinRequests, onRespondJoin)
        Spacer(Modifier.height(16.dp))
    }
    PresenceRoster(state, onToggleReady)
    Spacer(Modifier.height(16.dp))
    SchedulePanel(state, onAddSchedule)
    if (state.milestones.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        MilestoneTimeline(state.milestones)
    }
    Spacer(Modifier.height(24.dp))

    GhostButton(text = if (state.isHost) "Abort Room" else "Leave", onClick = if (state.isHost) onAbort else onExit)
}

/**
 * Invite: the system share sheet (one tap to WhatsApp/Messages — where
 * invites actually go) beside a QR toggle for the person sitting next to you.
 * Same {WEB_BASE_URL}/room/{code} link the web copies.
 */
@Composable
private fun InviteRow(code: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val link = "${app.stackd.BuildConfig.WEB_BASE_URL}/room/$code"
    var showQr by remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            app.stackd.core.ui.AccentButton(
                text = "Share invite",
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "Stack with me on Stack'd — room $code\n$link")
                    ctx.startActivity(android.content.Intent.createChooser(send, "Invite to room $code"))
                },
            )
        }
        Box(Modifier.weight(0.5f)) {
            GhostButton(text = if (showQr) "Hide QR" else "QR", onClick = { showQr = !showQr })
        }
    }
    if (showQr) {
        Spacer(Modifier.height(12.dp))
        app.stackd.core.ui.QrCode(content = link, size = 180.dp)
    }
}

/** Full-height centered stage for the countdown/placement beats (web: fixed inset, centered). */
@Composable
private fun Stage(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val h = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp
    Column(
        Modifier.fillMaxWidth().heightIn(min = (h * 0.68f).dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
private fun Countdown(state: RoomUiState) {
    val colors = Stackd.colors
    // Each tick pops in from 1.3x and settles — the beat you feel, not just read.
    val pop = remember { androidx.compose.animation.core.Animatable(1f) }
    androidx.compose.runtime.LaunchedEffect(state.countdown) {
        if (state.countdown != null) app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.SELECT)
        pop.snapTo(1.3f)
        pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 300f))
    }
    Stage {
        SectionLabel("STARTING", modifier = Modifier.breathing())
        Spacer(Modifier.height(16.dp))
        Text(
            state.countdown?.toString() ?: "…",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 128.sp, lineHeight = 136.sp),
            color = colors.accent,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
        Spacer(Modifier.height(16.dp))
        Text("Stack your phones face-down.", style = MaterialTheme.typography.bodyLarge, color = colors.textMuted)
    }
}

/**
 * The placement gate: after the countdown, the session waits here until the
 * accelerometer confirms the phone is flat, face-down and still. The clock has
 * NOT started yet — the start RPC only fires once placement lands — so a phone
 * left in hand simply holds here. An abort escape keeps the host from being
 * trapped if they can't get the phone flat (or change their mind).
 */
/**
 * The placement gate: after the countdown, the session waits here until the
 * accelerometer confirms the phone is flat, face-down and still. The clock has
 * NOT started yet — the start RPC only fires once placement lands — so a phone
 * left in hand simply holds here. A breathing ring says "waiting for you".
 */
@Composable
private fun Placing(onAbort: () -> Unit, starting: Boolean = false) {
    val colors = Stackd.colors
    val breathe = androidx.compose.animation.core.rememberInfiniteTransition(label = "breathe")
    val b by breathe.animateFloat(
        0f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "b",
    )
    Stage {
        SectionLabel(if (starting) "STARTING THE CLOCK" else "PLACE TO BEGIN")
        Spacer(Modifier.height(28.dp))
        androidx.compose.foundation.Canvas(Modifier.size(120.dp)) {
            val r = size.minDimension / 2
            drawCircle(colors.accent.copy(alpha = 0.10f + 0.10f * b), radius = r * (0.70f + 0.30f * b))
            drawCircle(colors.accent.copy(alpha = 0.55f), radius = r * 0.62f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawCircle(colors.accent, radius = r * 0.10f)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Phone face-down to start.",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "The clock starts the moment your phone is flat and still. Lift it and the session breaks.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        // Once placement confirms, the start request is in flight: no Cancel,
        // so a tap can't abort a session the server has already begun.
        if (!starting) GhostButton(text = "Cancel", onClick = onAbort)
    }
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
    onInteraction: () -> Unit = {},
) {
    val colors = Stackd.colors
    androidx.compose.runtime.LaunchedEffect(state.iBreached) {
        if (state.iBreached) app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.ERROR)
    }

    // Progress ring + remaining time.
    val progress = if ((state.room?.targetDurationSeconds ?: 0) > 0) {
        (state.elapsedSeconds.toFloat() / state.room!!.targetDurationSeconds).coerceIn(0f, 1f)
    } else 0f

    // Touch-breach: while armed, ANY touch inside this content column breaks the
    // stack — a stacked phone is meant to be untouched. The listener sits on the
    // Initial pointer pass so it fires before children (scroll, workspace
    // checkboxes, roster) can consume the event. The host's End/Abort controls
    // are rendered OUTSIDE this column, so a clean finish never trips the breach.
    // Gated on `!calibrating` so the taps that place the phone face-down during
    // the ARMING window don't themselves trip the breach; the guard activates
    // the moment calibration completes and the stack is being watched.
    val interactionGuard = if (state.armed && !state.calibrating && !state.iBreached) {
        Modifier.pointerInput(state.armed, state.calibrating) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    onInteraction()
                }
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier.fillMaxWidth().then(interactionGuard),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        Column(
            modifier = Modifier.breathing(enabled = !state.iBreached && !state.calibrating),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
    LiveBreachFeed(state.breaks)
    } // end interaction-guarded content column

    // Ambient soundscapes sit OUTSIDE the guarded column, like End/Abort:
    // choosing a bed or nudging volume is a deliberate control, not a stack
    // breach. Web mounts <AmbientPlayer/> in the same active-session panels.
    Spacer(Modifier.height(16.dp))
    AmbientPlayer()

    // End/Abort live OUTSIDE the guarded column so the host can finish cleanly
    // without the tap registering as a stack-breaking interaction.
    if (state.isHost) {
        Spacer(Modifier.height(16.dp))
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
    onRegenerateRecap: () -> Unit,
) {
    val colors = Stackd.colors
    val result = state.result
    SectionLabel(
        when {
            state.room?.statusEnum?.wire != "aborted" -> "SESSION COMPLETE"
            state.room.startedAt == null -> "CANCELLED"
            else -> "ABORTED"
        },
    )
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

        // LLM narrative recap — web's SessionRecapCard: loading, Signal lost +
        // Retry, Regenerate, and Download PDF.
        if (state.aiRecapInput != null) {
            Spacer(Modifier.height(20.dp))
            AiRecapCard(state, onRegenerateRecap)
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
        if (state.room?.statusEnum?.wire == "aborted") {
            // No result is coming for an aborted room — say what happened
            // instead of "Tallying…" forever.
            val neverStarted = state.room.startedAt == null
            Text(
                if (neverStarted) "Session cancelled." else "Session aborted.",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (neverStarted) "The clock never started, so nothing was recorded."
                else "Aborted sessions don't count toward your score or streak.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        } else {
            Text("Tallying your session…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
    }

    Spacer(Modifier.height(28.dp))
    EmberButton(text = "Back to Dashboard", onClick = onExit)
}

/** LLM narrative recap of the finished session — mirrors the web SessionRecapCard. */
@Composable
private fun AiRecapCard(state: RoomUiState, onRegenerate: () -> Unit) {
    val colors = Stackd.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val recap = state.aiRecap
    val loading = state.aiRecapLoading
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.06f), app.stackd.core.theme.Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.25f), app.stackd.core.theme.Radius2Xl)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI / SESSION_RECAP", style = MonoLabelSmall, color = colors.accent, modifier = Modifier.weight(1f))
            Text(
                if (loading) "COMPOSING…" else "REGENERATE →",
                style = MonoLabelSmall,
                color = if (loading) colors.textMuted else colors.textPrimary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(enabled = !loading, role = androidx.compose.ui.semantics.Role.Button, onClick = onRegenerate)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 4.dp),
            )
        }
        when {
            recap == null && loading -> {
                Spacer(Modifier.height(6.dp))
                Text("Composing your recap…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            recap == null -> {
                Spacer(Modifier.height(6.dp))
                Text("SIGNAL LOST", style = MonoLabelSmall, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                GhostButton(text = "Retry", onClick = onRegenerate)
            }
            else -> RecapBody(recap)
        }
        if (recap != null) {
            Spacer(Modifier.height(16.dp))
            GhostButton(
                text = "Download PDF",
                enabled = !loading,
                onClick = {
                    val input = state.aiRecapInput ?: return@GhostButton
                    val me = state.participants.firstOrNull { it.userId == state.meId }?.displayName
                    if (!RecapPdf.share(context, recap, input, me)) {
                        android.widget.Toast.makeText(context, "Couldn't export the recap.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
            )
        }
    }
}

@Composable
private fun RecapBody(recap: app.stackd.data.ai.SessionRecap) {
    val colors = Stackd.colors
    Column {
        Spacer(Modifier.height(6.dp))
        Text(
            recap.title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(recap.summary, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        recap.reflections.forEach { line ->
            Spacer(Modifier.height(6.dp))
            Text("· $line", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        }
        if (recap.nextStep.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("NEXT · ${recap.nextStep}", style = MonoLabelSmall, color = colors.accent)
        }
    }
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
