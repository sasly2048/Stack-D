package app.stackd.feature.dashboard

import app.stackd.core.ui.glassSurface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.data.profile.DAILY_REWARDS
import app.stackd.data.profile.RewardStatus

/**
 * Today's small wins, side by side: the daily login reward (web's
 * `daily-reward-card.tsx`) and the active challenge's progress. Chips, not
 * cards — they're secondary to starting a session, so they must not compete
 * with the hero for attention.
 */
@Composable
fun TodayStrip(
    reward: RewardStatus?,
    claiming: Boolean,
    notice: String?,
    challengeProgress: Float,
    onClaim: () -> Unit,
) {
    val showChallenge = challengeProgress > 0f
    if (reward == null && !showChallenge) return
    val colors = Stackd.colors
    Column(Modifier.fillMaxWidth()) {
        // Stacked full-width: the reward needs room to explain itself.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            reward?.let { RewardChip(it, claiming, onClaim, Modifier.fillMaxWidth()) }
            if (showChallenge) ChallengeChip(challengeProgress, Modifier.fillMaxWidth())
        }
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun RewardChip(
    reward: RewardStatus,
    claiming: Boolean,
    onClaim: () -> Unit,
    modifier: Modifier,
) {
    val colors = Stackd.colors
    val claimable = !reward.claimedToday && !claiming
    // nextDayOfStreak is today's day in the 7-day cycle (the one claimed, or
    // the one waiting); nextRewardXp is that day's reward.
    val day = reward.nextDayOfStreak
    val banked = if (reward.claimedToday) day else day - 1
    val tomorrowXp = DAILY_REWARDS[day % DAILY_REWARDS.size]
    Row(
        modifier = modifier
            .tappable(enabled = claimable, onClick = onClaim)
            .background(
                if (claimable) colors.accent.copy(alpha = 0.10f) else colors.textPrimary.copy(alpha = 0.03f),
                RadiusMd,
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            app.stackd.core.ui.StackdIcons.CardGiftcard,
            contentDescription = null,
            tint = if (claimable) colors.accent else colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Today's reward · +${reward.nextRewardXp} XP",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    reward.claimedToday -> "Claimed — next reward tomorrow (+$tomorrowXp XP)"
                    claiming -> "Claiming…"
                    else -> "Claim +${reward.nextRewardXp} XP"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (claimable) colors.accent else colors.textMuted,
            )
            Spacer(Modifier.height(8.dp))
            // The dots are the 7-day cycle of daily rewards: one per day claimed in a row.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(DAILY_REWARDS.size) { i ->
                        val today = !reward.claimedToday && i + 1 == day
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(
                                    when {
                                        i < banked -> colors.accent
                                        today -> colors.accent.copy(alpha = 0.45f)
                                        else -> colors.textPrimary.copy(alpha = 0.12f)
                                    },
                                    CircleShape,
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Day $day of ${DAILY_REWARDS.size} · daily streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ChallengeChip(progress: Float, modifier: Modifier) {
    val colors = Stackd.colors
    val done = progress >= 1f
    Row(
        modifier = modifier
            .glassSurface(RadiusMd)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            app.stackd.core.ui.StackdIcons.EmojiEvents,
            contentDescription = null,
            tint = if (done) colors.accent else colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (done) "Challenge done" else "Challenge ${Math.round(progress * 100)}%",
                // Web renders the reward line in its serif ("+10 XP waiting").
                style = MaterialTheme.typography.titleMedium,
                fontFamily = app.stackd.core.theme.SerifFamily,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.textPrimary.copy(alpha = 0.08f), CircleShape)) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(colors.accent, CircleShape),
                )
            }
        }
    }
}
