package app.stackd.feature.start

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.settings.SettingsStore
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.RadiusLg
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.RadiusXl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.ErrorBanner
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.StackdField
import app.stackd.data.room.RoomTemplate

/**
 * New-session configurator. The ViewModel owns creation; this composable hoists
 * navigation through [onRoomCreated], fired once with the new room's code.
 */
@Composable
fun StartRoute(
    onRoomCreated: (String) -> Unit,
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
    )
}

@OptIn(ExperimentalLayoutApi::class)
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
) {
    val colors = Stackd.colors
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
      app.stackd.core.ui.ResponsiveColumn {
        Text("NEW / CONFIGURE", style = MonoLabel, color = colors.textMuted)
        Spacer(Modifier.height(8.dp))
        Text(
            "Set the protocol.",
            style = MaterialTheme.typography.displaySmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(24.dp))

        if (state.showIntro) {
            IntroTip(onDismiss = onDismissIntro)
            Spacer(Modifier.height(24.dp))
        }

        if (state.templates.isNotEmpty()) {
            SectionLabel("TEMPLATE")
            Spacer(Modifier.height(12.dp))
            TemplateCard(
                title = "Custom",
                desc = "Configure manually.",
                selected = state.tplKey == "",
                onClick = { onSelectTemplate("") },
            )
            state.templates.forEach { tpl ->
                Spacer(Modifier.height(8.dp))
                TemplateCard(
                    title = tpl.title,
                    desc = tpl.description,
                    meta = "${(tpl.targetDurationSeconds / 60)}m · ${tpl.visibility}",
                    selected = state.tplKey == tpl.key,
                    onClick = { onSelectTemplate(tpl.key) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }

        StackdField(
            label = "Room Title (optional)",
            value = state.title,
            onValueChange = onTitleChange,
            placeholder = "Deep work Monday",
        )
        Spacer(Modifier.height(20.dp))

        StackdField(
            label = "Collective goal — hours (optional)",
            value = if (state.goalHours == 0) "" else state.goalHours.toString(),
            onValueChange = { onGoalHoursChange(it.filter(Char::isDigit).toIntOrNull() ?: 0) },
            placeholder = "0",
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
        )
        Spacer(Modifier.height(28.dp))

        // Duration
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("TARGET DURATION", style = MonoLabel, color = colors.textMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.duration.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                Text(" MIN", style = MonoLabelSmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.height(12.dp))
        Slider(
            value = state.duration.toFloat(),
            onValueChange = { onDurationChange(it.toInt()) },
            valueRange = StartUiState.MIN_MINUTES.toFloat()..StartUiState.MAX_MINUTES.toFloat(),
            // (240-5)/5 = 47 stops between ends; step count is stops minus one.
            steps = (StartUiState.MAX_MINUTES - StartUiState.MIN_MINUTES) / StartUiState.STEP_MINUTES - 1,
            enabled = !state.durationLocked,
            colors = SliderDefaults.colors(
                thumbColor = colors.textPrimary,
                activeTrackColor = colors.textPrimary,
                inactiveTrackColor = colors.textPrimary.copy(alpha = 0.15f),
                disabledThumbColor = colors.textMuted,
                disabledActiveTrackColor = colors.textMuted,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("5m", "30m", "1h", "2h", "4h").forEach {
                Text(it, style = MonoLabelSmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.height(16.dp))

        // Quick picks
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StartUiState.QUICK_DURATIONS.forEach { m ->
                val selected = state.duration == m
                val badge = when {
                    m == StartUiState.RECOMMENDED_MINUTES -> "REC"
                    state.lastMinutes == m -> "LAST"
                    else -> null
                }
                DurationChip(
                    label = "${m}m",
                    badge = badge,
                    selected = selected,
                    enabled = !state.durationLocked,
                    onClick = { onDurationChange(m) },
                )
            }
        }
        // A remembered value that isn't a preset is otherwise silently restored
        // with no explanation for the odd number.
        state.lastMinutes?.takeIf { it !in StartUiState.QUICK_DURATIONS }?.let {
            Spacer(Modifier.height(10.dp))
            Text("${it}m · LAST USED", style = MonoLabelSmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(28.dp))

        // Enforcement mode
        SectionLabel("ENFORCEMENT PROFILE")
        Spacer(Modifier.height(12.dp))
        ModeOption(
            title = "Gentle",
            desc = "Desk workspace. Minor wobbles logged, not penalized. Soft vibration warnings.",
            selected = state.mode == SettingsStore.MODE_GENTLE,
            onClick = { onSetMode(SettingsStore.MODE_GENTLE) },
        )
        Spacer(Modifier.height(8.dp))
        ModeOption(
            title = "Absolute",
            desc = "Group settings. Any movement, tab switch, or screen wake ends the session.",
            selected = state.mode == SettingsStore.MODE_ABSOLUTE,
            onClick = { onSetMode(SettingsStore.MODE_ABSOLUTE) },
        )
        Spacer(Modifier.height(28.dp))

        state.error?.let {
            ErrorBanner(it, onRetry = onCreate)
            Spacer(Modifier.height(16.dp))
        }

        EmberButton(
            text = if (state.busy) "Forging key…" else "Forge Room Key",
            onClick = onCreate,
            enabled = !state.busy,
            busy = state.busy,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "A 6-character key will be generated. Share it with the table.",
            style = MonoLabelSmall,
            color = colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
        )
      }
    }
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
        Text(
            "GOT IT",
            style = MonoLabelSmall,
            color = colors.textMuted,
            modifier = Modifier.clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun TemplateCard(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
    meta: String? = null,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) colors.accent.copy(alpha = 0.05f) else colors.textPrimary.copy(alpha = 0.03f),
                RadiusLg,
            )
            .border(
                1.dp,
                if (selected) colors.accent else colors.border,
                RadiusLg,
            )
            .padding(14.dp),
    ) {
        Text(
            title.uppercase() + if (selected) " ✓" else "",
            style = MonoLabelSmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        meta?.let {
            Spacer(Modifier.height(4.dp))
            Text(it.uppercase(), style = MonoLabelSmall, color = colors.accent)
        }
    }
}

@Composable
private fun DurationChip(
    label: String,
    badge: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = Stackd.colors
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(
                if (selected) colors.textPrimary.copy(alpha = 0.08f * alpha) else colors.textPrimary.copy(alpha = 0.03f),
                RadiusMd,
            )
            .border(
                1.dp,
                if (selected) colors.textPrimary.copy(alpha = alpha) else colors.border,
                RadiusMd,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            (if (selected) "✓ " else "") + label,
            style = MonoLabelSmall,
            color = if (selected) colors.textPrimary.copy(alpha = alpha) else colors.textMuted,
        )
        badge?.let {
            Text(it, style = MonoLabelSmall, color = colors.accent.copy(alpha = alpha))
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) colors.textPrimary.copy(alpha = 0.06f) else colors.background,
                RadiusXl,
            )
            .border(
                1.dp,
                if (selected) colors.textPrimary.copy(alpha = 0.5f) else colors.border,
                RadiusXl,
            )
            .padding(16.dp),
    ) {
        Text(
            title.uppercase() + if (selected) " ✓" else "",
            style = MonoLabel,
            color = if (selected) colors.textPrimary else colors.textMuted,
        )
        Spacer(Modifier.height(6.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}
