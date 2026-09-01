package app.stackd.feature.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Catalog row joined with the caller's unlock — web's `Achievement`. */
@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String = "",
    /** bronze | silver | gold | obsidian */
    val tier: String = "bronze",
    @SerialName("xp_reward") val xpReward: Int = 0,
    @SerialName("sort_order") val sortOrder: Int = 0,
) {
    var unlockedAt: String? = null
}

@Serializable
internal data class UnlockRow(
    @SerialName("achievement_id") val achievementId: String,
    @SerialName("unlocked_at") val unlockedAt: String? = null,
)

data class AchievementsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<Achievement> = emptyList(),
    val unlocked: Int = 0,
    val lifetimeXp: Long = 0,
) {
    val total: Int get() = rows.size
}

/** Ported from `achievements.functions.ts`: catalog + own unlocks, joined here. */
class AchievementsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AchievementsUiState())
    val state: StateFlow<AchievementsUiState> = _state

    init {
        load()
    }

    fun load() {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(loading = true, error = false)
        viewModelScope.launch {
            runCatching {
                val catalog = container.client.postgrest.from("achievements")
                    .select(
                        Columns.list(
                            "id", "name", "description", "icon", "tier", "xp_reward", "sort_order",
                        ),
                    ) { order("sort_order", Order.ASCENDING) }
                    .decodeList<Achievement>()
                val unlocks = container.client.postgrest.from("user_achievements")
                    .select(Columns.list("achievement_id", "unlocked_at")) {
                        filter { eq("user_id", userId) }
                    }
                    .decodeList<UnlockRow>()
                val byId = unlocks.associate { it.achievementId to it.unlockedAt }
                catalog.forEach { it.unlockedAt = byId[it.id] }
                val xp = container.profiles.getProfile(userId)?.lifetimeXp ?: 0
                Triple(catalog, byId.size, xp)
            }.fold(
                onSuccess = { (rows, unlocked, xp) ->
                    _state.value = AchievementsUiState(
                        loading = false, rows = rows, unlocked = unlocked, lifetimeXp = xp,
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = true)
                },
            )
        }
    }
}

@Composable
fun AchievementsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: AchievementsViewModel = viewModel(factory = stackdViewModel { AchievementsViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AchievementsScreen(state = state, onRetry = vm::load, onBack = onBack, modifier = modifier)
}

/** Tier accent colors, matching the web's TIER_STYLES intent. */
private fun tierColor(tier: String, fallback: Color): Color = when (tier) {
    "bronze" -> Color(0xFFCD7F32)
    "silver" -> Color(0xFFC0C0C0)
    "gold" -> Color(0xFFFFD700)
    "obsidian" -> Color(0xFFFFFFFF)
    else -> fallback
}

@Composable
fun AchievementsScreen(
    state: AchievementsUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            Text("STACK'D / ACHIEVEMENTS", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("YOUR MARKS")
            Spacer(Modifier.height(4.dp))
            Text(
                "${state.unlocked} of ${state.total} unlocked",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted,
            )
            if (!state.loading && !state.error) {
                Spacer(Modifier.height(16.dp))
                ChapterCard(state.lifetimeXp)
            }
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load achievements.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> state.rows.forEach { a ->
                    val unlocked = a.unlockedAt != null
                    val accent = tierColor(a.tier, colors.accent)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
                            .border(
                                1.dp,
                                if (unlocked) accent.copy(alpha = 0.5f) else colors.border,
                                Radius2Xl,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            a.icon.ifBlank { "◆" },
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.width(40.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                a.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (unlocked) colors.textPrimary else colors.textMuted,
                                fontWeight = if (unlocked) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                a.description,
                                style = MonoLabelSmall,
                                color = colors.textMuted,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(a.tier.uppercase(), style = MonoLabelSmall, color = accent)
                            Text(
                                if (unlocked) "+${a.xpReward} XP" else "LOCKED",
                                style = MonoLabelSmall,
                                color = if (unlocked) colors.accent else colors.textMuted,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Narrative "Chapter" progress — web's chapter card over `chapterForXp`. Pure
 * client math on lifetime XP; shows the current chapter, its subtitle, and a
 * bar toward the next chapter's XP floor.
 */
@Composable
private fun ChapterCard(lifetimeXp: Long) {
    val colors = Stackd.colors
    val chapter = app.stackd.feature.insights.chapterForXp(lifetimeXp)
    val next = app.stackd.feature.insights.nextChapter(lifetimeXp)
    val index = app.stackd.feature.insights.NARRATIVE_CHAPTERS.indexOfFirst { it.title == chapter.title }
    val progress = app.stackd.feature.insights.chapterProgress(lifetimeXp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.3f), Radius2Xl)
            .padding(16.dp),
    ) {
        Text("CHAPTER ${index + 1}", style = MonoLabelSmall, color = colors.accent)
        Spacer(Modifier.height(4.dp))
        Text(
            chapter.title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(chapter.subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
        if (next != null) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(colors.textPrimary.copy(alpha = 0.05f), CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(5.dp)
                        .background(colors.accent, CircleShape),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${lifetimeXp} / ${next.minXp} XP · next: ${next.title}",
                style = MonoLabelSmall, color = colors.textMuted,
            )
        }
    }
}
