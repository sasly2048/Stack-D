package app.stackd.feature.webhooks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
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
import app.stackd.core.ui.ResponsiveLazyColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.data.webhooks.WEBHOOK_EVENTS
import app.stackd.data.webhooks.Webhook
import app.stackd.data.webhooks.WebhookDelivery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WebhooksUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val hooks: List<Webhook> = emptyList(),
    val creating: Boolean = false,
    /** One-shot line under the form or a card: created, test result, failure. */
    val notice: String? = null,
    /** Webhook whose delivery log is open, and that log. */
    val expandedId: String? = null,
    val deliveries: List<WebhookDelivery> = emptyList(),
    /** Id with an in-flight toggle/test/delete, to disable its buttons. */
    val busyId: String? = null,
)

/** Webhooks — web's `webhooks.tsx`, via the public webhook routes. */
class WebhooksViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.webhooks
    private val _state = MutableStateFlow(WebhooksUiState())
    val state: StateFlow<WebhooksUiState> = _state

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = it.hooks.isEmpty(), error = null) }
        viewModelScope.launch {
            repo.list().fold(
                onSuccess = { hooks -> _state.update { it.copy(loading = false, hooks = hooks) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.message) } },
            )
        }
    }

    fun create(url: String, events: List<String>, onCreated: () -> Unit) {
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, notice = null) }
        viewModelScope.launch {
            repo.create(url, events).fold(
                onSuccess = { hook ->
                    _state.update {
                        it.copy(creating = false, hooks = listOf(hook) + it.hooks, notice = "Webhook created.")
                    }
                    onCreated()
                },
                onFailure = { e -> _state.update { it.copy(creating = false, notice = e.message) } },
            )
        }
    }

    fun toggle(hook: Webhook) = busy(hook.id) {
        val next = !hook.active
        repo.toggle(hook.id, next).onSuccess {
            _state.update { s -> s.copy(hooks = s.hooks.map { if (it.id == hook.id) it.copy(active = next) else it }) }
        }.onFailure { e -> _state.update { it.copy(notice = e.message) } }
    }

    fun delete(id: String) = busy(id) {
        repo.delete(id).onSuccess {
            _state.update { s -> s.copy(hooks = s.hooks.filterNot { it.id == id }, expandedId = null) }
        }.onFailure { e -> _state.update { it.copy(notice = e.message) } }
    }

    fun test(id: String) = busy(id) {
        repo.test(id).fold(
            onSuccess = { d ->
                val line = if (d.ok) "Test delivered — HTTP ${d.statusCode}." else
                    "Test failed — ${d.statusCode?.let { "HTTP $it" } ?: d.responseSnippet ?: "no response"}."
                _state.update { it.copy(notice = line) }
                if (_state.value.expandedId == id) loadDeliveries(id)
            },
            onFailure = { e -> _state.update { it.copy(notice = e.message) } },
        )
    }

    fun toggleDeliveries(id: String) {
        if (_state.value.expandedId == id) {
            _state.update { it.copy(expandedId = null, deliveries = emptyList()) }
        } else {
            _state.update { it.copy(expandedId = id, deliveries = emptyList()) }
            viewModelScope.launch { loadDeliveries(id) }
        }
    }

    private suspend fun loadDeliveries(id: String) {
        repo.deliveries(id).onSuccess { list ->
            if (_state.value.expandedId == id) _state.update { it.copy(deliveries = list) }
        }
    }

    private fun busy(id: String, block: suspend () -> Unit) {
        if (_state.value.busyId != null) return
        _state.update { it.copy(busyId = id, notice = null) }
        viewModelScope.launch {
            try { block() } finally { _state.update { it.copy(busyId = null) } }
        }
    }
}

@Composable
fun WebhooksRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: WebhooksViewModel = viewModel(factory = stackdViewModel { WebhooksViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = Stackd.colors
    var url by remember { mutableStateOf("") }
    var events by remember { mutableStateOf(setOf("session.complete")) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this webhook?") },
            text = { Text("Deliveries to this endpoint stop immediately. This can't be undone.") },
            confirmButton = { TextButton(onClick = { vm.delete(id); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    ResponsiveLazyColumn(modifier = modifier.background(colors.background)) {
        item(key = "header") {
            Column {
                app.stackd.core.ui.ScreenHeader("STACK'D / WEBHOOKS", onBack)
                Spacer(Modifier.height(16.dp))
                SectionLabel("WEBHOOKS")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Push session events to your own endpoint. Every request is signed " +
                        "with HMAC-SHA256 in the X-Stackd-Signature header.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(500) },
                    label = { Text("Endpoint URL (https://…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("EVENTS", style = MonoLabelSmall, color = colors.textMuted)
                Spacer(Modifier.height(6.dp))
                WEBHOOK_EVENTS.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { e ->
                            val on = e in events
                            Text(
                                e,
                                // Body style, not the tracked mono label: event
                                // names like challenge.complete must fit on one line.
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = if (on) colors.accent else colors.textMuted,
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .heightIn(min = 40.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, if (on) colors.accent else colors.border, CircleShape)
                                    .clickable(role = Role.Checkbox) {
                                        events = if (on) events - e else events + e
                                    }
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                EmberButton(
                    text = if (state.creating) "Creating…" else "Create webhook",
                    onClick = { vm.create(url, WEBHOOK_EVENTS.filter { it in events }) { url = "" } },
                    enabled = !state.creating && url.isNotBlank() && events.isNotEmpty(),
                    busy = state.creating,
                )
                state.notice?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accent)
                }
                Spacer(Modifier.height(24.dp))
                SectionLabel("YOUR ENDPOINTS")
                Spacer(Modifier.height(8.dp))
            }
        }

        when {
            state.loading -> item(key = "loading") {
                Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
            }
            state.error != null && state.hooks.isEmpty() -> item(key = "error") {
                Column {
                    Text(state.error ?: "", style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = vm::load)
                }
            }
            state.hooks.isEmpty() -> item(key = "empty") {
                Text(
                    "No webhooks yet. Add an endpoint above.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                )
            }
            else -> items(state.hooks, key = { it.id }) { hook ->
                WebhookCard(
                    hook = hook,
                    busy = state.busyId == hook.id,
                    expanded = state.expandedId == hook.id,
                    deliveries = if (state.expandedId == hook.id) state.deliveries else emptyList(),
                    onToggle = { vm.toggle(hook) },
                    onTest = { vm.test(hook.id) },
                    onDeliveries = { vm.toggleDeliveries(hook.id) },
                    onDelete = { pendingDelete = hook.id },
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
private fun WebhookCard(
    hook: Webhook,
    busy: Boolean,
    expanded: Boolean,
    deliveries: List<WebhookDelivery>,
    onToggle: () -> Unit,
    onTest: () -> Unit,
    onDeliveries: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = Stackd.colors
    val clipboard = LocalClipboardManager.current
    var reveal by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.textPrimary.copy(alpha = 0.02f), Radius2Xl)
            .border(1.dp, colors.border, Radius2Xl)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                hook.url,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = hook.active, onCheckedChange = { onToggle() }, enabled = !busy)
        }
        Text(hook.events.joinToString("  "), style = MaterialTheme.typography.labelMedium, color = colors.accent)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (reveal) hook.secret else "whsec ••••••••" + hook.secret.takeLast(4),
                style = MonoLabelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Reveal") }
            TextButton(onClick = { clipboard.setText(AnnotatedString(hook.secret)) }) { Text("Copy") }
        }
        Row {
            TextButton(onClick = onTest, enabled = !busy) { Text("Send test") }
            TextButton(onClick = onDeliveries) { Text(if (expanded) "Hide log" else "Deliveries") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDelete, enabled = !busy) { Text("Delete", color = colors.breach) }
        }
        if (expanded) {
            if (deliveries.isEmpty()) {
                Text(
                    "No deliveries yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            deliveries.forEach { d ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp, horizontal = 2.dp)) {
                    Text(
                        (d.statusCode?.toString() ?: "ERR"),
                        style = MonoLabelSmall,
                        color = if (d.ok) colors.live else colors.breach,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${d.event} · ${d.createdAt.take(16).replace('T', ' ')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                        )
                        d.responseSnippet?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
