package nart.simpleanki.di

import androidx.room.Room
import nart.simpleanki.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.core.data.media.FirebaseMediaRepository
import nart.simpleanki.core.data.media.MediaUploader
import nart.simpleanki.auth.AuthViewModel
import nart.simpleanki.auth.FirebaseAuthRepository
import nart.simpleanki.auth.GoogleSignInClient
import nart.simpleanki.core.data.local.AzriDatabase
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.data.settings.DataStoreSettingsRepository
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.data.sync.FirestoreSyncService
import nart.simpleanki.core.data.sync.RemoteSyncSource
import nart.simpleanki.core.data.sync.SyncManager
import nart.simpleanki.feature.cardform.CardFormViewModel
import nart.simpleanki.feature.profile.ProfileViewModel
import nart.simpleanki.feature.settings.SettingsViewModel
import nart.simpleanki.feature.decksettings.DeckEditViewModel
import nart.simpleanki.feature.deckdetail.DeckDetailViewModel
import nart.simpleanki.feature.folderdetail.FolderDetailViewModel
import nart.simpleanki.feature.library.FolderEditViewModel
import nart.simpleanki.feature.library.LibraryViewModel
import nart.simpleanki.core.notifications.Notifier
import nart.simpleanki.core.notifications.ReminderScheduler
import nart.simpleanki.core.notifications.WorkManagerReminderScheduler
import nart.simpleanki.feature.notifications.NotificationsViewModel
import nart.simpleanki.feature.queue.DailyGoalViewModel
import nart.simpleanki.feature.queue.StudyQueueViewModel
import nart.simpleanki.feature.study.StudyViewModel
import nart.simpleanki.feature.sync.SyncViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Injection args for screens that take optional ids (unambiguous vs. positional params). */
data class CardFormArgs(val deckId: String, val cardId: String? = null)
data class DeckEditArgs(val deckId: String? = null, val folderId: String? = null)
data class FolderEditArgs(val folderId: String? = null)
data class StudyArgs(val deckId: String? = null, val folderId: String? = null)

/** Root Koin module. */
val appModule = module {
    // Firebase
    single<FirebaseAuth> { Firebase.auth }
    single<FirebaseFirestore> { Firebase.firestore }
    single<FirebaseStorage> { Firebase.storage }
    single<MediaUploader> { FirebaseMediaRepository(get(), get()) }

    // Auth
    single<AuthRepository> { FirebaseAuthRepository(get()) }
    // Web client ID (OAuth client_type 3) is generated from each developer's own
    // google-services.json by the google-services plugin — never hard-coded.
    single { GoogleSignInClient(serverClientId = androidContext().getString(R.string.default_web_client_id)) }
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

    // Settings
    single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
    single { Notifier(androidContext()) }
    single<ReminderScheduler> { WorkManagerReminderScheduler(androidContext()) }

    // Feature ViewModels
    viewModel { SyncViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { ProfileViewModel(settingsRepository = get(), authRepository = get()) }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { params -> DeckDetailViewModel(deckId = params.get(), cardRepository = get(), deckRepository = get()) }
    viewModel { params ->
        FolderDetailViewModel(
            folderId = params.get(),
            deckRepository = get(),
            cardRepository = get(),
            folderRepository = get(),
        )
    }
    viewModel { params ->
        val args = params.get<StudyArgs>()
        StudyViewModel(
            deckId = args.deckId,
            folderId = args.folderId,
            cardRepository = get(),
            deckRepository = get(),
            settingsRepository = get(),
        )
    }
    viewModel { StudyQueueViewModel(cardRepository = get(), deckRepository = get(), folderRepository = get(), settingsRepository = get()) }
    viewModel { DailyGoalViewModel(settingsRepository = get()) }
    viewModel { NotificationsViewModel(settingsRepository = get(), scheduler = get()) }
    viewModel { params ->
        val a = params.get<CardFormArgs>()
        CardFormViewModel(deckId = a.deckId, cardRepository = get(), mediaUploader = get(), editingCardId = a.cardId)
    }
    viewModel { params ->
        val a = params.get<DeckEditArgs>()
        DeckEditViewModel(deckRepository = get(), folderRepository = get(), cardRepository = get(), editingDeckId = a.deckId, initialFolderId = a.folderId)
    }
    viewModel { params ->
        val a = params.get<FolderEditArgs>()
        FolderEditViewModel(folderRepository = get(), deckRepository = get(), editingFolderId = a.folderId)
    }
}
