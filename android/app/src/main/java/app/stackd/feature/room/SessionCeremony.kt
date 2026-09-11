package app.stackd.feature.room

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.data.recap.SessionSummary

/**
 * Cinematic post-session ceremony — the Android counterpart to the web's
 * SessionCeremony. Reveals beats in order — Focus Score → XP → Level/Prestige →
 * Achievements → Lifetime Milestones → Rank change → Friends finished →
 * Continue — each fading in on a short timer, with the XP counting up. Beats
 * whose data is absent (no new achievements, no rank change) are skipped, so a
 * quiet session still gets Score → XP → Continue.
 *
 * Full-screen scrim over the room; dismissed by [onContinue].
 */
@Composable
fun SessionCeremony(summary: SessionSummary, onContinue: () -> Unit) {
    val colors = Stackd.colors

    // Which optional beats exist, in reveal order after score+xp.
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
    LaunchedEffect(summary) {
        while (beat < beats.size - 1) {
            kotlinx.coroutines.delay(if (beats[beat] == "xp") 2200 else 1400)
            beat++
        }
    }
    fun shown(key: String): Boolean {
        val i = beats.indexOf(key)
        return i >= 0 && beat >= i
    }

    // XP count-up, eased, once the xp beat is reached. Keyed on a boolean, not
    // `beat`: keying on `beat` restarted this effect on every later beat tick,
    // cancelling a still-running count-up (it takes ~1.8s, beats advance every
    // 1.4s) and freezing the number at a partial value. Gate on "have we passed
    // the xp beat" so it starts once and always runs to completion.
    val xpReached = beat >= beats.indexOf("xp")
    var xp by remember(summary) { mutableFloatStateOf(0f) }
    LaunchedEffect(summary, xpReached) {
        if (xpReached && summary.xpEarned > 0) {
            val steps = 40
            repeat(steps + 1) { s ->
                val p = s.toFloat() / steps
                xp = summary.xpEarned * (1 - (1 - p) * (1 - p) * (1 - p) * (1 - p))
                kotlinx.coroutines.delay(45)
            }
            xp = summary.xpEarned.toFloat()
        }
    }

    val mins = maxOf(1, Math.round(summary.durationSeconds / 60.0).toInt())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.96f))
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("HELD", style = MonoLabel, color = colors.accent)
            Spacer(Modifier.height(12.dp))
            Text(
                "$mins minutes held.",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            // 1. Focus Score
            Spacer(Modifier.height(40.dp))
            Text("FOCUS SCORE", style = MonoLabelSmall, color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                "${summary.score}",
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(summary.tier.uppercase(), style = MonoLabelSmall, color = colors.accent)

            // 2. XP
            Beat(shown("xp")) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "+${xp.toInt()} XP",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }

            // 3. Level / Prestige
            Beat(shown("level")) {
                Spacer(Modifier.height(28.dp))
                val prestige = if (summary.prestige > 0) "P${summary.prestige} · " else ""
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${prestige}LEVEL ${summary.level}", style = MonoLabelSmall, color = colors.textMuted)
                    Text(
                        "${summary.levelXpInto} / ${summary.levelXpSpan}",
                        style = MonoLabelSmall, color = colors.textMuted,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val pct = if (summary.levelXpSpan > 0) {
                    (summary.levelXpInto.toFloat() / summary.levelXpSpan).coerceIn(0f, 1f)
                } else 0f
                val animated by animateFloatAsState(
                    targetValue = if (shown("level")) pct else 0f,
                    animationSpec = tween(1000),
                    label = "levelBar",
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.textPrimary.copy(alpha = 0.05f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animated)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.accent),
                    )
                }
            }

            // 4. Achievements
            Beat(shown("achievements") && summary.achievements.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                summary.achievements.forEach { a ->
                    Text("◆ ${a.name.uppercase()}", style = MonoLabelSmall, color = colors.accent)
                }
            }

            // 4b. Lifetime milestones
            Beat(shown("milestones") && summary.milestones.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                summary.milestones.forEach { m ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(colors.accent.copy(alpha = 0.06f), Radius2Xl)
                            .border(1.dp, colors.accent.copy(alpha = 0.4f), Radius2Xl)
                            .padding(14.dp),
                    ) {
                        Text("LIFETIME MILESTONE", style = MonoLabelSmall, color = colors.accent)
                        Spacer(Modifier.height(4.dp))
                        Text(m.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        if (m.description.isNotBlank()) {
                            Text(m.description, style = MonoLabelSmall, color = colors.textMuted)
                        }
                    }
                }
            }

            // 5. Rank change
            Beat(shown("rank") && summary.rankNow != summary.rankBefore) {
                Spacer(Modifier.height(28.dp))
                val delta = summary.rankBefore - summary.rankNow
                val up = delta > 0
                Text(
                    "${if (up) "▲" else "▼"} ${Math.abs(delta)} · RANK #${summary.rankNow}",
                    style = MonoLabelSmall,
                    color = if (up) colors.accent else colors.textMuted,
                )
            }

            // 6. Friends finished
            Beat(shown("friends") && summary.friendsFinished.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("ALSO FINISHED TODAY", style = MonoLabelSmall, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                Text(
                    summary.friendsFinished.take(4)
                        .joinToString(" · ") { it.displayName?.takeIf { n -> n.isNotBlank() } ?: "Anon" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }

            // 7. Continue
            Spacer(Modifier.height(40.dp))
            Text(
                "CONTINUE",
                style = MonoLabelSmall,
                color = if (shown("continue")) colors.textPrimary else colors.textMuted,
                modifier = Modifier
                    .alpha(if (shown("continue")) 1f else 0.4f)
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, colors.border, RoundedCornerShape(50))
                    .clickable(enabled = shown("continue"), onClick = onContinue)
                    .padding(horizontal = 32.dp, vertical = 14.dp),
            )
        }
    }
}

/** Reveals its content only when [visible]; keeps the staged fade simple. */
@Composable
private fun Beat(visible: Boolean, content: @Composable () -> Unit) {
    if (!visible) return
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        content()
    }
}
