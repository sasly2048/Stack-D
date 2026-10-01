package app.stackd.feature.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.AppContainer
import app.stackd.core.parseIsoMillis
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.SkeletonCard
import app.stackd.core.ui.pressFeedback
import app.stackd.feature.profile.FeatureEmptyState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import app.stackd.data.social.FeedItem
import app.stackd.data.social.FriendPresence
import app.stackd.data.social.PresenceStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FeedUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<FeedItem> = emptyList(),
    val circle: List<FriendPresence> = emptyList(),
    val nowMillis: Long = System.currentTimeMillis(),
    val meId: String? = null,
)

/**
 * Activity feed — web's `feed.tsx`.
 *
 * Two loops, matching the web's split: a 30s data refresh and a separate 60s
 * presence heartbeat. Both live in [viewModelScope], so leaving the screen
 * cancels them; the web has to lean on `refetchIntervalInBackground: false`
 * to get the same thing.
 */
class FeedViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state

    private val loops = mutableListOf<Job>()

    init {
        load()
    }

    /** Starts/stops the refresh + heartbeat pair with screen visibility. */
    fun setActive(active: Boolean) {
        loops.forEach { it.cancel() }
        loops.clear()
        if (!active) return
        loops += viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                load(silent = true)
            }
        }
        loops += viewModelScope.launch {
            while (isActive) {
                // Fire-and-forget: a dropped beat is not worth a visible error.
                runCatching { container.feed.heartbeat() }
                delay(60_000)
            }
        }
    }

    private fun cacheKey(userId: String) = "feed:$userId"

    fun load(silent: Boolean = false) {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate on the visible (non-silent) load only — seed
        // from the last cached state so re-entry shows data instantly instead of
        // a spinner. The 30s poll revalidates silently and must not reseed.
        val cached: FeedUiState? = container.cache.get(cacheKey(userId))
        if (!silent) _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                container.feed.listFeed(userId) to container.feed.friendsPresence(userId, now)
            }.fold(
                onSuccess = { (rows, circle) ->
                    val fresh = FeedUiState(
                        loading = false, circle = circle, nowMillis = now, meId = userId,
                        // Daily-reward claims are personal bookkeeping, not news.
                        rows = rows.filterNot { it.kind == "daily_reward" },
                    )
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = {
                    // A failed background refresh must not blank a feed that is
                    // already on screen — only the first load can show the error.
                    if (!silent) _state.value = _state.value.copy(loading = false, error = cached == null)
                },
            )
        }
    }
}

@Composable
fun FeedRoute(
    onBack: () -> Unit,
    onStart: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    vm: FeedViewModel = viewModel(factory = stackdViewModel { FeedViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    DisposableEffect(vm) {
        vm.setActive(true)
        onDispose { vm.setActive(false) }
    }
    FeedScreen(
        state = state,
        onRetry = { vm.load() },
        onBack = onBack,
        onStart = onStart,
        onOpenFriends = onOpenFriends,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}

@Composable
fun FeedScreen(
    state: FeedUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            app.stackd.core.ui.ScreenHeader("STACK'D / CIRCLE", onBack, title = "Social")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        repeat(4) { SkeletonBlock(Modifier.size(60.dp), CircleShape) }
                    }
                    Spacer(Modifier.height(32.dp))
                    repeat(4) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SkeletonBlock(Modifier.size(40.dp), CircleShape)
                            Column(Modifier.weight(1f)) {
                                SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(14.dp))
                                Spacer(Modifier.height(6.dp))
                                SkeletonBlock(Modifier.fillMaxWidth(0.85f).height(12.dp))
                            }
                        }
                    }
                }
                state.error -> {
                    Text(
                        "Couldn't load your circle.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    CircleStrip(state.circle, onOpenFriends, onOpenProfile)
                    Spacer(Modifier.height(32.dp))

                    if (state.rows.isEmpty()) {
                        // An empty state that only names the problem is a dead
                        // end — both ways out of it are one tap away.
                        FeatureEmptyState(
                            icon = app.stackd.core.ui.StackdIcons.Sensors,
                            title = "Quiet so far",
                            body = "Finished sessions from you and your circle show up here.",
                        )
                        EmberButton(text = "Start a session", onClick = onStart)
                    } else {
                        Activity(state.rows, state.nowMillis, state.meId, onOpenProfile)
                    }
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

/**
 * Who's around, as a stories-style strip: faces first, focusing friends lead
 * with a breathing ember ring, and adding someone is always the last bubble.
 * Presence is the social proof that makes starting a session feel shared.
 */
@Composable
private fun CircleStrip(circle: List<FriendPresence>, onOpenFriends: () -> Unit, onOpenProfile: (String) -> Unit) {
    val colors = Stackd.colors
    val focusing = circle.count { it.status == PresenceStatus.FOCUSING }
    Text(
        when {
            circle.isEmpty() -> "Your circle"
            focusing == 1 -> "1 friend focusing now"
            focusing > 1 -> "$focusing friends focusing now"
            else -> "Your circle · ${circle.size}"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (focusing > 0) colors.accent else colors.textPrimary,
    )
    if (circle.isEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(
            "Add a friend to see when they're focusing — and stack together.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
        )
    }
    Spacer(Modifier.height(14.dp))
    val sorted = remember(circle) { circle.sortedBy { it.status.ordinal } }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        sorted.forEach { f ->
            Bubble(
                label = f.displayName?.takeIf { it.isNotBlank() }?.substringBefore(' ') ?: "Anon",
                status = f.status,
                onClick = { onOpenProfile(f.id) },
            ) { app.stackd.core.ui.Avatar(url = f.avatarUrl, name = f.displayName, size = 56.dp) }
        }
        Bubble(label = "Add", status = null, onClick = onOpenFriends) {
            Box(
                Modifier
                    .size(56.dp)
                    .border(1.dp, colors.textPrimary.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    app.stackd.core.ui.StackdIcons.Add,
                    contentDescription = "Add friends",
                    tint = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun Bubble(
    label: String,
    status: PresenceStatus?,
    onClick: () -> Unit,
    face: @Composable () -> Unit,
) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    val pulse = if (status == PresenceStatus.FOCUSING) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "ring")
        t.animateFloat(
            0.45f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(1400),
                androidx.compose.animation.core.RepeatMode.Reverse,
            ),
            label = "ringAlpha",
        ).value
    } else {
        1f
    }
    val ring = when (status) {
        PresenceStatus.FOCUSING -> colors.accent.copy(alpha = pulse)
        PresenceStatus.IDLE -> colors.textPrimary.copy(alpha = 0.25f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    Column(
        Modifier
            .width(64.dp)
            .pressFeedback(source)
            .clickable(source, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.border(2.dp, ring, CircleShape).padding(4.dp),
            contentAlignment = Alignment.Center,
        ) { face() }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (status == PresenceStatus.FOCUSING) colors.textPrimary else colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Activity as a calm list grouped by day — dividers, not a stack of boxes. */
@Composable
private fun Activity(rows: List<FeedItem>, now: Long, meId: String?, onOpenProfile: (String) -> Unit) {
    val colors = Stackd.colors
    val zone = remember { java.time.ZoneId.systemDefault() }
    val groups = remember(rows, now) {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        rows.groupBy { item ->
            val d = parseIsoMillis(item.createdAt)?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            when (d) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> "Earlier"
            }
        }
    }
    groups.forEach { (day, items) ->
        Text(day.uppercase(), style = MonoLabelSmall, color = colors.textMuted)
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { i, item ->
            FeedRow(item, now, item.userId == meId, onOpenProfile)
            if (i < items.lastIndex) app.stackd.core.ui.HairlineDivider()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FeedRow(item: FeedItem, now: Long, mine: Boolean, onOpenProfile: (String) -> Unit) {
    val colors = Stackd.colors
    val fullName = item.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"
    val name = if (mine) "You" else fullName
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressFeedback(source)
            .clickable(
                interactionSource = source,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
            ) { onOpenProfile(item.userId) }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.stackd.core.ui.Avatar(url = item.avatarUrl, name = fullName, size = 40.dp)
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = colors.textPrimary, fontWeight = FontWeight.SemiBold))
                append(name)
                pop()
                append(" ")
                append(item.line)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(feedTimeAgo(item.createdAt, now), style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
    }
}

/** The feed's own wording — "just now", then Nm/Nh/Nd ago, matching the web. */
internal fun feedTimeAgo(iso: String, now: Long): String {
    val at = parseIsoMillis(iso) ?: return ""
    val s = ((now - at) / 1000).coerceAtLeast(0)
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        else -> "${s / 86_400}d ago"
    }
}
