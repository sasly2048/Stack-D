package app.stackd.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stackd.core.stackdViewModel

/**
 * Stateful wrapper for [AuthScreen]: owns the [AuthViewModel] and hoists the one
 * navigation the screen produces — [onAuthenticated], fired once the user has
 * cleared the confirm-identity step. Everything else is internal to the VM.
 */
@Composable
fun AuthRoute(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    vm: AuthViewModel = viewModel(factory = stackdViewModel { AuthViewModel(it) }),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AuthScreen(
        state = state,
        onEmailChange = vm::onEmailChange,
        onPasswordChange = vm::onPasswordChange,
        onDisplayNameChange = vm::onDisplayNameChange,
        onEmailBlur = vm::onEmailBlur,
        onPasswordBlur = vm::onPasswordBlur,
        onToggleMode = vm::toggleMode,
        onSubmit = vm::submit,
        onChallengeChange = vm::onChallengeChange,
        onConfirm = vm::confirmIdentity,
        onCancelIdentity = vm::cancelIdentity,
        onEntered = {
            vm.consumeEntered()
            onAuthenticated()
        },
        modifier = modifier,
    )
}
