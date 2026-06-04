package nart.simpleanki.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import nart.simpleanki.core.analytics.LoggableEvent
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.analytics.LogType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object SignedOut : AuthUiState
    data class SignedIn(val user: AuthUser) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

/**
 * Drives auth-gated navigation. Google credential retrieval (which needs an Activity)
 * happens in the UI layer; the resulting id token is handed to [onGoogleIdToken].
 */
class AuthViewModel(
    private val repository: AuthRepository,
    private val logManager: LogManager = LogManager(emptyList()),
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.authState.collect { user ->
                _uiState.value = if (user != null) AuthUiState.SignedIn(user) else AuthUiState.SignedOut
            }
        }
    }

    fun onGoogleIdToken(idToken: String) {
        viewModelScope.launch {
            repository.signInWithGoogle(idToken)
                .onSuccess { logManager.track(Event.SignInGoogle) }
                .onFailure {
                    logManager.track(Event.SignInFailed(it.message ?: "unknown"))
                    _uiState.value = AuthUiState.Error(it.message ?: "Google sign-in failed")
                }
        }
    }

    fun onContinueAsGuest() {
        viewModelScope.launch {
            repository.signInAnonymously()
                .onSuccess { logManager.track(Event.ContinueAsGuest) }
                .onFailure {
                    logManager.track(Event.SignInFailed(it.message ?: "unknown"))
                    _uiState.value = AuthUiState.Error(it.message ?: "Guest sign-in failed")
                }
        }
    }

    fun onGoogleSignInError(message: String) {
        logManager.track(Event.SignInFailed(message))
        _uiState.value = AuthUiState.Error(message)
    }

    fun onSignOut() {
        logManager.track(Event.SignOut)
        repository.signOut()
    }

    private sealed interface Event : LoggableEvent {
        data object SignInGoogle : Event { override val eventName = "sign_in_google" }
        data object ContinueAsGuest : Event { override val eventName = "continue_as_guest" }
        data object SignOut : Event { override val eventName = "sign_out" }
        data class SignInFailed(val reason: String) : Event {
            override val eventName = "sign_in_failed"
            override val params get() = mapOf("reason" to reason)
            override val type get() = LogType.Warning
        }
    }
}
