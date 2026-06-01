package nart.simpleanki.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [AuthRepository] for unit tests. */
class FakeAuthRepository(
    private val state: MutableStateFlow<AuthUser?> = MutableStateFlow(null),
) : AuthRepository {

    var googleResult: Result<AuthUser> = Result.success(GOOGLE_USER)
    var anonymousResult: Result<AuthUser> = Result.success(ANON_USER)
    var signOutCalls = 0

    override val authState: Flow<AuthUser?> = state

    override suspend fun signInWithGoogle(idToken: String): Result<AuthUser> {
        googleResult.onSuccess { state.value = it }
        return googleResult
    }

    override suspend fun signInAnonymously(): Result<AuthUser> {
        anonymousResult.onSuccess { state.value = it }
        return anonymousResult
    }

    override fun signOut() {
        signOutCalls++
        state.value = null
    }

    fun emit(user: AuthUser?) {
        state.value = user
    }

    companion object {
        val GOOGLE_USER = AuthUser("g-uid", "Grace Hopper", "grace@example.com", isAnonymous = false)
        val ANON_USER = AuthUser("a-uid", null, null, isAnonymous = true)
    }
}
