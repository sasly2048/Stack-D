package app.stackd.feature.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.SkeletonCard
import app.stackd.feature.profile.FeatureEmptyState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.SearchOff
import app.stackd.data.vault.VaultItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class VaultUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    /** Null until the entitlement resolves; false shows the Elite gate. */
    val hasAccess: Boolean? = null,
    val items: List<VaultItem> = emptyList(),
    val saving: Boolean = false,
    /** Item ids with an in-flight AI summarize call — drives the per-item spinner. */
    val summarizing: Set<String> = emptySet(),
)

/** Memory Vault — web's `vault.tsx`, Elite-gated like `requireFeature("vault")`. */
class VaultViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "vault:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: VaultUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            val ent = runCatching { container.premium.myEntitlement() }.getOrNull()
            if (ent == null) {
                _state.value = _state.value.copy(loading = false, error = cached == null)
                return@launch
            }
            if (!ent.isElite && !ent.isAdmin) {
                _state.value = VaultUiState(loading = false, hasAccess = false)
                return@launch
            }
            runCatching { container.vault.listVault(userId) }.fold(
                onSuccess = {
                    val fresh = VaultUiState(loading = false, hasAccess = true, items = it)
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    fun add(title: String, body: String, url: String, tags: String) {
        val userId = container.auth.currentUserId ?: return
        if (title.isBlank() || _state.value.saving) return
        _state.value = _state.value.copy(saving = true)
        viewModelScope.launch {
            val item = runCatching {
                container.vault.createVaultItem(
                    userId = userId,
                    title = title.trim(),
                    body = body,
                    url = url,
                    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                )
            }.getOrNull()
            _state.value = _state.value.copy(
                saving = false,
                items = if (item != null) listOf(item) + _state.value.items else _state.value.items,
            )
        }
    }

    fun delete(id: String) {
        _state.value = _state.value.copy(items = _state.value.items.filterNot { it.id == id })
        viewModelScope.launch { runCatching { container.vault.deleteVaultItem(id) } }
    }

    /**
     * Generates the AI summary for one item via the public AI route (Elite-only,
     * already gated by the screen). The route writes ai_summary back server-side;
     * we also patch it into local state so the ✦ line appears without a reload.
     * A failed/unreachable call just clears the spinner — the Summarize button
     * stays, so the user can retry.
     */
    fun summarize(id: String) {
        if (id in _state.value.summarizing) return
        _state.value = _state.value.copy(summarizing = _state.value.summarizing + id)
        viewModelScope.launch {
            val summary = container.ai.summarizeVaultItem(id)?.summary
            _state.value = _state.value.copy(
                summarizing = _state.value.summarizing - id,
                items = if (summary.isNullOrBlank()) _state.value.items else {
                    _state.value.items.map {
                        if (it.id == id) it.copy(aiSummary = summary) else it
                    }
                },
            )
        }
    }
}

@Composable
fun VaultRoute(
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    vm: VaultViewModel = viewModel(factory = stackdViewModel { VaultViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    VaultScreen(
        state = state,
        onAdd = vm::add,
        onDelete = vm::delete,
        onSummarize = vm::summarize,
        onRetry = vm::load,
        onBack = onBack,
        onUpgrade = onUpgrade,
        modifier = modifier,
    )
}

@Composable
fun VaultScreen(
    state: VaultUiState,
    onAdd: (title: String, body: String, url: String, tags: String) -> Unit,
    onDelete: (String) -> Unit,
    onSummarize: (String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    // All form/search/dialog state lives here, not inside lazy items — an item
    // that scrolls off screen would otherwise drop a half-typed entry.
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    // Web searches title/notes/summary server-side with ilike; Android already
    // holds the full page (limit 200), so the same match runs in memory.
    var query by remember { mutableStateOf("") }
    // Guard the irreversible delete behind a confirm, mirroring the web's
    // window.confirm. Holds the item id awaiting confirmation; null = no dialog.
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this vault item?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
    val q = query.trim().lowercase()
    val shown = remember(state.items, q) {
        if (q.isEmpty()) state.items else state.items.filter {
            it.title.lowercase().contains(q) ||
                it.body?.lowercase()?.contains(q) == true ||
                it.aiSummary?.lowercase()?.contains(q) == true
        }
    }

    app.stackd.core.ui.ResponsiveLazyColumn(modifier = modifier.background(colors.background)) {
        item(key = "header") {
            Column {
                app.stackd.core.ui.ScreenHeader("STACK'D / VAULT", onBack)
                Spacer(Modifier.height(16.dp))
                SectionLabel("MEMORY VAULT")
                Spacer(Modifier.height(16.dp))
            }
        }

        when {
            state.loading -> item(key = "loading") {
                Column {
                    SkeletonBlock(Modifier.fillMaxWidth().height(52.dp))
                    Spacer(Modifier.height(16.dp))
                    repeat(4) { SkeletonCard(height = 112.dp) }
                }
            }
            state.error -> item(key = "error") {
                Column {
                    Text(
                        "Couldn't open the vault.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
            }
            state.hasAccess == false -> item(key = "gate") {
                EliteGate(
                    "Keep what mattered from every deep-work session — notes, links and artifacts, forever searchable.",
                    onUpgrade,
                )
            }
            else -> {
                item(key = "form") {
                  Column {
                    GhostButton(
                        text = if (showForm) "Cancel" else "New entry",
                        onClick = { showForm = !showForm },
                    )
                    if (showForm) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = title, onValueChange = { if (it.length <= 200) title = it },
                            label = { Text("Title") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = body, onValueChange = { body = it },
                            label = { Text("Notes") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url, onValueChange = { url = it },
                            label = { Text("Link (optional)") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = tags, onValueChange = { tags = it },
                            label = { Text("Tags, comma-separated") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        EmberButton(
                            text = if (state.saving) "Saving…" else "Store it",
                            onClick = {
                                onAdd(title, body, url, tags)
                                showForm = false
                                title = ""; body = ""; url = ""; tags = ""
                            },
                            enabled = title.isNotBlank(),
                            busy = state.saving,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    if (state.items.isNotEmpty()) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search title, notes, summary") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (state.items.isEmpty() && !showForm) {
                        FeatureEmptyState(
                            icon = Icons.Outlined.Inventory2,
                            title = "Your vault is empty",
                            body = "Store notes, links and artifacts from a session so they're never lost.",
                        )
                    } else if (state.items.isNotEmpty() && shown.isEmpty()) {
                        FeatureEmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "No matches",
                            body = "Nothing matches “$query”. Try a different word.",
                        )
                    }
                  }
                }
                items(shown, key = { it.id }) { item ->
                    VaultItemCard(
                        item = item,
                        summarizing = item.id in state.summarizing,
                        onDelete = { pendingDelete = item.id },
                        onSummarize = { onSummarize(item.id) },
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

/** One stored entry. Actions are real 48dp buttons with semantics, not tiny text taps. */
@Composable
private fun VaultItemCard(
    item: VaultItem,
    summarizing: Boolean,
    onDelete: () -> Unit,
    onSummarize: () -> Unit,
) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDelete) {
                Text("Delete", style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            }
        }
        Column(Modifier.padding(end = 10.dp)) {
            item.body?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted, maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    item.tags.joinToString("  ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium, color = colors.accent,
                )
            }
        }
        // AI summary — web parity: the ✦ line when it exists, otherwise a
        // Summarize action that calls the Elite-gated route and writes it back.
        val summary = item.aiSummary?.takeIf { it.isNotBlank() }
        when {
            summary != null -> Text(
                "✦ $summary",
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp, end = 10.dp),
            )
            summarizing -> Text(
                "Summarizing…",
                style = MaterialTheme.typography.labelMedium, color = colors.textMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> TextButton(
                onClick = onSummarize,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
            ) {
                Text("✦ Summarize", style = MaterialTheme.typography.labelMedium, color = colors.accent)
            }
        }
    }
}

/** Shared Elite upsell block, mirroring the web's <PremiumGate>. */
@Composable
internal fun EliteGate(description: String, onUpgrade: () -> Unit) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accent.copy(alpha = 0.04f), Radius2Xl)
            .border(1.dp, colors.accent.copy(alpha = 0.3f), Radius2Xl)
            .padding(20.dp),
    ) {
        Text("ELITE FEATURE", style = MonoLabelSmall, color = colors.accent)
        Spacer(Modifier.height(8.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        Spacer(Modifier.height(16.dp))
        EmberButton(text = "See plans", onClick = onUpgrade)
    }
}
