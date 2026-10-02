package app.stackd.feature.room

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.stackd.core.feedback.Sfx
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.Avatar
import app.stackd.core.ui.EaseRitual
import app.stackd.core.ui.EmberButton
import app.stackd.data.recap.SessionSummary
import com.composables.icons.lucide.Award
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.delay

/**
 * Post-session ceremony — the payoff moment, staged like a short film rather
 * than a results table (web SessionCeremony, Duolingo lesson-complete).
 *
 * Score → XP → Level → Achievements → Milestones → Rank → Friends → Continue.
 * Each beat rises in on a spring with its own sound and haptic; beats whose
 * data is absent are skipped, so a quiet session still gets
 * Score → XP → Continue. The action only appears once the story has played.
 */
@Composable
fun SessionCeremony(summary: SessionSummary, onContinue: () -> Unit) {
    val colors = Stackd.colors
    val view = LocalView.current

    val beats = remember(summary) {
        buildList {
            add("score"); add("xp"); add("level")
            if (summary.achievements.isNotEmpty()) add("achievements")
            if (summary.milestones.isNotEmpty()) add("milestones")
            if (summary.rankNow != summary.rankBefore) add("rank")
            if (summary.friendsFinished.isNotEmpty()) add("friends")
            add("continue")
        }
    }
    var beat by remember(summary) { mutableIntStateOf(0) }
    fun shown(key: String) = beats.indexOf(key).let { it >= 0 && beat >= it }

    // Score ring + count-up drive the opening beat.
    val scoreP = remember(summary) { Animatable(0f) }
    LaunchedEffect(summary) {
        Sfx.play(Sfx.Kind.SUCCESS)
        view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
        scoreP.animateTo(1f, tween(1400, easing = EaseRitual))
        while (beat < beats.size - 1) {
            delay(if (beats[beat] == "xp") 1900 else 1100)
            beat++
            when (beats[beat]) {
                "xp" -> Sfx.play(Sfx.Kind.XP)
                "level" -> Sfx.play(Sfx.Kind.SELECT)
                "achievements", "milestones" -> Sfx.play(Sfx.Kind.ACHIEVEMENT)
                "rank" -> if (summary.rankNow < summary.rankBefore) Sfx.play(Sfx.Kind.SUCCESS)
                "friends" -> Sfx.play(Sfx.Kind.NOTIFY)
            }
            if (beats[beat] != "continue") view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    // XP counts up once its beat lands (keyed on reaching it, so later beats
    // never cancel a running count).
    val xpReached = beat >= beats.indexOf("xp")
    val xp = remember(summary) { Animatable(0f) }
    LaunchedEffect(summary, xpReached) {
        if (xpReached && summary.xpEarned > 0) xp.animateTo(summary.xpEarned.toFloat(), tween(1500, easing = EaseRitual))
    }

    val mins = maxOf(1, Math.round(summary.durationSeconds / 60.0).toInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.97f))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$mins ${if (mins == 1) "minute" else "minutes"} held.",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    summary.breaches == 0 -> "Not a single break. That's the stack."
                    summary.breaches == 1 -> "One break. Still a strong hold."
                    else -> "${summary.breaches} breaks. Every hold builds the habit."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )

            // 1. Score ring
            Spacer(Modifier.height(32.dp))
            ScoreRing(summary.score, scoreP.value)
            Spacer(Modifier.height(10.dp))
            Text(
                tierLabel(summary.tier),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )

            // 2. XP
            Beat(shown("xp")) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "+${xp.value.toInt()} XP",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 3. Level bar
            Beat(shown("level")) {
                Spacer(Modifier.height(24.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val prestige = if (summary.prestige > 0) "P${summary.prestige} · " else ""
                    Text("${prestige}Level ${summary.level}", style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
                    Text(
                        "${summary.levelXpInto} / ${summary.levelXpSpan} XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val pct = if (summary.levelXpSpan > 0) (summary.levelXpInto.toFloat() / summary.levelXpSpan).coerceIn(0f, 1f) else 0f
                val fill by animateFloatAsState(if (shown("level")) pct else 0f, tween(1100, easing = EaseRitual), label = "level")
                Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.07f))) {
                    Box(Modifier.fillMaxWidth(fill).height(10.dp).clip(CircleShape).background(colors.accent))
                }
            }

            // 4. Achievements — badges pop with overshoot
            Beat(shown("achievements") && summary.achievements.isNotEmpty(), pop = true) {
                Spacer(Modifier.height(28.dp))
                Text("Unlocked", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(Modifier.height(12.dp))
                summary.achievements.forEach { a -> AwardRow(a.name, a.description) }
            }

            // 4b. Lifetime milestones
            Beat(shown("milestones") && summary.milestones.isNotEmpty(), pop = true) {
                Spacer(Modifier.height(20.dp))
                Text("Lifetime milestone", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(Modifier.height(12.dp))
                summary.milestones.forEach { m -> AwardRow(m.name, m.description) }
            }

            // 5. Rank change
            Beat(shown("rank") && summary.rankNow != summary.rankBefore) {
                Spacer(Modifier.height(24.dp))
                val delta = summary.rankBefore - summary.rankNow
                Text(
                    if (delta > 0) "Up ${delta} ${if (delta == 1) "place" else "places"} · now #${summary.rankNow}"
                    else "Now #${summary.rankNow} on the board",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (delta > 0) colors.accent else colors.textMuted,
                )
            }

            // 6. Friends finished
            Beat(shown("friends") && summary.friendsFinished.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    summary.friendsFinished.take(4).forEach {
                        Avatar(
                            url = it.avatarUrl,
                            name = it.displayName,
                            size = 32.dp,
                            modifier = Modifier.border(2.dp, colors.background, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    summary.friendsFinished.take(3).joinToString(", ") {
                        it.displayName?.substringBefore(' ')?.takeIf { n -> n.isNotBlank() } ?: "A friend"
                    } + " also focused today",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }

            // 7. Continue — appears when the story is told
            Spacer(Modifier.height(40.dp))
            Beat(shown("continue")) {
                EmberButton(text = "Continue", onClick = onContinue)
            }
        }
    }
}

private fun tierLabel(tier: String) = when (tier.lowercase()) {
    "pristine" -> "Pristine"
    "flow" -> "Flow state"
    "steady" -> "Steady"
    "compromised" -> "Protocol compromised"
    else -> tier.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Score as a filling arc; the number counts with the arc. */
@Composable
private fun ScoreRing(score: Int, p: Float) {
    val colors = Stackd.colors
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            drawArc(colors.textPrimary.copy(alpha = 0.07f), -90f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(
                colors.accent,
                -90f, 360f * (score / 100f) * p, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${(score * p).toInt()}",
                style = MaterialTheme.typography.displayLarge.copy(fontFamily = SerifFamily, fontSize = 64.sp),
                color = colors.textPrimary,
            )
            Text("focus score", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

@Composable
private fun AwardRow(name: String, desc: String) {
    val colors = Stackd.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.accent.copy(alpha = 0.07f), RoundedCornerShape(16.dp))
            .border(1.dp, colors.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(colors.accent.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Lucide.Award, null, tint = colors.accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
    }
}

/** A beat rising into place (spring); [pop] adds a scale overshoot for rewards. */
@Composable
private fun Beat(visible: Boolean, pop: Boolean = false, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) +
            slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) { it / 3 } +
            (if (pop) scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f) else fadeIn()),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** Debug-only sample so the ceremony can be previewed without finishing a session. */
internal fun sampleSessionSummary() = SessionSummary(
    score = 92, tier = "flow", durationSeconds = 45 * 60, breaches = 0, xpEarned = 640,
    lifetimeXp = 2860, prestige = 0, level = 7, levelXpInto = 380, levelXpSpan = 600, streak = 3,
    achievements = listOf(app.stackd.data.recap.AwardCard("deep", "Deep Diver", "Held a 45-minute stack")),
    milestones = emptyList(), rankNow = 12, rankBefore = 15, personality = null,
    friendsFinished = emptyList(),
)
