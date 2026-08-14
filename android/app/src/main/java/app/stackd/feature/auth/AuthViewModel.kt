package app.stackd.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stackd.core.AppContainer
import app.stackd.data.auth.AuthOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SIGN_IN, SIGN_UP }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val emailTouched: Boolean = false,
    val passwordTouched: Boolean = false,
    val pending: Boolean = false,
    val error: String? = null,
    /** Set after a successful sign-up: no session exists, so this is not a route change. */
    val notice: String? = null,
    /** A session exists; the confirm-identity step is showing. */
    val confirmStep: Boolean = false,
    val confirmUserId: String? = null,
    val confirmEmail: String? = null,
    val confirmChallenge: String = "",
    val confirmAttempts: Int = 0,
    val autoSignedOut: Boolean = false,
    /** True once identity is confirmed — the screen navigates on this. */
    val entered: Boolean = false,
) {
    val emailInvalid: Boolean
        get() = emailTouched && email.isNotEmpty() && !EMAIL_PATTERN.matches(email)

    val passwordInvalid: Boolean
        get() = passwordTouched && password.isNotEmpty() && password.length < MIN_PASSWORD

    val submitBlocked: Boolean get() = emailInvalid || passwordInvalid

    /** No email from the provider — confirm against a derived challenge instead. */
    val confirmNeedsChallenge: Boolean get() = confirmEmail.isNullOrBlank()

    /** Deterministic 4-char challenge from the user id, matching the web. */
    val expectedChallenge: String
        get() = confirmUserId.orEmpty().replace("-", "").take(4).uppercase()

    private companion object {
        val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
        const val MIN_PASSWORD = 6
    }
}

/**
 * Drives the auth screen and the confirm-identity step that follows it.
 *
 * Deliberately thin: every guard, throttle and credential check happens
 * server-side in the `auth-guard` Edge Function (see [app.stackd.data.auth.AuthRepository]),
 * so there is nothing here that a modified client could bypass to its own
 * advantage. What lives here is presentation state — which field is touched,
 * whether we're mid-request, and how many times the challenge has been missed.
 */
class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        // A session may already exist from a previous launch — the web shows
        // the same confirm step on return rather than silently entering.
        container.auth.currentUserId?.let { enterConfirmStep(it) }
    }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onDisplayNameChange(value: String) = _state.update { it.copy(displayName = value) }
    fun onEmailBlur() = _state.update { it.copy(emailTouched = true) }
    fun onPasswordBlur() = _state.update { it.copy(passwordTouched = true) }

    fun toggleMode() = _state.update {
        // Clear the other mode's leftovers — a "wrong password" error still
        // sitting there after switching to Create Account reads as the new form
        // already being broken.
        it.copy(
            mode = if (it.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
            error = null,
            notice = null,
            emailTouched = false,
            passwordTouched = false,
        )
    }

    fun submit() {
        val current = _state.value
        if (current.pending || current.submitBlocked) return
        _state.update { it.copy(pending = true, error = null, notice = null) }

        viewModelScope.launch {
            val outcome = when (current.mode) {
                AuthMode.SIGN_IN -> container.auth.signIn(current.email.trim(), current.password)
                AuthMode.SIGN_UP -> container.auth.signUp(
                    email = current.email.trim(),
                    password = current.password,
                    displayName = current.displayName,
                )
            }
            applyOutcome(outcome)
        }
    }

    fun signInWithGoogle(idToken: String, rawNonce: String?) {
        if (_state.value.pending) return
        _state.update { it.copy(pending = true, error = null) }
        viewModelScope.launch {
            applyOutcome(container.auth.signInWithGoogle(idToken, rawNonce))
        }
    }

    private fun applyOutcome(outcome: AuthOutcome) {
        when (outcome) {
            AuthOutcome.SignedIn -> {
                _state.update { it.copy(pending = false) }
                container.auth.currentUserId?.let { enterConfirmStep(it) }
            }

            AuthOutcome.ConfirmationEmailSent -> _state.update {
                // Email confirmation is required on this project, so sign-up
                // yields no session — this must not route to the dashboard.
                it.copy(
                    pending = false,
                    notice = "Check your email to confirm your account.",
                    password = "",
                )
            }

            is AuthOutcome.Failed -> _state.update {
                it.copy(pending = false, error = outcome.message)
            }
        }
    }

    private fun enterConfirmStep(userId: String) {
        val email = container.auth.currentEmail
        _state.update {
            it.copy(
                confirmStep = true,
                confirmUserId = userId,
                confirmEmail = email,
                confirmChallenge = "",
                confirmAttempts = 0,
                autoSignedOut = false,
            )
        }
    }

    fun onChallengeChange(value: String) = _state.update {
        it.copy(confirmChallenge = value.uppercase().take(CHALLENGE_LENGTH))
    }

    /**
     * Confirms the signed-in identity. With an email present this is a plain
     * acknowledgement; without one there is nothing human-readable to check
     * against, so the derived challenge stands in — and three misses sign the
     * session out rather than leaving a stranger parked on someone's account.
     */
    fun confirmIdentity() {
        val current = _state.value
        if (current.autoSignedOut) return

        if (current.confirmNeedsChallenge &&
            current.confirmChallenge.trim() != current.expectedChallenge
        ) {
            val attempts = current.confirmAttempts + 1
            _state.update { it.copy(confirmAttempts = attempts) }
            if (attempts >= MAX_CONFIRM_ATTEMPTS) {
                _state.update { it.copy(autoSignedOut = true) }
                cancelIdentity()
            }
            return
        }
        _state.update { it.copy(entered = true) }
    }

    /** "Not me" — drop the session and return to the form. */
    fun cancelIdentity() {
        viewModelScope.launch {
            container.auth.signOut()
            _state.update {
                it.copy(
                    confirmStep = false,
                    confirmUserId = null,
                    confirmEmail = null,
                    confirmChallenge = "",
                    password = "",
                    entered = false,
                )
            }
        }
    }

    fun consumeEntered() = _state.update { it.copy(entered = false) }

    companion object {
        const val MAX_CONFIRM_ATTEMPTS = 3
        const val CHALLENGE_LENGTH = 4
    }
}
