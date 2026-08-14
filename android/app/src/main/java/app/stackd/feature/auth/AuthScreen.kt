package app.stackd.feature.auth

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.MonoLabelSmall
import app.stackd.core.theme.RadiusMd
import app.stackd.core.theme.Radius2Xl
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.ErrorBanner
import app.stackd.core.ui.HairlineDivider
import app.stackd.core.ui.LinkButton
import app.stackd.core.ui.NoticeBanner
import app.stackd.core.ui.SectionLabel
import app.stackd.core.ui.StackdField

/**
 * Sign in / create account, then the confirm-identity step.
 *
 * Two deliberate divergences from the web build, both already settled:
 *  - No Turnstile CAPTCHA. There is no native widget, and a signed APK is a
 *    weaker bot target than an open web form; every server-side guard stands.
 *  - No Apple button. Apple sign-in needs a web redirect Android doesn't have.
 *
 * The Google button is hidden until a Google Cloud OAuth client ID is present,
 * so email/password works standalone until that lands.
 */
@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onEmailBlur: () -> Unit,
    onPasswordBlur: () -> Unit,
    onToggleMode: () -> Unit,
    onSubmit: () -> Unit,
    onChallengeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelIdentity: () -> Unit,
    onEntered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.entered) {
        if (state.entered) onEntered()
    }

    if (state.confirmStep) {
        ConfirmIdentity(
            state = state,
            onChallengeChange = onChallengeChange,
            onConfirm = onConfirm,
            onCancel = onCancelIdentity,
            modifier = modifier,
        )
        return
    }

    val colors = Stackd.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            "STACK'D / PROTOCOL.01",
            style = MonoLabel,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(40.dp))

        Column(modifier = Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally)) {
            SectionLabel(
                if (state.mode == AuthMode.SIGN_IN) "AUTH / RETURN" else "AUTH / CREATE",
            )
            Spacer(Modifier.height(16.dp))
            Text(
                if (state.mode == AuthMode.SIGN_IN) {
                    "Re-enter the protocol."
                } else {
                    "Claim your presence."
                },
                style = MaterialTheme.typography.displayMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(36.dp))

            if (state.mode == AuthMode.SIGN_UP) {
                StackdField(
                    label = "Display Name",
                    value = state.displayName,
                    onValueChange = onDisplayNameChange,
                    placeholder = "Marcus A.",
                )
                Spacer(Modifier.height(16.dp))
            }

            StackdField(
                label = "Email",
                value = state.email,
                onValueChange = onEmailChange,
                required = true,
                placeholder = "you@domain.com",
                keyboardType = KeyboardType.Email,
                isError = state.emailInvalid,
                hint = "Enter a complete address, like you@domain.com.",
                onFocusLost = onEmailBlur,
            )
            Spacer(Modifier.height(16.dp))
            StackdField(
                label = "Password",
                value = state.password,
                onValueChange = onPasswordChange,
                required = true,
                password = true,
                placeholder = "••••••••",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isError = state.passwordInvalid,
                hint = "At least 6 characters.",
                onFocusLost = onPasswordBlur,
            )
            if (state.mode == AuthMode.SIGN_UP && !state.passwordInvalid) {
                Spacer(Modifier.height(6.dp))
                Text("MINIMUM 6 CHARACTERS", style = MonoLabelSmall, color = colors.textMuted)
            }

            Spacer(Modifier.height(24.dp))
            EmberButton(
                text = when {
                    state.pending && state.mode == AuthMode.SIGN_IN -> "Signing in…"
                    state.pending -> "Creating account…"
                    state.mode == AuthMode.SIGN_IN -> "Continue with Email"
                    else -> "Create Account"
                },
                onClick = onSubmit,
                enabled = !state.submitBlocked,
                busy = state.pending,
            )

            state.notice?.let {
                Spacer(Modifier.height(16.dp))
                NoticeBanner(it)
            }
            state.error?.let {
                Spacer(Modifier.height(16.dp))
                ErrorBanner(it, onRetry = onSubmit)
            }

            Spacer(Modifier.height(32.dp))
            LinkButton(
                text = if (state.mode == AuthMode.SIGN_IN) {
                    "No protocol key? Create one →"
                } else {
                    "Already have one? Sign in →"
                },
                onClick = onToggleMode,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * "Is this you?" — interposed after any successful auth, matching the web.
 *
 * With an email present this is a plain acknowledgement. Without one (a
 * provider that shares no address) there is nothing human-readable to check, so
 * a 4-character challenge derived from the user id stands in; three misses sign
 * the session out rather than leaving a stranger parked on someone's account.
 */
@Composable
private fun ConfirmIdentity(
    state: AuthUiState,
    onChallengeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Stackd.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text("STACK'D / VERIFY", style = MonoLabel, color = colors.textMuted)
        Spacer(Modifier.height(40.dp))

        Column(modifier = Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally)) {
            SectionLabel("AUTH / STEP 02 — CONFIRM IDENTITY")
            Spacer(Modifier.height(16.dp))
            Text(
                "Is this you?",
                style = MaterialTheme.typography.displayMedium,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.textPrimary.copy(alpha = 0.03f), Radius2Xl)
                    .border(1.dp, colors.textPrimary.copy(alpha = 0.1f), Radius2Xl)
                    .padding(20.dp),
            ) {
                Text("SIGNED-IN AS", style = MonoLabel, color = colors.textMuted)
                Spacer(Modifier.height(8.dp))
                if (state.confirmNeedsChallenge) {
                    Text(
                        "No email returned by provider",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your provider didn't share an email. Confirm the verification " +
                            "code below to continue, or sign out and try another way in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("ACCOUNT ID", style = MonoLabel, color = colors.textMuted)
                    Text(
                        state.confirmUserId.orEmpty(),
                        style = MonoLabelSmall,
                        color = colors.textPrimary.copy(alpha = 0.8f),
                    )
                } else {
                    Text(
                        state.confirmEmail.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                }
            }

            if (state.confirmNeedsChallenge) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TYPE THESE 4 CHARACTERS: ",
                        style = MonoLabel,
                        color = colors.textMuted,
                    )
                    Text(state.expectedChallenge, style = MonoLabel, color = colors.accent)
                }
                Spacer(Modifier.height(8.dp))
                StackdField(
                    label = "Confirmation",
                    value = state.confirmChallenge,
                    onValueChange = onChallengeChange,
                    centeredMono = true,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))
                val attemptsLeft = AuthViewModel.MAX_CONFIRM_ATTEMPTS - state.confirmAttempts
                Text(
                    when {
                        state.autoSignedOut -> "AUTO SIGNED OUT — TOO MANY ATTEMPTS."
                        state.confirmAttempts > 0 ->
                            "DOESN'T MATCH. $attemptsLeft ATTEMPT" +
                                (if (attemptsLeft == 1) "" else "S") + " LEFT."
                        else -> ""
                    },
                    style = MonoLabelSmall,
                    color = colors.breach,
                )
            } else {
                Spacer(Modifier.height(20.dp))
                Text(
                    "For your safety we ask you to confirm before granting access to " +
                        "the protocol. If this isn't you, cancel and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted,
                )
            }

            Spacer(Modifier.height(28.dp))
            EmberButton(
                text = "Confirm & Enter",
                onClick = onConfirm,
                enabled = !state.autoSignedOut,
            )
            Spacer(Modifier.height(12.dp))
            LinkButton(
                text = "Not me — sign out",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                color = colors.textMuted,
            )
        }
    }
}
