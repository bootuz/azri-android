package nart.simpleanki.core.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import nart.simpleanki.core.billing.EntitlementRepository
import nart.simpleanki.core.billing.Entitlements
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Background sync worker: runs the two-way [SyncManager] for the signed-in user when
 * the network is available, even if the app isn't foregrounded. Complements the 20s
 * foreground sync. Dependencies are resolved from Koin (no WorkManager DI plugin needed).
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {

    private val syncManager: SyncManager by inject()
    private val auth: FirebaseAuth by inject()
    private val entitlements: EntitlementRepository by inject()

    override suspend fun doWork(): Result {
        val user = auth.currentUser ?: return Result.success()
        val signedInWithGoogle = !user.isAnonymous
        if (!Entitlements.shouldSync(entitlements.entitlement.value.isPremium, signedInWithGoogle)) {
            return Result.success() // free tier: nothing to sync
        }
        return runCatching { syncManager.sync(user.uid) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val WORK_NAME = "azri_periodic_sync"

        /** Schedules a periodic (~15 min) background sync; safe to call on every app start. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
