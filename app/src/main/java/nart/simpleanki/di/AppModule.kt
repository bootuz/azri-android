package nart.simpleanki.di

import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.auth.AuthViewModel
import nart.simpleanki.auth.FirebaseAuthRepository
import nart.simpleanki.auth.GoogleSignInClient
import nart.simpleanki.core.data.local.AzriDatabase
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.data.sync.FirestoreSyncService
import nart.simpleanki.core.data.sync.RemoteSyncSource
import nart.simpleanki.core.data.sync.SyncManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Firebase Web client ID (OAuth client_type 3) for Credential Manager. */
private const val WEB_CLIENT_ID =
    "592209129003-sfmoavbuj0crmqm35dslhfpo3b10ol19.apps.googleusercontent.com"

/** Root Koin module. */
val appModule = module {
    // Firebase
    single<FirebaseAuth> { Firebase.auth }
    single<FirebaseFirestore> { Firebase.firestore }

    // Auth
    single<AuthRepository> { FirebaseAuthRepository(get()) }
    single { GoogleSignInClient(serverClientId = WEB_CLIENT_ID) }
    viewModel { AuthViewModel(get()) }

    // Local persistence (Room)
    single {
        Room.databaseBuilder(androidContext(), AzriDatabase::class.java, "azri.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<AzriDatabase>().folderDao() }
    single { get<AzriDatabase>().deckDao() }
    single { get<AzriDatabase>().cardDao() }

    // Repositories
    single { FolderRepository(get()) }
    single { DeckRepository(get()) }
    single { CardRepository(get()) }

    // Sync
    single<RemoteSyncSource> { FirestoreSyncService(get()) }
    single { SyncManager(get(), get(), get(), get()) }
}
