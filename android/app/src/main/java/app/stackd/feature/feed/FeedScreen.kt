package app.stackd.feature.feed

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
                        loading = false, rows = rows, circle = circle, nowMillis = now,
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
) {
    val colors = Stackd.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        ResponsiveColumn {
            Text("STACK'D / SIGNAL", style = MonoLabel, color = colors.textMuted)
            Spacer(Modifier.height(16.dp))
            SectionLabel("FEED")
            Spacer(Modifier.height(16.dp))

            when {
                state.loading -> Text(
                    "Loading your feed…",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                state.error -> {
                    Text(
                        "Couldn't load your feed.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    CirclePanel(state.circle, onOpenFriends)
                    Spacer(Modifier.height(20.dp))

                    if (state.rows.isEmpty()) {
                        // An empty state that only names the problem is a dead
                        // end — both ways out of it are one tap away.
                        Text(
                            "No signal yet. Complete a session or add friends.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                        )
                        Spacer(Modifier.height(12.dp))
                        EmberButton(text = "Start a session", onClick = onStart)
                        Spacer(Modifier.height(8.dp))
                        GhostButton(text = "Find friends", onClick = onOpenFriends)
                    }
                    state.rows.forEach { FeedRow(it, state.nowMillis) }
                }
            }

            Spacer(Modifier.height(24.dp))
            GhostButton(text = "Back", onClick = onBack)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CirclePanel(circle: List<FriendPresence>, onOpenFriends: () -> Unit) {
    val colors = Stackd.colors
    Text(
        if (circle.isEmpty()) "CIRCLE" else "CIRCLE · ${circle.size}",
        style = MonoLabelSmall, color = colors.textMuted,
    )
    Spacer(Modifier.height(6.dp))
    if (circle.isEmpty()) {
        Text(
            "No ties yet.",
            style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        GhostButton(text = "Find someone", onClick = onOpenFriends)
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(vertical = 4.dp),
    ) {
        circle.forEach { f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            when (f.status) {
                                PresenceStatus.FOCUSING -> colors.accent
                                PresenceStatus.IDLE -> colors.textMuted
                                PresenceStatus.OFFLINE -> colors.textPrimary.copy(alpha = 0.15f)
                            },
                            CircleShape,
                        ),
                )
                Text(
                    f.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    f.status.name.lowercase(),
                    style = MonoLabelSmall,
                    color = if (f.status == PresenceStatus.FOCUSING) colors.accent else colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun FeedRow(item: FeedItem, now: Long) {
    val colors = Stackd.colors
    val name = item.displayName?.takeIf { it.isNotBlank() } ?: "Anonymous"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        app.stackd.core.ui.Avatar(url = item.avatarUrl, name = name, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(item.line, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            Spacer(Modifier.height(2.dp))
            Text(feedTimeAgo(item.createdAt, now), style = MonoLabelSmall, color = colors.textMuted)
        }
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
