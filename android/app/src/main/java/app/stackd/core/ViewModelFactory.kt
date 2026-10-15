package app.stackd.core

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import app.stackd.StackdApplication

/**
 * Builds a [ViewModelProvider.Factory] that hands each ViewModel the
 * process-wide [AppContainer]. Manual DI's counterpart to Hilt's
 * `@HiltViewModel` — the ViewModel stays a plain class with a constructor, and
 * this is the one place that knows how to reach the container.
 *
 * Usage: `viewModel(factory = stackdViewModel { AuthViewModel(it.auth) })`.
 *
 * Implemented as a plain factory rather than the `viewModelFactory { }` DSL:
 * that DSL's `initializer` is reified per concrete VM type, which a generic
 * helper can't satisfy.
 */
fun <VM : ViewModel> stackdViewModel(
    factory: (AppContainer) -> VM,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        factory(extras.appContainer) as T
}

/** The container behind a [CreationExtras], for factories that need it directly. */
val CreationExtras.appContainer: AppContainer
    get() = (this[APPLICATION_KEY] as StackdApplication).container
