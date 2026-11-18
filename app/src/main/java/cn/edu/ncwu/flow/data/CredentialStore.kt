package cn.edu.ncwu.flow.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class LoginMode { MANUAL, STORED }

data class CredentialSettings(
    val mode: LoginMode = LoginMode.MANUAL,
    val username: String = "",
    val hasStoredPassword: Boolean = false,
    val lastStatus: String = "",
)

internal data class StoredCredentials(val username: String, val password: String)

class CredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun settings(): CredentialSettings = CredentialSettings(
        mode = runCatching { LoginMode.valueOf(preferences.getString(KEY_MODE, null) ?: LoginMode.MANUAL.name) }
            .getOrDefault(LoginMode.MANUAL),
        username = preferences.getString(KEY_USERNAME, "").orEmpty(),
        hasStoredPassword = preferences.contains(KEY_PASSWORD) && preferences.contains(KEY_IV),
        lastStatus = preferences.getString(KEY_LAST_STATUS, "").orEmpty(),
    )

    fun save(username: String, password: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        preferences.edit()
            .putString(KEY_MODE, LoginMode.STORED.name)
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_LAST_STATUS, "凭证已安全保存在本机")
            .apply()
    }

    fun useManualMode() {
        preferences.edit().putString(KEY_MODE, LoginMode.MANUAL.name).apply()
    }

    fun useStoredMode() {
        if (preferences.contains(KEY_PASSWORD)) {
            preferences.edit().putString(KEY_MODE, LoginMode.STORED.name).apply()
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    internal fun credentials(): StoredCredentials? {
        if (settings().mode != LoginMode.STORED) return null
        val username = preferences.getString(KEY_USERNAME, "").orEmpty()
        val encrypted = preferences.getString(KEY_PASSWORD, null) ?: return null
        val iv = preferences.getString(KEY_IV, null) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            StoredCredentials(
                username,
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).toString(Charsets.UTF_8),
            )
        }.getOrNull()
    }

    internal fun recordStatus(status: String) {
        preferences.edit().putString(KEY_LAST_STATUS, status).apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFERENCES = "secure_campus_login"
        private const val KEY_ALIAS = "ncwu_flow_login_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_MODE = "mode"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_IV = "iv"
        private const val KEY_LAST_STATUS = "last_status"
    }
}
