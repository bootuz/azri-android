package nart.simpleanki.auth

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsSignedOut_whenNoUser() = runTest {
        val vm = AuthViewModel(FakeAuthRepository())
        vm.uiState.test {
            assertEquals(AuthUiState.SignedOut, awaitItem())
        }
    }

    @Test
    fun reflectsSignedIn_whenAuthStateEmitsUser() = runTest {
        val fake = FakeAuthRepository()
        val vm = AuthViewModel(fake)
        vm.uiState.test {
            assertEquals(AuthUiState.SignedOut, awaitItem())
            fake.emit(FakeAuthRepository.GOOGLE_USER)
            val state = awaitItem()
            assertTrue(state is AuthUiState.SignedIn)
            assertEquals("grace@example.com", (state as AuthUiState.SignedIn).user.email)
        }
    }

    @Test
    fun guestSignIn_movesToSignedIn() = runTest {
        val fake = FakeAuthRepository()
        val vm = AuthViewModel(fake)
        vm.uiState.test {
            assertEquals(AuthUiState.SignedOut, awaitItem())
            vm.onContinueAsGuest()
            val state = awaitItem()
            assertTrue(state is AuthUiState.SignedIn)
            assertTrue((state as AuthUiState.SignedIn).user.isAnonymous)
        }
    }

    @Test
    fun googleSignInFailure_setsError() = runTest {
        val fake = FakeAuthRepository().apply {
            googleResult = Result.failure(IllegalStateException("boom"))
        }
        val vm = AuthViewModel(fake)
        vm.uiState.test {
            assertEquals(AuthUiState.SignedOut, awaitItem())
            vm.onGoogleIdToken("bad-token")
            val state = awaitItem()
            assertTrue(state is AuthUiState.Error)
            assertEquals("boom", (state as AuthUiState.Error).message)
        }
    }

    @Test
    fun signOut_delegatesToRepository() = runTest {
        val fake = FakeAuthRepository()
        fake.emit(FakeAuthRepository.GOOGLE_USER)
        val vm = AuthViewModel(fake)
        vm.onSignOut()
        assertEquals(1, fake.signOutCalls)
    }
}
