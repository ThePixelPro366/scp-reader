package com.foundation.scpreader.data

import android.content.Context
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted, on-device storage for the GitHub personal access token used by the update checker.
 * Backed by Jetpack Security (AES256-GCM master key, AES256-SIV/GCM prefs) rather than the plain
 * DataStore that [SettingsStore] uses, since this value is a credential, not a preference. The
 * token is never logged.
 */
class SecureTokenStore(context: Context) {
    private val appContext = context.applicationContext

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var githubToken: String?
        get() = prefs.getString(KEY_GITHUB_TOKEN, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit {
            if (value.isNullOrBlank()) remove(KEY_GITHUB_TOKEN) else putString(KEY_GITHUB_TOKEN, value)
        }

    private companion object {
        const val KEY_GITHUB_TOKEN = "github_pat"
    }
}
