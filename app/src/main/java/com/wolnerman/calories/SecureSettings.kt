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

    /** Service en ligne choisi : groq (défaut v2.0), openrouter, openai ou gemini. */
    var provider: String
        get() = prefs.getString("provider", CloudProviders.GROQ) ?: CloudProviders.GROQ
        set(v) { prefs.edit().putString("provider", v).apply() }

    /** Une entrée chiffrée par service (« gemini_key » et « openai_key » existaient en v1.7–1.9). */
    private fun pref(p: String) = "${p}_key"

    /** Résultat de la migration v2.0 (affiché une fois dans Profil), null si rien n'a bougé. */
    var migrationNote: String?
        get() = prefs.getString("migration_note", null)
        set(v) { prefs.edit().apply { if (v == null) remove("migration_note") else putString("migration_note", v) }.apply() }

    /**
     * Migration v2.0 (une seule fois) : une clé rangée dans le mauvais emplacement (ex. clé Groq « gsk_… »
     * collée dans le champ Gemini en v1.8) est déplacée vers le bon service, qui devient le service choisi.
     */
    fun migrateV2() {
        if (prefs.getBoolean("migrated_v2", false)) return
        val ids = listOf(CloudProviders.GEMINI, CloudProviders.OPENAI, CloudProviders.GROQ, CloudProviders.OPENROUTER)
        val keys = ids.associateWith { id -> if (hasKey(id)) loadKey(id) else null }
        val plan = planMigration(keys, prefs.getString("provider", null))
        plan.keys.forEach { (id, k) -> if (k == null) clearKey(id) else if (k != keys[id]) saveKey(k, id) }
        provider = plan.provider
        migrationNote = plan.note
        prefs.edit().putBoolean("migrated_v2", true).apply()
    }

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
        const val PROVIDER_OPENAI = CloudProviders.OPENAI
        const val PROVIDER_GEMINI = CloudProviders.GEMINI

        data class Plan(val keys: Map<String, String?>, val provider: String, val note: String?)

        /**
         * Calcul pur de la migration (testable) : [keys] = clé déchiffrée par emplacement, [savedProvider] = choix
         * enregistré (null si jamais choisi). Une clé dont le préfixe désigne un autre service est déplacée si
         * l'emplacement cible est vide. Le service choisi devient celui de la clé déplacée (Groq en priorité),
         * sinon le choix enregistré s'il a une clé, sinon Groq.
         */
        fun planMigration(keys: Map<String, String?>, savedProvider: String?): Plan {
            val out = keys.toMutableMap()
            val moved = ArrayList<String>()
            for ((slot, k) in keys) {
                if (k.isNullOrBlank()) continue
                val target = CloudProviders.detect(k) ?: continue
                if (target == slot || !out[target].isNullOrBlank()) continue
                out[target] = k; out[slot] = null; moved += target
            }
            val order = listOf(CloudProviders.GROQ, CloudProviders.OPENROUTER, CloudProviders.OPENAI, CloudProviders.GEMINI)
            val provider = moved.minByOrNull { order.indexOf(it) }
                ?: savedProvider?.takeIf { !out[it].isNullOrBlank() }
                ?: CloudProviders.GROQ
            val note = if (moved.isEmpty()) null else moved.joinToString(", ") { id ->
                "Ta clé ${CloudProviders.nameOf(id)} était rangée au mauvais endroit : elle a été déplacée vers ${CloudProviders.nameOf(id)}."
            } + " Service choisi : ${CloudProviders.nameOf(provider)}."
            return Plan(out, provider, note)
        }
    }
}
