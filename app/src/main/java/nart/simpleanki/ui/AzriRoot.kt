package nart.simpleanki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import nart.simpleanki.auth.AuthUiState
import nart.simpleanki.auth.AuthViewModel
import nart.simpleanki.auth.GoogleSignInClient
import nart.simpleanki.feature.auth.SignInScreen
import nart.simpleanki.ui.navigation.AzriNavHost
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Auth-gated root: shows Home when signed in, otherwise the sign-in screen. */
@Composable
fun AzriRoot(
    viewModel: AuthViewModel = koinViewModel(),
    googleSignInClient: GoogleSignInClient = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    when (val s = state) {
        is AuthUiState.SignedIn -> AzriNavHost(onSignOut = viewModel::onSignOut)
        else -> SignInScreen(
            onGoogleClick = {
                scope.launch {
                    googleSignInClient.getIdToken(context)
                        .onSuccess { viewModel.onGoogleIdToken(it) }
                        .onFailure { viewModel.onGoogleSignInError(it.message ?: "Sign-in cancelled") }
                }
            },
            onGuestClick = viewModel::onContinueAsGuest,
            errorMessage = (s as? AuthUiState.Error)?.message,
        )
    }
}
