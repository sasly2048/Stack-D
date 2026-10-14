package app.stackd.feature.start

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.settings.SettingsStore
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.RadiusXl
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.ErrorBanner
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.StackdField
import app.stackd.core.ui.sessionShared
import com.composables.icons.lucide.ChevronRight
import app.stackd.core.ui.pressFeedback
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * New-session configurator. The ViewModel owns creation; this composable hoists
 * navigation through [onRoomCreated], fired once with the new room's code.
 */
@Composable
fun StartRoute(
    onRoomCreated: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onJoinRoom: (String) -> Unit = {},
    /** From a Home ritual chip: opens the form already set to this length/name. */
    presetMinutes: Int? = null,
    presetTitle: String? = null,
    vm: StartViewModel = viewModel(
        factory = stackdViewModel {
            StartViewModel(it.auth, it.profiles, it.rooms, it.settings, presetMinutes, presetTitle)
        },
    ),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdCode) {
        state.createdCode?.let {
            onRoomCreated(it)
            vm.consumeCreated()
        }
    }

    StartScreen(
        state = state,
        onSelectTemplate = vm::selectTemplate,
        onTitleChange = vm::onTitleChange,
        onGoalHoursChange = vm::onGoalHoursChange,
        onDurationChange = vm::onDurationChange,
        onSetMode = vm::setMode,
        onDismissIntro = vm::dismissIntro,
        onCreate = vm::create,
        onJoinRoom = onJoinRoom,
        onBack = onBack,
    )
}

/**
 * One decision per block, most important first: how long (the hero), what
 * kind (templates), how strict (mode). Name and goal are optional, so they
 * sit behind a disclosure; the create action is pinned so it never scrolls
 * away from the choices it confirms.
 */
@Composable
fun StartScreen(
    state: StartUiState,
    onSelectTemplate: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onGoalHoursChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onSetMode: (String) -> Unit,
    onDismissIntro: () -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    onJoinRoom: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
) {
    val colors = Stackd.colors
    Box(modifier.fillMaxSize().background(colors.background)) {
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            app.stackd.core.ui.ResponsiveColumn {
                app.stackd.core.ui.ScreenHeader("NEW / SESSION", onBack, title = "Plan a session")
                Spacer(Modifier.height(20.dp))

                if (state.showIntro) {
                    IntroTip(onDismiss = onDismissIntro)
                    Spacer(Modifier.height(20.dp))
                }

                DurationHero(state, onDurationChange)

                if (state.templates.isNotEmpty()) {
                    Spacer(Modifier.height(32.dp))
                    BlockLabel("Start from")
                    Spacer(Modifier.height(12.dp))
                    Templates(state, onSelectTemplate)
                }

                Spacer(Modifier.height(32.dp))
                BlockLabel("Strictness")
                Spacer(Modifier.height(12.dp))
                ModeSegment(state.mode, onSetMode)

                Spacer(Modifier.height(24.dp))
                Details(state, onTitleChange, onGoalHoursChange)

                Spacer(Modifier.height(40.dp))
                JoinWithCode(onJoinRoom)
                // Room for the pinned action bar.
                Spacer(Modifier.height(140.dp))
            }
        }
        CreateBar(state, onCreate, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BlockLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Stackd.colors.textPrimary,
        fontWeight = FontWeight.SemiBold,
    )
}

/** What a length is for, in the words the line under the number uses. */
private fun meaningOf(minutes: Int): String = when (minutes) {
    15 -> "A quick reset"
    25 -> "One classic focus block"
    30 -> "A steady half hour"
    45 -> "A proper deep-work block"
    60 -> "A full hour of deep work"
    90 -> "One full deep-work cycle"
    120 -> "A long haul"
    StartUiState.MAX_MINUTES -> "The deep end"
    else -> "Your own length"
}

/**
 * The headline choice: a big serif number, what it means, and the controls.
 * The slider snaps to the same stops as the chips beneath it, so the two can
 * never disagree. Each snap is a light tick; reaching the longest stack is a
 * firmer one plus an ember halo behind the number and thumb.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DurationHero(state: StartUiState, onDurationChange: (Int) -> Unit) {
    val colors = Stackd.colors
    val view = androidx.compose.ui.platform.LocalView.current
    val stops = StartUiState.QUICK_DURATIONS
    val ends = remember(state.duration) {
        LocalTime.now().plusMinutes(state.duration.toLong()).format(DateTimeFormatter.ofPattern("h:mm a"))
    }
    val atMax = state.duration == StartUiState.MAX_MINUTES
    // Bloom once on arrival at max, then hold a faint glow while it stays there.
    val halo = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(atMax) {
        if (atMax) {
            halo.animateTo(1f, androidx.compose.animation.core.tween(420, easing = app.stackd.core.theme.EaseRitual))
            halo.animateTo(0.45f, androidx.compose.animation.core.tween(900, easing = app.stackd.core.theme.EaseRitual))
        } else {
            halo.animateTo(0f, androidx.compose.animation.core.tween(300))
        }
    }
    fun pick(minutes: Int) {
        if (minutes == state.duration && !state.durationLocked) return
        // Arriving at the longest stack is its own small moment, every time.
        if (minutes == StartUiState.MAX_MINUTES) app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.OPEN)
        view.performHapticFeedback(
            if (minutes == StartUiState.MAX_MINUTES && android.os.Build.VERSION.SDK_INT >= 30) {
                android.view.HapticFeedbackConstants.CONFIRM
            } else {
                android.view.HapticFeedbackConstants.CLOCK_TICK
            },
        )
        onDurationChange(minutes)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.10f), colors.surface)),
                Radius2Xl,
            )
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FOCUS FOR", style = MonoLabelSmall, color = colors.textMuted)
        Spacer(Modifier.height(4.dp))
        Box(contentAlignment = Alignment.Center) {
            // Ember halo behind the number at the longest stack.
            Box(
                Modifier
                    .size(width = 220.dp, height = 120.dp)
                    .graphicsLayer { alpha = halo.value }
                    .background(
                        Brush.radialGradient(listOf(colors.accentGlow.copy(alpha = 0.55f), colors.accent.copy(alpha = 0.18f), Color.Transparent)),
                    ),
            )
            Row(Modifier.sessionShared("duration", morph = true), verticalAlignment = Alignment.Bottom) {
                // The number rolls the way the slider moved (longer rises, shorter
                // falls) instead of two values cross-fading over each other.
                AnimatedContent(
                    targetState = state.duration,
                    transitionSpec = {
                        val up = targetState > initialState
                        val ease = app.stackd.core.theme.EaseRitual
                        (androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(180, easing = ease)) { if (up) it / 2 else -it / 2 } +
                            fadeIn(androidx.compose.animation.core.tween(180, easing = ease)))
                            .togetherWith(
                                androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(180, easing = ease)) { if (up) -it / 2 else it / 2 } +
                                    fadeOut(androidx.compose.animation.core.tween(90)),
                            )
                            .using(androidx.compose.animation.SizeTransform(clip = false))
                    },
                    label = "duration",
                ) { d ->
                    Text(
                        if (d >= 60 && d % 60 == 0) "${d / 60}" else "$d",
                        style = MaterialTheme.typography.displayLarge.copy(fontFamily = SerifFamily),
                        color = colors.textPrimary,
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (state.duration >= 60 && state.duration % 60 == 0) {
                        if (state.duration == 60) "hour" else "hours"
                    } else {
                        "min"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textMuted,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        AnimatedContent(
            targetState = "${meaningOf(state.duration)} · ends $ends",
            // Old line leaves before the new one lands, so they never double-expose.
            transitionSpec = {
                fadeIn(androidx.compose.animation.core.tween(150, delayMillis = 60)) togetherWith
                    fadeOut(androidx.compose.animation.core.tween(90))
            },
            label = "meaning",
        ) { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = colors.accent)
        }
        Spacer(Modifier.height(16.dp))
        val index = stops.indexOf(state.duration).takeIf { it >= 0 }
            ?: stops.indexOf(StartUiState.snap(state.duration))
        // The thumb swells while held: the control answers the finger directly.
        val sliderSource = remember { MutableInteractionSource() }
        val sliderPressed by sliderSource.collectIsPressedAsState()
        val sliderDragged by sliderSource.collectIsDraggedAsState()
        val thumbScale by animateFloatAsState(
            if (sliderPressed || sliderDragged) 1.15f else 1f,
            androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
            label = "thumb",
        )
        Slider(
            value = index.toFloat(),
            onValueChange = { pick(stops[it.roundToInt().coerceIn(0, stops.lastIndex)]) },
            valueRange = 0f..stops.lastIndex.toFloat(),
            steps = stops.size - 2,
            interactionSource = sliderSource,
            // Own thumb + track: M3's default draws a detached bar thumb and a
            // stop dot that read as a broken control at the low end.
            thumb = {
                Box(Modifier.graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }, contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .graphicsLayer { alpha = halo.value }
                            .background(Brush.radialGradient(listOf(colors.accentGlow.copy(alpha = 0.7f), Color.Transparent)), CircleShape),
                    )
                    Box(
                        Modifier
                            .size(26.dp)
                            .background(colors.textPrimary, CircleShape),
                    )
                }
            },
            track = { s ->
                val frac = ((s.value - s.valueRange.start) / (s.valueRange.endInclusive - s.valueRange.start)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.textPrimary.copy(alpha = 0.12f), CircleShape),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .height(6.dp)
                            .background(colors.accent, CircleShape),
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StartUiState.CHIP_DURATIONS.forEach { m ->
                Pill(
                    label = if (m >= 60 && m % 60 == 0) "${m / 60}h" else "${m}m",
                    badge = if (state.lastMinutes == m && state.duration != m) "last" else null,
                    selected = state.duration == m,
                    enabled = true,
                    onClick = { pick(m) },
                )
            }
        }
    }
}
/** Silver when chosen (the web's primary), quiet glass otherwise. */
@Composable
private fun Pill(label: String, badge: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val bg by animateColorAsState(
        if (selected) colors.textPrimary else colors.textPrimary.copy(alpha = 0.05f),
        label = "pillBg",
    )
    val fg = if (selected) colors.background else colors.textPrimary
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .pressFeedback(source, sound = app.stackd.core.feedback.Sfx.Kind.SELECT)
            .selectable(selected, enabled = enabled, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = fg, fontWeight = FontWeight.SemiBold)
        badge?.let {
            Text(it, style = MonoLabelSmall, color = if (selected) colors.background.copy(alpha = 0.6f) else colors.accent)
        }
    }
}

/** Templates as a swipeable shelf: scanning sideways beats a tall stack of boxes. */
@Composable
private fun Templates(state: StartUiState, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TemplateCard("Custom", "Your own length and rules.", null, state.tplKey == "") { onSelect("") }
        state.templates.forEach { tpl ->
            TemplateCard(
                tpl.title,
                tpl.description,
                "${tpl.targetDurationSeconds / 60} min",
                state.tplKey == tpl.key,
            ) { onSelect(tpl.key) }
        }
    }
}

@Composable
private fun TemplateCard(title: String, desc: String, meta: String?, selected: Boolean, onClick: () -> Unit) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val border by animateColorAsState(if (selected) colors.accent else colors.border, label = "tplBorder")
    Column(
        Modifier
            .width(168.dp)
            .heightIn(min = 120.dp)
            .pressFeedback(source, sound = app.stackd.core.feedback.Sfx.Kind.SELECT)
            .selectable(selected, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .background(
                if (selected) colors.accent.copy(alpha = 0.08f) else colors.textPrimary.copy(alpha = 0.03f),
                RadiusXl,
            )
            .border(1.dp, border, RadiusXl)
            .padding(16.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            desc,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        meta?.let {
            Spacer(Modifier.height(10.dp))
            Text(it.uppercase(), style = MonoLabelSmall, color = colors.accent)
        }
    }
}

/** Two-way choice = segmented control, with only the chosen one explained. */
@Composable
private fun ModeSegment(mode: String, onSetMode: (String) -> Unit) {
    val colors = Stackd.colors
    val options = listOf(
        SettingsStore.MODE_GENTLE to "Gentle",
        SettingsStore.MODE_ABSOLUTE to "Absolute",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = mode == key
            val bg by animateColorAsState(if (selected) colors.textPrimary else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .background(bg, RoundedCornerShape(50))
                    .selectable(selected, role = Role.RadioButton) {
                        if (!selected) app.stackd.core.feedback.Sfx.play(app.stackd.core.feedback.Sfx.Kind.SELECT)
                        onSetMode(key)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) colors.background else colors.textMuted,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    AnimatedContent(mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "modeDesc") { m ->
        Text(
            if (m == SettingsStore.MODE_GENTLE) {
                "For a desk. Small wobbles are logged, not penalised, with a soft buzz as a warning."
            } else {
                "For a table of friends. Any lift, tilt or screen wake breaks your stack."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
}

/** Optional naming and group goal, folded away until wanted. */
@Composable
private fun Details(state: StartUiState, onTitleChange: (String) -> Unit, onGoalHoursChange: (Int) -> Unit) {
    val colors = Stackd.colors
    var open by rememberSaveable { mutableStateOf(state.title.isNotBlank() || state.goalHours > 0) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                app.stackd.core.feedback.Sfx.play(if (open) app.stackd.core.feedback.Sfx.Kind.CLOSE else app.stackd.core.feedback.Sfx.Kind.OPEN)
                open = !open
            }
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Add a name or group goal",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            com.composables.icons.lucide.Lucide.ChevronRight,
            contentDescription = if (open) "Hide details" else "Show details",
            tint = colors.textMuted,
            modifier = Modifier.rotate(if (open) 90f else 0f),
        )
    }
    AnimatedVisibility(open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column {
            Spacer(Modifier.height(8.dp))
            StackdField(
                label = "Room name",
                value = state.title,
                onValueChange = onTitleChange,
                placeholder = "Deep work Monday",
            )
            Spacer(Modifier.height(16.dp))
            StackdField(
                label = "Group goal (hours)",
                value = if (state.goalHours == 0) "" else state.goalHours.toString(),
                onValueChange = { onGoalHoursChange(it.filter(Char::isDigit).toIntOrNull() ?: 0) },
                placeholder = "e.g. 10",
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
        }
    }
}

/** Pinned action: scrim fades content out beneath it, button stays thumb-reachable. */
@Composable
private fun CreateBar(state: StartUiState, onCreate: () -> Unit, modifier: Modifier) {
    val colors = Stackd.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Color.Transparent, 0.3f to colors.background))
            .navigationBarsPadding()
            .padding(top = 28.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 560.dp).padding(horizontal = 20.dp)) {
            state.error?.let {
                ErrorBanner(it, onRetry = onCreate)
                Spacer(Modifier.height(12.dp))
            }
            EmberButton(
                text = when {
                    state.busy -> "Creating stack…"
                    state.durationLocked -> "Create Stack"
                    state.duration % 60 == 0 -> "Create ${state.duration / 60}-hour Stack"
                    else -> "Create ${state.duration}-min Stack"
                },
                onClick = onCreate,
                enabled = !state.busy,
                busy = state.busy,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "You'll get a 6-character key to share with the table.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/** Mirrors web normalizeCode/ROOM_CODE_PATTERN: uppercase alphanumerics, exactly 6. */
@Composable
private fun JoinWithCode(onJoinRoom: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    BlockLabel("Have a key?")
    Spacer(Modifier.height(12.dp))
    StackdField(
        label = "Room key",
        value = code,
        onValueChange = {
            code = it.uppercase().filter { c -> c in 'A'..'Z' || c in '0'..'9' }.take(6)
            error = null
        },
        placeholder = "ABC123",
        isError = error != null,
        hint = error,
        imeAction = androidx.compose.ui.text.input.ImeAction.Go,
        centeredMono = true,
    )
    Spacer(Modifier.height(12.dp))
    GhostButton(
        text = "Join room",
        onClick = { if (code.length == 6) onJoinRoom(code) else error = "Enter the full 6-character key." },
    )
}

@Composable
private fun IntroTip(onDismiss: () -> Unit) {
    val colors = Stackd.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.06f), RadiusMd)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "A room is a shared timer — everyone stacks their phones face-down and " +
                "holds the silence until it runs out.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Text("GOT IT", style = MonoLabelSmall, color = colors.textMuted)
        }
    }
}
