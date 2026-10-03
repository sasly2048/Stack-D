package app.stackd.feature.trust

import app.stackd.core.ui.glassSurface

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.font.FontStyle
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
import app.stackd.core.theme.RadiusMd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import app.stackd.core.ui.ResponsiveColumn
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.SkeletonBlock
import app.stackd.core.ui.SkeletonCard
import app.stackd.feature.profile.FeatureEmptyState
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.Role
import app.stackd.data.trust.BlockedUser
import app.stackd.data.trust.MyReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class TrustUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val blocks: List<BlockedUser> = emptyList(),
    val reports: List<MyReport> = emptyList(),
    val unblocking: String? = null,
    val filing: Boolean = false,
    val notice: String? = null,
)

/** Report reasons the web offers; kept short so they fit the `kind` column. */
val REPORT_KINDS = listOf("harassment", "spam", "cheating", "inappropriate", "other")

/** Trust & Safety — web's `trust.tsx`: blocked users + reports you filed. */
class TrustViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(TrustUiState())
    val state: StateFlow<TrustUiState> = _state

    init {
        load()
    }

    private fun cacheKey(userId: String) = "trust:$userId"

    fun load() {
        val userId = container.auth.currentUserId ?: return
        // Stale-while-revalidate: seed from the last cached state so re-entry
        // shows data instantly instead of a spinner, then revalidate below.
        val cached: TrustUiState? = container.cache.get(cacheKey(userId))
        _state.value = (cached ?: _state.value).copy(loading = cached == null, error = false)
        viewModelScope.launch {
            runCatching {
                container.trust.listBlocks(userId) to container.trust.listMyReports(userId)
            }.fold(
                onSuccess = { (blocks, reports) ->
                    val fresh = TrustUiState(loading = false, blocks = blocks, reports = reports)
                    _state.value = fresh
                    container.cache.put(cacheKey(userId), fresh)
                },
                onFailure = { _state.value = _state.value.copy(loading = false, error = cached == null) },
            )
        }
    }

    fun fileRoomReport(code: String, kind: String, reason: String) {
        val userId = container.auth.currentUserId ?: return
        if (code.isBlank() || _state.value.filing) return
        _state.value = _state.value.copy(filing = true, notice = null)
        viewModelScope.launch {
            runCatching {
                container.trust.reportRoomByCode(userId, code, kind, reason.ifBlank { null })
            }.fold(
                onSuccess = { found ->
                    _state.value = _state.value.copy(
                        filing = false,
                        notice = if (found) "Report filed. Moderators will review it."
                        else "No room found with that code.",
                    )
                    if (found) load()
                },
                onFailure = {
                    _state.value = _state.value.copy(filing = false, notice = "Couldn't file the report.")
                },
            )
        }
    }

    fun unblock(targetId: String) {
        val userId = container.auth.currentUserId ?: return
        if (_state.value.unblocking != null) return
        _state.value = _state.value.copy(unblocking = targetId)
        viewModelScope.launch {
            runCatching { container.trust.unblockUser(userId, targetId) }.fold(
                onSuccess = {
                    _state.value = _state.value.copy(
                        unblocking = null,
                        blocks = _state.value.blocks.filterNot { it.userId == targetId },
                        notice = "Unblocked.",
                    )
                },
                onFailure = {
                    _state.value = _state.value.copy(unblocking = null, notice = "Could not unblock.")
                },
            )
        }
    }
}

@Composable
fun TrustRoute(
    onBack: () -> Unit,
    onOpenModeration: () -> Unit,
    modifier: Modifier = Modifier,
    vm: TrustViewModel = viewModel(factory = stackdViewModel { TrustViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    TrustScreen(
        state = state,
        onUnblock = vm::unblock,
        onFileReport = vm::fileRoomReport,
        onRetry = vm::load,
        onBack = onBack,
        onOpenModeration = onOpenModeration,
        modifier = modifier,
    )
}

@Composable
fun TrustScreen(
    state: TrustUiState,
    onUnblock: (String) -> Unit,
    onFileReport: (String, String, String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onOpenModeration: () -> Unit,
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
            app.stackd.core.ui.ScreenHeader("STACK'D / SAFETY", onBack, title = "Trust & safety")
            Spacer(Modifier.height(16.dp))
            Text(
                "You control your room. Stack'd never shares what happens in your sessions with anyone outside it.",
                style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary,
            )
            Spacer(Modifier.height(24.dp))
            SectionLabel("TRUST & SAFETY")
            Spacer(Modifier.height(8.dp))
            Text(
                "Blocks are silent. Reports go to moderators.",
                style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
            )
            Spacer(Modifier.height(16.dp))
            GhostButton(text = "Host moderation dashboard", onClick = onOpenModeration)

            Spacer(Modifier.height(24.dp))
            ReportRoomForm(filing = state.filing, onFile = onFileReport)

            state.notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.accent)
            }

            Spacer(Modifier.height(24.dp))
            when {
                state.loading -> {
                    SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(10.dp))
                    Spacer(Modifier.height(8.dp))
                    repeat(2) {
                        SkeletonBlock(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(56.dp), Radius2Xl)
                    }
                    Spacer(Modifier.height(24.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.3f).height(10.dp))
                    Spacer(Modifier.height(8.dp))
                    SkeletonCard(height = 88.dp)
                }
                state.error -> {
                    Text(
                        "Couldn't load your safety settings.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.textMuted,
                    )
                    Spacer(Modifier.height(12.dp))
                    GhostButton(text = "Retry", onClick = onRetry)
                }
                else -> {
                    Text("BLOCKED USERS", style = MonoLabelSmall, color = colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                    if (state.blocks.isEmpty()) {
                        FeatureEmptyState(
                            icon = app.stackd.core.ui.StackdIcons.VerifiedUser,
                            title = "Nobody blocked",
                            body = "You're on good terms with everyone.",
                        )
                    }
                    state.blocks.forEach { b ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .glassSurface(Radius2Xl)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                b.displayName?.takeIf { it.isNotBlank() } ?: "Anon",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (state.unblocking == b.userId) "Unblocking…" else "Unblock",
                                style = MonoLabelSmall,
                                color = colors.accent,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable(enabled = state.unblocking == null, role = Role.Button) {
                                        onUnblock(b.userId)
                                    }
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .padding(horizontal = 8.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("YOUR REPORTS", style = MonoLabelSmall, color = colors.textMuted)
                    Spacer(Modifier.height(8.dp))
                    if (state.reports.isEmpty()) {
                        FeatureEmptyState(
                            icon = app.stackd.core.ui.StackdIcons.Flag,
                            title = "No reports filed",
                            body = "Report from any profile or room when something's off.",
                        )
                    }
                    state.reports.forEach { ReportCard(it) }
                }
            }

            Spacer(Modifier.height(56.dp))
        }
    }
}

@Composable
private fun ReportCard(r: MyReport) {
    val colors = Stackd.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .glassSurface(Radius2Xl)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(r.kind, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            Text(
                r.status.uppercase(),
                style = MonoLabelSmall,
                color = if (r.status == "open") colors.accent else colors.textMuted,
            )
        }
        r.reason?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
        }
        Spacer(Modifier.height(4.dp))
        Text(trustDate(r.createdAt), style = MonoLabelSmall, color = colors.textMuted)
    }
}

/**
 * File a report against a room by its code — the one entry point the app has
 * for `fileReport` until the room screen grows its own report action. Web files
 * from profile and room; a room code is what a user can actually read off a
 * lobby, so this covers the room path without threading through the room state
 * machine.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReportRoomForm(filing: Boolean, onFile: (String, String, String) -> Unit) {
    val colors = Stackd.colors
    var code by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(REPORT_KINDS.first()) }
    var reason by remember { mutableStateOf("") }

    Text("REPORT A ROOM", style = MonoLabelSmall, color = colors.textMuted)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = code,
        onValueChange = { if (it.length <= 12) code = it },
        label = { Text("Room code") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        REPORT_KINDS.forEach { k ->
            val on = kind == k
            Text(
                k,
                style = MonoLabelSmall,
                color = if (on) colors.accent else colors.textMuted,
                modifier = Modifier
                    // 48dp touch area; the chip itself stays compact.
                    .minimumInteractiveComponentSize()
                    .clip(RadiusMd)
                    .background(
                        if (on) colors.accent.copy(alpha = 0.1f) else colors.textPrimary.copy(alpha = 0.04f),
                        RadiusMd,
                    )
                    .border(1.dp, if (on) colors.accent.copy(alpha = 0.6f) else colors.border, RadiusMd)
                    .clickable(role = androidx.compose.ui.semantics.Role.RadioButton) { kind = k }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = reason,
        onValueChange = { if (it.length <= 500) reason = it },
        label = { Text("Reason (optional)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(16.dp))
    EmberButton(
        text = if (filing) "Filing…" else "File report",
        onClick = {
            onFile(code, kind, reason)
            code = ""; reason = ""
        },
        enabled = code.isNotBlank() && !filing,
        busy = filing,
    )
}
