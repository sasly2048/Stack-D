package app.stackd.feature.leaderboard

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.SerifFamily
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.Avatar
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.pressFeedback
import app.stackd.feature.profile.FeatureEmptyState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import app.stackd.data.social.LeaderboardGroup
import app.stackd.data.social.LeaderboardProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LeaderboardUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val individuals: List<LeaderboardProfile> = emptyList(),
    val groups: List<LeaderboardGroup> = emptyList(),
    val meId: String? = null,
    /** This week's league group; empty until the first stack of the week. */
    val league: List<app.stackd.data.social.LeagueRow> = emptyList(),
)

class LeaderboardViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(LeaderboardUiState())
    val state: StateFlow<LeaderboardUiState> = _state

    init {
        load()
    }

    // A getter, not a stored val: init { load() } above runs before stored
    // properties below it are initialized, so a stored key read null and crashed.
    private val cacheKey: String get() = "leaderboard"

    fun load() {
        // Rankings tolerate seconds of staleness — show the last set instantly,
        // then revalidate. No spinner on re-entry.
        val cached: LeaderboardUiState? = container.cache.get(cacheKey)
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching {
                container.leaderboard.topIndividuals() to container.leaderboard.topGroups()
            }.fold(
                onSuccess = { (people, groups) ->
                    // The league board is its own read: a failure there shouldn't
                    // blank the all-time board.
                    val league = runCatching { container.leaderboard.myLeague() }.getOrDefault(emptyList())
                    val fresh = LeaderboardUiState(
                        loading = false,
                        individuals = people,
                        groups = groups,
                        meId = container.auth.currentUserId,
                        league = league,
                    )
                    _state.value = fresh
                    container.cache.put(cacheKey, fresh)
                },
                onFailure = {
                    _state.value = _state.value.copy(loading = false, error = cached == null)
                },
            )
        }
    }
}

/**
 * XP rankings — web's `leaderboard.tsx`: individual and group tabs, top 100
 * each, with the caller's own row highlighted.
 */
@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    vm: LeaderboardViewModel = viewModel(factory = stackdViewModel { LeaderboardViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LeaderboardScreen(state = state, onRetry = vm::load, onBack = onBack, onOpenProfile = onOpenProfile, modifier = modifier)
}

@Composable
fun LeaderboardScreen(
    state: LeaderboardUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
) {
    val colors = Stackd.colors
    // This week first: a small group you can actually climb beats an all-time
    // list nobody closes the gap on.
    var tab by remember { mutableStateOf("week") }
    app.stackd.core.ui.ResponsiveLazyColumn(modifier = modifier.background(colors.background)) {
        item(key = "header") {
            Column {
                app.stackd.core.ui.ScreenHeader("STACK'D / LEADERBOARD", onBack, title = "Leaderboard")
                Spacer(Modifier.height(16.dp))
                SectionLabel("THE STANDINGS")
                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("week" to "THIS WEEK", "individual" to "ALL TIME", "groups" to "GROUPS").forEach { (key, label) ->
                        val selected = tab == key
                        val source = remember { MutableInteractionSource() }
                        Text(
                            label,
                            style = MonoLabelSmall,
                            color = if (selected) colors.accent else colors.textMuted,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .pressFeedback(source)
                                .background(if (selected) colors.accent.copy(alpha = 0.08f) else Color.Transparent, RadiusMd)
                                .border(1.dp, if (selected) colors.accent else colors.border, RadiusMd)
                                .selectable(
                                    selected = selected,
                                    interactionSource = source,
                                    indication = null,
                                    role = androidx.compose.ui.semantics.Role.Tab,
                                ) { tab = key }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                val myRank = state.individuals.indexOfFirst { it.id == state.meId }
                if (tab == "individual" && !state.loading && myRank >= 0) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "#${myRank + 1}",
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = SerifFamily,
                            fontWeight = FontWeight.Normal,
                            color = colors.textPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "YOUR RANK · OF ${state.individuals.size}",
                            style = MonoLabelSmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        when {
            state.loading -> items(8, key = { "sk:$it" }) {
                SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(60.dp), Radius2Xl)
            }
            state.error -> item(key = "error") {
                Column {
                    Text(
                        "Couldn't load the board.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
            }
            tab == "week" && state.league.isEmpty() -> item(key = "empty-w") {
                FeatureEmptyState(
                    icon = app.stackd.core.ui.StackdIcons.Leaderboard,
                    title = "Your league opens with a stack",
                    body = "Finish a stack this week to join a league of up to 30. Each Monday the top fifth moves up a tier.",
                )
            }
            tab == "week" -> {
                item(key = "league-head") { LeagueHeader(state.league) }
                itemsIndexed(state.league, key = { _, r -> "l:${r.userId}" }) { i, r ->
                    val prev = state.league.getOrNull(i - 1)?.zone
                    if (r.zone != prev && r.zone != "safe") ZoneLabel(r.zone)
                    if (prev == "demote" && r.zone != "demote" || (prev == "promote" && r.zone == "safe")) Spacer(Modifier.height(8.dp))
                    BoardRow(
                        rank = r.rank,
                        title = r.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                        subtitle = r.username?.let { "@$it" },
                        xp = r.weeklyXp,
                        isMe = r.isMe,
                        onClick = { onOpenProfile(r.userId) },
                        person = true,
                        avatarUrl = r.avatarUrl,
                        zone = r.zone,
                    )
                }
            }
            tab == "individual" && state.individuals.isEmpty() -> item(key = "empty-p") {
                FeatureEmptyState(
                    icon = app.stackd.core.ui.StackdIcons.Leaderboard,
                    title = "The board is empty",
                    body = "Hold a session to earn XP and claim the first spot.",
                )
            }
            tab == "groups" && state.groups.isEmpty() -> item(key = "empty-g") {
                FeatureEmptyState(
                    icon = app.stackd.core.ui.StackdIcons.Groups,
                    title = "No groups ranked yet",
                    body = "Groups appear here once their members start earning XP.",
                )
            }
            tab == "individual" -> itemsIndexed(state.individuals, key = { _, p -> "p:${p.id}" }) { i, p ->
                val name = p.displayName?.takeIf { it.isNotBlank() } ?: "Anon"
                // Same display name twice is ambiguous: tell them apart by handle.
                val dup = state.individuals.count { it.displayName == p.displayName } > 1
                BoardRow(
                    rank = i + 1,
                    title = name,
                    subtitle = listOfNotNull(
                        p.username?.takeIf { dup }?.let { "@$it" },
                        p.currentFocusStreak.takeIf { it > 0 }?.let { "$it-day streak" },
                    ).joinToString(" · ").ifEmpty { null },
                    xp = p.lifetimeXp,
                    isMe = p.id == state.meId,
                    onClick = { onOpenProfile(p.id) },
                    person = true,
                    avatarUrl = p.avatarUrl,
                    sharedKey = "avatar-${p.id}",
                )
            }
            else -> itemsIndexed(state.groups, key = { _, g -> "g:${g.id}" }) { i, g ->
                BoardRow(
                    rank = i + 1,
                    title = g.name,
                    subtitle = if (g.memberCount == 1) "1 member" else "${g.memberCount} members",
                    xp = g.totalGroupXp,
                    isMe = false,
                )
            }
        }

        item(key = "footer") {
            Column {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BoardRow(
    rank: Int,
    title: String,
    subtitle: String?,
    xp: Long,
    isMe: Boolean,
    onClick: (() -> Unit)? = null,
    person: Boolean = false,
    avatarUrl: String? = null,
    sharedKey: String? = null,
    /** League zone: a thin stripe marks promotion (ember) and demotion (breach). */
    zone: String? = null,
) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val stripe = when (zone) { "promote" -> colors.accent; "demote" -> colors.breach; else -> null }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onClick != null) Modifier.pressFeedback(source) else Modifier)
            .background(
                if (isMe) {
                    Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.09f), colors.surface))
                } else {
                    SolidColor(colors.textPrimary.copy(alpha = 0.04f))
                },
                Radius2Xl,
            )
            .border(1.dp, if (isMe) colors.accent.copy(alpha = 0.18f) else colors.border, Radius2Xl)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        role = androidx.compose.ui.semantics.Role.Button,
                    ) { onClick() }
                } else {
                    Modifier
                },
            )
            .heightIn(min = 48.dp)
            .then(
                if (stripe != null) {
                    Modifier.androidxDrawStripe(stripe)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#$rank",
            style = MonoLabelSmall,
            color = if (rank <= 3) colors.accent else colors.textMuted,
            modifier = Modifier.width(44.dp),
        )
        if (person) {
            Avatar(url = avatarUrl, name = title, size = 32.dp, sharedKey = sharedKey)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let { Text(it, style = MonoLabelSmall, color = colors.textMuted) }
        }
        if (rank == 1) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$xp",
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Normal,
                    color = colors.textPrimary,
                )
                Text(" XP", style = MonoLabelSmall, color = colors.textMuted, modifier = Modifier.padding(bottom = 4.dp))
            }
        } else {
            Text(
                "$xp XP",
                style = MonoLabelSmall,
                color = colors.textPrimary,
            )
        }
    }
}

private val TierNames = listOf("Bronze", "Silver", "Gold", "Ember", "Obsidian")

/** Tier, time left, and the one number that matters: the gap to the next rank. */
@Composable
private fun LeagueHeader(rows: List<app.stackd.data.social.LeagueRow>) {
    val colors = Stackd.colors
    val me = rows.firstOrNull { it.isMe } ?: return
    val tier = TierNames.getOrElse(me.tier - 1) { "Bronze" }
    val daysLeft = me.weekEndsAt?.let {
        runCatching {
            val end = java.time.OffsetDateTime.parse(it).toInstant()
            kotlin.math.ceil(java.time.Duration.between(java.time.Instant.now(), end).toHours() / 24.0).toInt().coerceAtLeast(0)
        }.getOrNull()
    }
    val above = rows.firstOrNull { it.rank == me.rank - 1 }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "#${me.rank}",
                style = MaterialTheme.typography.displayMedium,
                fontFamily = SerifFamily,
                fontWeight = FontWeight.Normal,
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${tier.uppercase()} LEAGUE · ${daysLeft?.let { if (it == 1) "1 DAY LEFT" else "$it DAYS LEFT" } ?: "THIS WEEK"}",
                style = MonoLabelSmall,
                color = colors.textMuted,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                above != null -> "${above.weeklyXp - me.weeklyXp + 1} XP to pass ${above.displayName?.substringBefore(' ') ?: "the next stacker"}"
                rows.size == 1 -> "You're the first here this week. Others join as they stack."
                else -> "You're leading the league this week."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.accent,
        )
    }
}

@Composable
private fun ZoneLabel(zone: String) {
    val colors = Stackd.colors
    Text(
        if (zone == "promote") "PROMOTION ZONE" else "DEMOTION ZONE",
        style = MonoLabelSmall,
        color = if (zone == "promote") colors.accent else colors.breach,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/** A 3dp vertical stripe on the row's leading edge. */
private fun Modifier.androidxDrawStripe(color: Color): Modifier = this.then(
    Modifier.drawBehind {
        drawRect(color, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
    },
)