package nart.simpleanki.di

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.auth.AuthViewModel
import nart.simpleanki.auth.FirebaseAuthRepository
import nart.simpleanki.auth.GoogleSignInClient
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Firebase Web client ID (OAuth client_type 3) for Credential Manager. */
private const val WEB_CLIENT_ID =
    "592209129003-sfmoavbuj0crmqm35dslhfpo3b10ol19.apps.googleusercontent.com"

/** Root Koin module. Feature modules add their own definitions. */
val appModule = module {
    single<FirebaseAuth> { Firebase.auth }
    single<FirebaseFirestore> { Firebase.firestore }
    single<AuthRepository> { FirebaseAuthRepository(get()) }
    single { GoogleSignInClient(serverClientId = WEB_CLIENT_ID) }
    viewModel { AuthViewModel(get()) }
}
