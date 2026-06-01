package nart.simpleanki.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Firebase-backed [AuthRepository]. */
class FirebaseAuthRepository(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        result.user?.toAuthUser() ?: error("Google sign-in returned no user")
    }

    override suspend fun signInAnonymously(): Result<AuthUser> = runCatching {
        val result = auth.signInAnonymously().await()
        result.user?.toAuthUser() ?: error("Anonymous sign-in returned no user")
    }

    override fun signOut() = auth.signOut()
}

private fun FirebaseUser.toAuthUser(): AuthUser = AuthUser(
    uid = uid,
    displayName = displayName,
    email = email,
    isAnonymous = isAnonymous,
)
