package app.stackd.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.stackd.core.stackdViewModel
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.pressFeedback
import app.stackd.feature.profile.FeatureEmptyState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.social.Friend
import app.stackd.data.social.PersonRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class FriendsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val rows: List<Friend> = emptyList(),
    val searchResults: List<PersonRef> = emptyList(),
    val searching: Boolean = false,
    /** user_ids a request was just sent to, for instant button feedback. */
    val requested: Set<String> = emptySet(),
) {
    val friends: List<Friend> get() = rows.filter { it.direction == "friend" }
    val incoming: List<Friend> get() = rows.filter { it.direction == "incoming" }
    val outgoing: List<Friend> get() = rows.filter { it.direction == "outgoing" }
}

/** Friends — web's `friends.tsx`: list, requests both ways, people search. */
class FriendsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(FriendsUiState())
    val state: StateFlow<FriendsUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "friends:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: FriendsUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching { container.friends.listFriends(userId) }.fold(
                onSuccess = {
                    val fresh = _state.value.copy(loading = false, error = false, rows = it)
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    fun search(q: String) {
        val userId = container.auth.currentUserId ?: return
        if (q.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        _state.value = _state.value.copy(searching = true)
        viewModelScope.launch {
            val rows = runCatching { container.friends.searchPeople(userId, q) }
                .getOrDefault(emptyList())
            _state.value = _state.value.copy(searching = false, searchResults = rows)
        }
    }

    fun sendRequest(addresseeId: String) {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(requested = _state.value.requested + addresseeId)
        viewModelScope.launch {
            runCatching { container.friends.sendRequest(userId, addresseeId) }
            load()
        }
    }

    fun respond(id: String, accept: Boolean) {
        val userId = container.auth.currentUserId ?: return
        _state.value = _state.value.copy(rows = _state.value.rows.filterNot { it.id == id && !accept })
        viewModelScope.launch {
            runCatching { container.friends.respond(id, userId, accept) }
            load()
        }
    }

    fun remove(id: String) {
        _state.value = _state.value.copy(rows = _state.value.rows.filterNot { it.id == id })
        viewModelScope.launch { runCatching { container.friends.remove(id) } }
    }
}

@Composable
fun FriendsRoute(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    vm: FriendsViewModel = viewModel(factory = stackdViewModel { FriendsViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    FriendsScreen(
        state = state,
        onSearch = vm::search,
        onSendRequest = vm::sendRequest,
        onRespond = vm::respond,
        onRemove = vm::remove,
        onRetry = vm::load,
        onBack = onBack,
        onOpenProfile = onOpenProfile,
        modifier = modifier,
    )
}

@Composable
fun FriendsScreen(
    state: FriendsUiState,
    onSearch: (String) -> Unit,
    onSendRequest: (String) -> Unit,
    onRespond: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
) {
    val colors = Stackd.colors
    // Hoisted out of the lazy items: state remembered inside an item is dropped
    // when the item scrolls off screen.
    var query by remember { mutableStateOf("") }
    app.stackd.core.ui.ResponsiveLazyColumn(modifier = modifier.background(colors.background)) {
        item(key = "header") {
            Column {
                app.stackd.core.ui.ScreenHeader("STACK'D / FRIENDS", onBack)
                Spacer(Modifier.height(16.dp))
                SectionLabel("YOUR PEOPLE")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it.take(60)
                        onSearch(query)
                    },
                    label = { Text("Find people by name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.searchResults.isNotEmpty()) Spacer(Modifier.height(8.dp))
            }
        }
        items(state.searchResults, key = { "s:${it.id}" }) { p ->
            PersonRow(
                name = p.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                sub = null,
                onOpen = { onOpenProfile(p.id) },
                actionA = if (p.id in state.requested) "SENT" else "ADD",
                onA = if (p.id in state.requested) null else ({ onSendRequest(p.id) }),
            )
        }
        item(key = "gap") { Spacer(Modifier.height(24.dp)) }

        when {
            state.loading -> items(5, key = { "sk:$it" }) {
                SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(72.dp), Radius2Xl)
            }
            state.error -> item(key = "error") {
                Column {
                    Text(
                        "Couldn't load friends.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
            }
            else -> {
                if (state.incoming.isNotEmpty()) {
                    item(key = "in-h") {
                        Column {
                            Text("REQUESTS", style = MonoLabelSmall, color = colors.accent)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    items(state.incoming, key = { "in:${it.id}" }) { f ->
                        PersonRow(
                            name = f.displayName ?: "Anon",
                            sub = "wants to connect",
                            onOpen = { onOpenProfile(f.userId) },
                            actionA = "ACCEPT", onA = { onRespond(f.id, true) },
                            actionB = "DECLINE", onB = { onRespond(f.id, false) },
                        )
                    }
                    item(key = "in-gap") { Spacer(Modifier.height(24.dp)) }
                }
                if (state.outgoing.isNotEmpty()) {
                    item(key = "out-h") {
                        Column {
                            Text("SENT", style = MonoLabelSmall, color = colors.textMuted)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    items(state.outgoing, key = { "out:${it.id}" }) { f ->
                        PersonRow(
                            name = f.displayName ?: "Anon",
                            sub = "pending",
                            onOpen = { onOpenProfile(f.userId) },
                            actionA = "CANCEL", onA = { onRemove(f.id) },
                        )
                    }
                    item(key = "out-gap") { Spacer(Modifier.height(24.dp)) }
                }
                item(key = "fr-h") {
                    Column {
                        Text("FRIENDS · ${state.friends.size}", style = MonoLabelSmall, color = colors.textMuted)
                        Spacer(Modifier.height(8.dp))
                        if (state.friends.isEmpty()) {
                            // Search sits right above, so no extra button.
                            FeatureEmptyState(
                                icon = Icons.Outlined.PersonAddAlt,
                                title = "No friends yet",
                                body = "Search above to send your first request.",
                            )
                        }
                    }
                }
                items(state.friends, key = { "fr:${it.id}" }) { f ->
                    PersonRow(
                        name = f.displayName ?: "Anon",
                        sub = "since ${f.since.take(10)}",
                        onOpen = { onOpenProfile(f.userId) },
                        actionA = "REMOVE", onA = { onRemove(f.id) },
                    )
                }
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
private fun PersonRow(
    name: String,
    sub: String?,
    onOpen: (() -> Unit)? = null,
    actionA: String? = null,
    onA: (() -> Unit)? = null,
    actionB: String? = null,
    onB: (() -> Unit)? = null,
) {
    val colors = Stackd.colors
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .then(if (onOpen != null) Modifier.pressFeedback(source, pressedScale = 0.98f) else Modifier)
            .background(colors.textPrimary.copy(alpha = 0.04f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .then(
                    if (onOpen != null) {
                        Modifier.clickable(
                            interactionSource = source,
                            indication = null,
                            role = Role.Button,
                        ) { onOpen() }
                    } else Modifier,
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted) }
        }
        actionA?.let { label ->
            RowAction(label, if (onA != null) colors.accent else colors.textMuted, onA)
        }
        actionB?.let { label ->
            RowAction(label, colors.textMuted, onB)
        }
    }
}

/** Compact text action with a full 48dp touch target. */
@Composable
private fun RowAction(label: String, color: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button) { onClick() } else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MonoLabelSmall, color = color)
    }
}
