package nart.simpleanki.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import nart.simpleanki.auth.AuthUiState
import nart.simpleanki.auth.AuthViewModel
import nart.simpleanki.auth.GoogleSignInClient
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.feature.auth.SignInScreen
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.Entitlements
import nart.simpleanki.feature.sync.SyncViewModel
import nart.simpleanki.ui.navigation.AzriNavHost
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/** Auth-gated root: shows Home when signed in, otherwise the sign-in screen. */
@Composable
fun AzriRoot(
    viewModel: AuthViewModel = koinViewModel(),
    googleSignInClient: GoogleSignInClient = koinInject(),
    syncViewModel: SyncViewModel = koinViewModel(),
    entitlementRepository: EntitlementRepository = koinInject(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logManager: LogManager = koinInject()

    when (val s = state) {
        is AuthUiState.SignedIn -> {
            // Foreground sync: immediately on sign-in, then periodically so local edits
            // (and remote changes from iOS/web) propagate without a relaunch.
            LaunchedEffect(s.user.uid) {
                while (true) {
                    val premium = entitlementRepository.entitlement.value.isPremium
                    if (Entitlements.shouldSync(isPremium = premium, signedInWithGoogle = !s.user.isAnonymous)) {
                        syncViewModel.sync(s.user.uid)
                    }
                    kotlinx.coroutines.delay(20_000)
                }
            }
            LaunchedEffect(s.user.uid) {
                logManager.identifyUser(s.user.uid, s.user.displayName, s.user.email)
            }
            AzriNavHost()
        }
        else -> {
            // Only clear once auth has actually resolved to signed-out/error — not during the
            // initial Loading flash, which would emit a spurious clear on every cold start.
            if (s !is AuthUiState.Loading) {
                LaunchedEffect(Unit) { logManager.clearUser() }
            }
            SignInScreen(
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
}
