package nart.simpleanki.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Google sign-in failed") }
        }
    }

    fun onContinueAsGuest() {
        viewModelScope.launch {
            repository.signInAnonymously()
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "Guest sign-in failed") }
        }
    }

    fun onGoogleSignInError(message: String) {
        _uiState.value = AuthUiState.Error(message)
    }

    fun onSignOut() = repository.signOut()
}
