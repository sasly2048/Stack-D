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
    vm: StartViewModel = viewModel(
        factory = stackdViewModel {
            StartViewModel(it.auth, it.profiles, it.rooms, it.settings)
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

/** The headline choice: a big serif number, its consequence, and the controls. */
@Composable
private fun DurationHero(state: StartUiState, onDurationChange: (Int) -> Unit) {
    val colors = Stackd.colors
    val ends = remember(state.duration) {
        LocalTime.now().plusMinutes(state.duration.toLong()).format(DateTimeFormatter.ofPattern("h:mm a"))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.10f), colors.surface)),
                Radius2Xl,
            )
            .border(1.dp, colors.accent.copy(alpha = 0.18f), Radius2Xl)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FOCUS FOR", style = MonoLabelSmall, color = colors.textMuted)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(
                targetState = state.duration,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
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
        Text(
            if (state.durationLocked) "Set by template" else "Ends around $ends",
            style = MaterialTheme.typography.bodySmall,
            color = colors.accent,
        )
        Spacer(Modifier.height(16.dp))
        Slider(
            value = state.duration.toFloat(),
            onValueChange = {
                val stepped = (it / StartUiState.STEP_MINUTES).toInt() * StartUiState.STEP_MINUTES
                onDurationChange(stepped.coerceAtLeast(StartUiState.MIN_MINUTES))
            },
            valueRange = StartUiState.MIN_MINUTES.toFloat()..StartUiState.MAX_MINUTES.toFloat(),
            enabled = !state.durationLocked,
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.textPrimary.copy(alpha = 0.12f),
                disabledThumbColor = colors.textMuted,
                disabledActiveTrackColor = colors.textMuted,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StartUiState.QUICK_DURATIONS.forEach { m ->
                Pill(
                    label = if (m >= 60 && m % 60 == 0) "${m / 60}h" else "${m}m",
                    badge = when {
                        m == StartUiState.RECOMMENDED_MINUTES -> "rec"
                        state.lastMinutes == m -> "last"
                        else -> null
                    },
                    selected = state.duration == m,
                    enabled = !state.durationLocked,
                    onClick = { onDurationChange(m) },
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
            .pressFeedback(source)
            .selectable(selected, enabled = enabled, interactionSource = source, indication = null, role = Role.RadioButton, onClick = onClick)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = FontWeight.SemiBold)
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
            .pressFeedback(source)
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
                    .selectable(selected, role = Role.RadioButton) { onSetMode(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
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
            .clickable(role = Role.Button) { open = !open }
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
                text = if (state.busy) "Creating room…" else "Create room",
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
            .border(1.dp, colors.accent.copy(alpha = 0.25f), RadiusMd)
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
