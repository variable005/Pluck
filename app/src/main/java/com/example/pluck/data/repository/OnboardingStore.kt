package com.example.pluck.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Keeps the small amount of first-run profile information private to this installation.
 *
 * The store deliberately lives outside the main settings repository so onboarding can remain
 * independent of account, provider, and generation settings. Its preference file is protected by
 * the Android Keystore-backed [EncryptedSharedPreferences] implementation.
 */
@Singleton
class OnboardingStore @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Emits whether the welcome flow has been completed for this app installation. */
    fun observeCompleted(): Flow<Boolean> = callbackFlow {
        fun current() = preferences.getBoolean(COMPLETED_KEY, false)
        trySend(current())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == COMPLETED_KEY) trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Emits the locally stored display name, or null when the user chose to skip it. */
    fun observePreferredName(): Flow<String?> = callbackFlow {
        fun current() = preferences.getString(PREFERRED_NAME_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        trySend(current())
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PREFERRED_NAME_KEY) trySend(current())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    /** Persists the optional name and marks the welcome flow as complete in one edit. */
    suspend fun complete(preferredName: String?) {
        preferences.edit()
            .putString(PREFERRED_NAME_KEY, preferredName?.trim()?.take(MAX_NAME_LENGTH)?.ifBlank { null })
            .putBoolean(COMPLETED_KEY, true)
            .apply()
    }

    private companion object {
        const val PREFERENCES_FILE = "pluck_onboarding"
        const val COMPLETED_KEY = "completed"
        const val PREFERRED_NAME_KEY = "preferred_name"
        const val MAX_NAME_LENGTH = 40
    }
}
