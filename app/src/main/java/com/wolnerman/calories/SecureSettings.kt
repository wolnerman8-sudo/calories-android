package com.wolnerman.calories

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Réglages de la reconnaissance photo. La clé API Gemini est chiffrée en AES-256-GCM avec une clé
 * conservée dans l'Android Keystore (non exportable) ; seul le texte chiffré est stocké dans les préférences.
 */
class SecureSettings(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("reco_settings", Context.MODE_PRIVATE)

    var recognitionEnabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(v) { prefs.edit().putBoolean("enabled", v).apply() }

    /** Partage du journal avec Santé Connect (v1.8), désactivé par défaut. */
    var healthEnabled: Boolean
        get() = prefs.getBoolean("health_enabled", false)
        set(v) { prefs.edit().putBoolean("health_enabled", v).apply() }

    /** Fournisseur en ligne choisi : « openai » (défaut, v1.9) ou « gemini ». */
    var provider: String
        get() = prefs.getString("provider", PROVIDER_OPENAI) ?: PROVIDER_OPENAI
        set(v) { prefs.edit().putString("provider", v).apply() }

    private fun pref(p: String) = if (p == PROVIDER_GEMINI) "gemini_key" else "openai_key"

    fun hasKey(p: String = provider): Boolean = prefs.contains(pref(p))

    fun saveKey(key: String, p: String = provider) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secret())
        val enc = c.doFinal(key.trim().toByteArray(Charsets.UTF_8))
        val blob = c.iv + enc
        prefs.edit().putString(pref(p), Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun loadKey(p: String = provider): String? = try {
        prefs.getString(pref(p), null)?.let { s ->
            val blob = Base64.decode(s, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, blob, 0, 12))
            String(c.doFinal(blob, 12, blob.size - 12), Charsets.UTF_8)
        }
    } catch (e: Exception) { null } // clé Keystore perdue (restauration…) : il faudra la recoller

    /** 4 derniers caractères pour l'affichage (« ••••abcd »). */
    fun keyHint(p: String = provider): String? = loadKey(p)?.takeLast(4)?.let { "••••$it" }

    fun clearKey(p: String = provider) { prefs.edit().remove(pref(p)).apply() }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return gen.generateKey()
    }

    companion object {
        private const val ALIAS = "calories_gemini_key" // nom historique (v1.7), sert pour toutes les clés
        const val PROVIDER_OPENAI = "openai"
        const val PROVIDER_GEMINI = "gemini"
    }
}
