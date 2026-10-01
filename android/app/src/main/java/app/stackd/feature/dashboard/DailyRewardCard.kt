package app.stackd.feature.dashboard

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
        Spacer(Modifier.height(12.dp))
        // IntrinsicSize.Min so both chips share the taller one's height.
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            reward?.let { RewardChip(it, claiming, onClaim, Modifier.weight(1f).fillMaxHeight()) }
            if (showChallenge) ChallengeChip(challengeProgress, Modifier.weight(1f).fillMaxHeight())
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
    // Days of the 7-day cycle already banked. After claiming day 7 the next
    // day wraps to 1, which would read as an empty cycle, so show it full.
    val banked = (reward.nextDayOfStreak - 1).let { if (reward.claimedToday && it == 0) DAILY_REWARDS.size else it }
    Row(
        modifier = modifier
            .tappable(enabled = claimable, onClick = onClaim)
            .background(
                if (claimable) colors.accent.copy(alpha = 0.10f) else colors.textPrimary.copy(alpha = 0.03f),
                RadiusMd,
            )
            .border(1.dp, if (claimable) colors.accent.copy(alpha = 0.4f) else colors.border, RadiusMd)
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            app.stackd.core.ui.StackdIcons.CardGiftcard,
            contentDescription = null,
            tint = if (claimable) colors.accent else colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                when {
                    reward.claimedToday -> "Reward claimed"
                    claiming -> "Claiming…"
                    else -> "Claim +${reward.nextRewardXp} XP"
                },
                // Web renders the reward line in its serif ("+10 XP waiting").
                style = MaterialTheme.typography.titleMedium,
                fontFamily = app.stackd.core.theme.SerifFamily,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(DAILY_REWARDS.size) { i ->
                    val today = !reward.claimedToday && i + 1 == reward.nextDayOfStreak
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
        }
    }
}

@Composable
private fun ChallengeChip(progress: Float, modifier: Modifier) {
    val colors = Stackd.colors
    val done = progress >= 1f
    Row(
        modifier = modifier
            .background(colors.textPrimary.copy(alpha = 0.03f), RadiusMd)
            .border(1.dp, colors.border, RadiusMd)
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
