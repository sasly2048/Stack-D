package app.stackd.feature.partners

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.Avatar
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.SkeletonBlock
import app.stackd.feature.profile.FeatureEmptyState
import app.stackd.data.social.Partner
import app.stackd.data.social.PersonRef
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PartnersUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val partners: List<Partner> = emptyList(),
    val query: String = "",
    val results: List<PersonRef> = emptyList(),
    val busy: Boolean = false,
    val notice: String? = null,
)

/** Accountability partners — web's `partners.tsx` over `mentor.functions`. */
class PartnersViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(PartnersUiState())
    val state: StateFlow<PartnersUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "partners:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: PartnersUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching { container.partners.listPartners(userId) }.fold(
                onSuccess = {
                    val fresh = _state.value.copy(loading = false, error = false, partners = it)
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    fun onQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }

    /** Debounced search, matching the web's 200ms delay on the same input. */
    suspend fun runSearch(q: String) {
        val userId = container.auth.currentUserId ?: return
        if (q.isBlank()) {
            _state.value = _state.value.copy(results = emptyList())
            return
        }
        delay(200)
        if (_state.value.query != q) return // superseded by a newer keystroke
        val results = runCatching { container.friends.searchPeople(userId, q) }.getOrDefault(emptyList())
        if (_state.value.query == q) _state.value = _state.value.copy(results = results)
    }

    fun invite(partnerId: String, asRole: String) = act {
        container.partners.pairPartner(it, partnerId, asRole)
        _state.value = _state.value.copy(query = "", results = emptyList(), notice = "Invitation sent.")
    }

    fun respond(relationshipId: String, accept: Boolean) = act {
        container.partners.respondToPairing(relationshipId, accept)
        _state.value = _state.value.copy(notice = if (accept) "Partnership active." else "Invitation declined.")
    }

    fun end(relationshipId: String) = act { container.partners.endPartnership(relationshipId) }

    private fun act(block: suspend (String) -> Unit) {
        val userId = container.auth.currentUserId ?: return
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true, notice = null)
        viewModelScope.launch {
            runCatching { block(userId) }
                .onFailure { _state.value = _state.value.copy(notice = "That didn't work. Try again.") }
            _state.value = _state.value.copy(busy = false)
            load()
        }
    }
}

@Composable
fun PartnersRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PartnersViewModel = viewModel(factory = stackdViewModel { PartnersViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Debounced live search: re-runs whenever the query text changes.
    LaunchedEffect(state.query) { vm.runSearch(state.query) }
    PartnersScreen(
        state = state,
        onQuery = vm::onQuery,
        onInvite = vm::invite,
        onRespond = vm::respond,
        onEnd = vm::end,
        onRetry = vm::load,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PartnersScreen(
    state: PartnersUiState,
    onQuery: (String) -> Unit,
    onInvite: (String, String) -> Unit,
    onRespond: (String, Boolean) -> Unit,
    onEnd: (String) -> Unit,
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
            app.stackd.core.ui.ScreenHeader("STACK'D / ACCOUNTABILITY", onBack, title = "Partners")
            Spacer(Modifier.height(16.dp))
            SectionLabel("PARTNERS")
            Spacer(Modifier.height(8.dp))
            Text(
                "Pair with a mentor or mentee. Keep each other honest.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                label = { Text("Find someone…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.results.forEach { p ->
                // Name above, actions below: GhostButton fills its width, so two
                // of them beside a weighted name squeezed the second to nothing.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(colors.textPrimary.copy(alpha = 0.04f), Radius2Xl)
                        .border(1.dp, colors.border, Radius2Xl)
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val name = p.displayName?.takeIf { it.isNotBlank() } ?: "Anon"
                        Avatar(url = p.avatarUrl, name = name, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            text = "Mentor", onClick = { onInvite(p.id, "mentor") },
                            enabled = !state.busy, modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = "Mentee", onClick = { onInvite(p.id, "mentee") },
                            enabled = !state.busy, modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            state.notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accent)
            }

            Spacer(Modifier.height(24.dp))
            Text("ACTIVE", style = MonoLabelSmall, color = colors.textMuted)
            Spacer(Modifier.height(8.dp))
            when {
                state.loading -> repeat(2) {
                    SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(112.dp), Radius2Xl)
                }
                state.error -> {
                    Text(
                        "Couldn't load your partners.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                state.partners.isEmpty() -> {
                    // Invites need a search result, so the empty-state action shares a link instead.
                    val context = androidx.compose.ui.platform.LocalContext.current
                    FeatureEmptyState(
                        icon = app.stackd.core.ui.StackdIcons.Handshake,
                        title = "No partners yet",
                        body = "One person who checks you show up. Search above, or invite them.",
                        actionText = "Invite a partner",
                        onAction = { app.stackd.feature.profile.shareStackdInvite(context) },
                    )
                }
                else -> state.partners.forEach { p ->
                    PartnerRow(p, busy = state.busy, onRespond = onRespond, onEnd = onEnd)
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun PartnerRow(
    p: Partner,
    busy: Boolean,
    onRespond: (String, Boolean) -> Unit,
    onEnd: (String) -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.textPrimary.copy(alpha = 0.04f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val name = p.displayName?.takeIf { it.isNotBlank() } ?: "Anon"
            Avatar(url = p.avatarUrl, name = name, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${p.role} · ${p.status}",
                    style = MonoLabelSmall, color = colors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (p.incoming) {
                GhostButton(
                    text = "Accept",
                    onClick = { onRespond(p.relationshipId, true) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                GhostButton(
                    text = "Decline",
                    onClick = { onRespond(p.relationshipId, false) },
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
            GhostButton(
                text = "End",
                onClick = { onEnd(p.relationshipId) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
