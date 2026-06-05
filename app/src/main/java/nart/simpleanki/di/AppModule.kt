package nart.simpleanki.di

import androidx.room.Room
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import nart.simpleanki.BuildConfig
import nart.simpleanki.R
import nart.simpleanki.auth.AuthRepository
import nart.simpleanki.auth.AuthViewModel
import nart.simpleanki.auth.FirebaseAuthRepository
import nart.simpleanki.auth.GoogleSignInClient
import nart.simpleanki.core.analytics.FirebaseAnalyticsService
import nart.simpleanki.core.analytics.FirebaseCrashlyticsService
import nart.simpleanki.core.analytics.LogManager
import nart.simpleanki.core.analytics.LogcatService
import nart.simpleanki.core.apkg.ApkgFormatDetector
import nart.simpleanki.core.apkg.ApkgImportService
import nart.simpleanki.core.apkg.ApkgMediaReader
import nart.simpleanki.core.apkg.ApkgUnzipper
import nart.simpleanki.core.apkg.DefaultApkgImportService
import nart.simpleanki.core.billing.EntitlementCache
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.PlayBillingRepository
import nart.simpleanki.core.csv.CsvImportService
import nart.simpleanki.core.csv.DefaultCsvImportService
import nart.simpleanki.core.data.local.AzriDatabase
import nart.simpleanki.core.data.local.MIGRATION_1_2
import nart.simpleanki.core.data.local.MIGRATION_2_3
import nart.simpleanki.core.data.local.MIGRATION_3_4
import nart.simpleanki.core.data.media.FirebaseMediaRepository
import nart.simpleanki.core.data.media.LocalMediaStore
import nart.simpleanki.core.data.media.MediaManager
import nart.simpleanki.core.data.media.MediaUploader
import nart.simpleanki.core.data.repository.CardRepository
import nart.simpleanki.core.data.repository.DeckRepository
import nart.simpleanki.core.data.repository.FolderRepository
import nart.simpleanki.core.data.repository.ReviewLogRepository
import nart.simpleanki.core.data.repository.StreakProvider
import nart.simpleanki.core.data.repository.StreakStateManager
import nart.simpleanki.core.data.repository.StreakStateRepository
import nart.simpleanki.core.data.repository.TypingLogRepository
import nart.simpleanki.core.data.repository.TypingMasteryProvider
import nart.simpleanki.core.data.settings.DataStoreSettingsRepository
import nart.simpleanki.core.data.settings.SettingsRepository
import nart.simpleanki.core.data.sync.FirestoreSyncService
import nart.simpleanki.core.data.sync.RemoteSyncSource
import nart.simpleanki.core.data.sync.SyncManager
import nart.simpleanki.core.notifications.Notifier
import nart.simpleanki.core.notifications.ReminderScheduler
import nart.simpleanki.core.notifications.WorkManagerReminderScheduler
import nart.simpleanki.feature.apkgimport.ApkgImportViewModel
import nart.simpleanki.feature.cardform.CardFormViewModel
import nart.simpleanki.feature.csvimport.CsvImportViewModel
import nart.simpleanki.feature.deckdetail.DeckDetailViewModel
import nart.simpleanki.feature.decksettings.DeckEditViewModel
import nart.simpleanki.feature.folderdetail.FolderDetailViewModel
import nart.simpleanki.feature.library.FolderEditViewModel
import nart.simpleanki.feature.library.LibraryViewModel
import nart.simpleanki.feature.notifications.NotificationsViewModel
import nart.simpleanki.feature.paywall.PaywallViewModel
import nart.simpleanki.feature.profile.ProfileViewModel
import nart.simpleanki.feature.queue.DailyGoalViewModel
import nart.simpleanki.feature.queue.StudyQueueViewModel
import nart.simpleanki.feature.settings.SettingsViewModel
import nart.simpleanki.feature.review.ReviewViewModel
import nart.simpleanki.feature.study.StudyViewModel
import nart.simpleanki.feature.typepractice.TypePracticeViewModel
import nart.simpleanki.feature.sync.SyncViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.File

/** Injection args for screens that take optional ids (unambiguous vs. positional params). */
data class CardFormArgs(val deckId: String? = null, val cardId: String? = null)
data class DeckEditArgs(val deckId: String? = null, val folderId: String? = null)
data class FolderEditArgs(val folderId: String? = null)
data class StudyArgs(val deckId: String? = null, val folderId: String? = null)

/** Root Koin module. */
val appModule = module {
    // Firebase
    single<FirebaseAuth> { Firebase.auth }
    single<FirebaseFirestore> { Firebase.firestore }
    single<FirebaseStorage> { Firebase.storage }
    single<FirebaseAnalytics> { Firebase.analytics }
    single<FirebaseCrashlytics> { Firebase.crashlytics }
    single {
        val analytics: FirebaseAnalytics = get()
        val crashlytics: FirebaseCrashlytics = get()
        // Keep dev traffic out of production dashboards; Logcat still shows everything locally.
        analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        LogManager(
            listOf(
                LogcatService(),
                FirebaseAnalyticsService(analytics),
                FirebaseCrashlyticsService(crashlytics),
            ),
        )
    }
    single<MediaUploader> { FirebaseMediaRepository(get(), get()) }
    single { LocalMediaStore(File(androidContext().filesDir, "media")) }
    single { MediaManager(get(), get()) }
    single<ApkgImportService> {
        DefaultApkgImportService(
            unzipper = ApkgUnzipper(),
            detector = ApkgFormatDetector(),
            mediaReader = ApkgMediaReader(),
            media = get(),
            deckRepository = get(),
            cardRepository = get(),
            appContext = androidContext(),
        )
    }
    viewModel { (deckName: String) -> ApkgImportViewModel(service = get(), deckName = deckName) }
    single<CsvImportService> {
        DefaultCsvImportService(
            deckRepository = get(),
            cardRepository = get(),
            appContext = androidContext(),
        )
    }
    viewModel { (deckName: String) -> CsvImportViewModel(service = get(), deckName = deckName) }

    // Auth
    single<AuthRepository> { FirebaseAuthRepository(get()) }
    // Web client ID (OAuth client_type 3) is generated from each developer's own
    // google-services.json by the google-services plugin — never hard-coded.
    single { GoogleSignInClient(serverClientId = androidContext().getString(R.string.default_web_client_id)) }
    viewModel { AuthViewModel(get(), get()) }

    // Local persistence (Room)
    single {
        Room.databaseBuilder(androidContext(), AzriDatabase::class.java, "azri.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }
    single { get<AzriDatabase>().folderDao() }
    single { get<AzriDatabase>().deckDao() }
    single { get<AzriDatabase>().cardDao() }
    single { get<AzriDatabase>().reviewLogDao() }
    single { get<AzriDatabase>().streakStateDao() }
    single { get<AzriDatabase>().typingLogDao() }

    // Repositories
    single { FolderRepository(get()) }
    single { DeckRepository(get()) }
    single { CardRepository(get()) }
    single { ReviewLogRepository(get()) }
    single { TypingLogRepository(get()) }
    single { TypingMasteryProvider(get(), get()) }
    single { StreakStateRepository(get()) }
    single { StreakProvider(get(), get()) }
    single { StreakStateManager(get(), get()) }

    // Sync
    single<RemoteSyncSource> { FirestoreSyncService(get()) }
    single { SyncManager(get(), get(), get(), get(), get(), get(), get(), get()) }

    // Billing / entitlement
    single { EntitlementCache(androidContext()) }
    single<EntitlementRepository> { PlayBillingRepository(androidContext(), get()) }

    // Settings
    single<SettingsRepository> { DataStoreSettingsRepository(androidContext()) }
    single { Notifier(androidContext()) }
    single<ReminderScheduler> { WorkManagerReminderScheduler(androidContext()) }

    // Feature ViewModels
    viewModel { SyncViewModel(get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel {
        ProfileViewModel(
            settingsRepository = get(),
            authRepository = get(),
            entitlementRepository = get()
        )
    }
    viewModel { LibraryViewModel(get(), get(), get()) }
    viewModel { params ->
        DeckDetailViewModel(
            deckId = params.get(),
            cardRepository = get(),
            deckRepository = get()
        )
    }
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
            reviewLogRepository = get(),
            streakStateRepository = get(),
            streakStateManager = get(),
            streakProvider = get(),
            logManager = get(),
        )
    }
    viewModel { params ->
        val args = params.get<StudyArgs>()
        ReviewViewModel(
            deckId = args.deckId,
            folderId = args.folderId,
            cardRepository = get(),
            deckRepository = get(),
            logManager = get(),
        )
    }
    viewModel { params ->
        val args = params.get<StudyArgs>()
        // Type Practice is deck-level only (v1) — args.folderId is intentionally unused.
        TypePracticeViewModel(
            deckId = args.deckId,
            cardRepository = get(),
            deckRepository = get(),
            typingLogRepository = get(),
            logManager = get(),
        )
    }
    viewModel {
        StudyQueueViewModel(
            cardRepository = get(),
            deckRepository = get(),
            folderRepository = get(),
            settingsRepository = get(),
            entitlementRepository = get(),
            streakProvider = get(),
            streakStateRepository = get(),
            streakStateManager = get(),
        )
    }
    viewModel { DailyGoalViewModel(settingsRepository = get()) }
    viewModel { NotificationsViewModel(settingsRepository = get(), scheduler = get()) }
    viewModel { PaywallViewModel(get()) }
    viewModel { params ->
        val a = params.get<CardFormArgs>()
        CardFormViewModel(
            deckId = a.deckId,
            cardRepository = get(),
            mediaManager = get(),
            deckRepository = get(),
            editingCardId = a.cardId,
            logManager = get(),
        )
    }
    viewModel { params ->
        val a = params.get<DeckEditArgs>()
        DeckEditViewModel(
            deckRepository = get(),
            folderRepository = get(),
            cardRepository = get(),
            editingDeckId = a.deckId,
            initialFolderId = a.folderId
        )
    }
    viewModel { params ->
        val a = params.get<FolderEditArgs>()
        FolderEditViewModel(
            folderRepository = get(),
            deckRepository = get(),
            editingFolderId = a.folderId
        )
    }
}
