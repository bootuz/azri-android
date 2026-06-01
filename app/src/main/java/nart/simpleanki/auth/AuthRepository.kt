package nart.simpleanki.auth

import kotlinx.coroutines.flow.Flow

/**
 * Authentication seam. The Firebase implementation lives in [FirebaseAuthRepository];
 * tests substitute a fake. [authState] emits null when signed out.
 */
interface AuthRepository {
    val authState: Flow<AuthUser?>
    suspend fun signInWithGoogle(idToken: String): Result<AuthUser>
    suspend fun signInAnonymously(): Result<AuthUser>
    fun signOut()
}
