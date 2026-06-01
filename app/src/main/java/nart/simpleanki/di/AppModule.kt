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
import nart.simpleanki.core.domain.fsrs.SchedulingService
import nart.simpleanki.feature.cardform.CardFormViewModel
import nart.simpleanki.feature.decksettings.DeckEditViewModel
import nart.simpleanki.feature.deckdetail.DeckDetailViewModel
import nart.simpleanki.feature.library.FolderEditViewModel
import nart.simpleanki.feature.library.LibraryViewModel
import nart.simpleanki.feature.study.StudyViewModel
import nart.simpleanki.feature.sync.SyncViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Firebase Web client ID (OAuth client_type 3) for Credential Manager. */
private const val WEB_CLIENT_ID =
    "592209129003-sfmoavbuj0crmqm35dslhfpo3b10ol19.apps.googleusercontent.com"

/** Injection args for screens that take optional ids (unambiguous vs. positional params). */
data class CardFormArgs(val deckId: String, val cardId: String? = null)
data class DeckEditArgs(val deckId: String? = null, val folderId: String? = null)
data class FolderEditArgs(val folderId: String? = null)

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

    // Scheduling
    single { SchedulingService() }

    // Feature ViewModels
    viewModel { SyncViewModel(get()) }
    viewModel { LibraryViewModel(get(), get()) }
    viewModel { params -> DeckDetailViewModel(deckId = params.get(), cardRepository = get(), deckRepository = get()) }
    viewModel { params -> StudyViewModel(deckId = params.get(), cardRepository = get(), scheduling = get()) }
    viewModel { params ->
        val a = params.get<CardFormArgs>()
        CardFormViewModel(deckId = a.deckId, cardRepository = get(), editingCardId = a.cardId)
    }
    viewModel { params ->
        val a = params.get<DeckEditArgs>()
        DeckEditViewModel(deckRepository = get(), editingDeckId = a.deckId, initialFolderId = a.folderId)
    }
    viewModel { params ->
        val a = params.get<FolderEditArgs>()
        FolderEditViewModel(folderRepository = get(), editingFolderId = a.folderId)
    }
}
