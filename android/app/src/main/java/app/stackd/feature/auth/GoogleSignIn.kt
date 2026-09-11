package app.stackd.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Google sign-in via Credential Manager, mirroring the web's "Continue with
 * Google" (which goes through Lovable's OAuth broker — different transport,
 * same Supabase user).
 *
 * The ID token this yields is exchanged for a Supabase session by
 * `AuthRepository.signInWithGoogle`. A raw nonce is generated per attempt and
 * its SHA-256 goes into the token request; Supabase verifies the raw nonce
 * against the token's hashed one, so a replayed token from another context is
 * rejected.
 */
object GoogleSignIn {

    data class Result(val idToken: String, val rawNonce: String)

    /** Null when the user dismissed the sheet or no credential was returned. */
    suspend fun request(context: Context, serverClientId: String): Result? {
        val rawNonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setNonce(hashedNonce)
            .build()

        val response = runCatching {
            CredentialManager.create(context).getCredential(
                context,
                GetCredentialRequest.Builder().addCredentialOption(option).build(),
            )
        }.getOrNull() ?: return null

        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        return Result(idToken = idToken, rawNonce = rawNonce)
    }
}
