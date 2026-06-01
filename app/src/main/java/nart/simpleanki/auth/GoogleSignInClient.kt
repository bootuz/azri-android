package nart.simpleanki.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Wraps Credential Manager to obtain a Google ID token. [serverClientId] is the
 * Firebase Web client ID (OAuth client_type 3). Requires an Activity context.
 */
class GoogleSignInClient(
    private val serverClientId: String,
) {
    suspend fun getIdToken(activityContext: Context): Result<String> = runCatching {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val credentialManager = CredentialManager.create(activityContext)
        val response = credentialManager.getCredential(activityContext, request)
        val credential = response.credential
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        googleCredential.idToken
    }
}
