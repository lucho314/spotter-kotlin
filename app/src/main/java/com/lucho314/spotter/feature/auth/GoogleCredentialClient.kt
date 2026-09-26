package com.lucho314.spotter.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Outcome of a Credential Manager Google sign-in attempt. */
sealed interface GoogleIdResult {
    data class Success(val idToken: String) : GoogleIdResult
    data object Cancelled : GoogleIdResult
    data object NoCredential : GoogleIdResult
    data class Failure(val message: String?) : GoogleIdResult
}

/** Wraps Credential Manager's native Google Sign-In flow (ID token, no browser). */
class GoogleCredentialClient @Inject constructor() {

    suspend fun getIdToken(context: Context, serverClientId: String, hashedNonce: String): GoogleIdResult {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                GoogleIdResult.Success(googleIdTokenCredential.idToken)
            } else {
                GoogleIdResult.Failure("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleIdResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleIdResult.NoCredential
        } catch (e: GetCredentialException) {
            GoogleIdResult.Failure(e.message)
        } catch (e: GoogleIdTokenParsingException) {
            // Thrown by GoogleIdTokenCredential.createFrom; it is not a GetCredentialException.
            GoogleIdResult.Failure(e.message)
        }
    }
}

/**
 * Lets [LoginScreen] fetch the Hilt-provided [GoogleCredentialClient] without routing an Android
 * `Context` through [LoginViewModel] (ViewModels shouldn't hold one): the client is stateless and
 * only ever used with the `Context` the composable already has (`LocalContext.current`).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface GoogleCredentialClientEntryPoint {
    fun googleCredentialClient(): GoogleCredentialClient
}
