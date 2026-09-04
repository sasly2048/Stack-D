package app.stackd.feature.dashboard

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Stackd
import app.stackd.data.profile.DAILY_REWARDS
import app.stackd.data.profile.RewardStatus

/**
 * Daily login reward — web's `daily-reward-card.tsx`: claim button, streak
 * line, and the 7-day XP cycle strip with today's slot highlighted.
 */
@Composable
fun DailyRewardCard(
    reward: RewardStatus,
    claiming: Boolean,
    notice: String?,
    onClaim: () -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.04f), Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.3f), Radius2Xl)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("DAILY", style = MonoLabelSmall, color = colors.accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (reward.claimedToday) "Claimed" else "+${reward.nextRewardXp} XP waiting",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Streak · ${reward.streak} days · Day ${reward.nextDayOfStreak}/7 in cycle",
                    style = MonoLabelSmall,
                    color = colors.textMuted,
                )
            }
            Text(
                when {
                    reward.claimedToday -> "TOMORROW"
                    claiming -> "CLAIMING…"
                    else -> "CLAIM"
                },
                style = MonoLabelSmall,
                color = if (reward.claimedToday) colors.textMuted else colors.accent,
                modifier = Modifier
                    .border(
                        1.dp,
                        if (reward.claimedToday) colors.border else colors.accent.copy(alpha = 0.5f),
                        RadiusMd,
                    )
                    .clickable(enabled = !reward.claimedToday && !claiming) { onClaim() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DAILY_REWARDS.forEachIndexed { i, xp ->
                val active = i + 1 == reward.nextDayOfStreak && !reward.claimedToday
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .background(
                            if (active) colors.accent.copy(alpha = 0.3f)
                            else colors.textPrimary.copy(alpha = 0.03f),
                            RadiusMd,
                        )
                        .then(
                            if (active) Modifier.border(1.dp, colors.accent, RadiusMd) else Modifier,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$xp",
                        style = MonoLabelSmall,
                        color = if (active) colors.accent else colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MonoLabelSmall, color = colors.accent)
        }
    }
}
