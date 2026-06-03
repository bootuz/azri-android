package nart.simpleanki.core.billing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.premiumDataStore by preferencesDataStore("azri_premium")

/** Persists the last known entitlement so Premium survives offline launches. */
class EntitlementCache(private val context: Context) {
    private val tierKey = stringPreferencesKey("premium_tier")

    val cached: Flow<Entitlement> = context.premiumDataStore.data.map { prefs ->
        val tier = prefs[tierKey]?.let { runCatching { PremiumTier.valueOf(it) }.getOrNull() } ?: PremiumTier.None
        Entitlement(tier)
    }

    suspend fun save(entitlement: Entitlement) {
        context.premiumDataStore.edit { it[tierKey] = entitlement.tier.name }
    }
}
