package com.lamahuissn.motoapp.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The session, kept on the phone.
 *
 * A token here can take money out of a household's hand, so it is not written to preferences in the
 * clear: it is encrypted with an AES key that lives in the Android keystore (hardware-backed where
 * the phone has it) and never leaves it. `allowBackup` is off in the manifest for the same reason —
 * this token must not outlive the phone it was issued to.
 *
 * The token is still revocable from the office, which is the control that matters when a phone is
 * lost: the panel sees the device by name and can delete it.
 */
class TokenVault(private val context: Context) {

    fun save(session: Session) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encrypt(SessionCodec.encode(session)))
            .apply()
    }

    fun load(): Session? {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return null

        return runCatching { SessionCodec.decode(decrypt(stored)) }.getOrNull()
    }

    fun clear() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // The IV travels with the ciphertext: GCM needs it to decrypt, and it is not a secret.
        return Base64.encodeToString(cipher.iv + body, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_LENGTH)
        val body = bytes.copyOfRange(IV_LENGTH, bytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))

        return String(cipher.doFinal(body), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )

        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "moto_session"
        const val KEY = "session"
        const val ALIAS = "motoapp.session"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}

/**
 * What is stored, as text — kept apart from the keystore so the part that can be WRONG is testable:
 * a keystore cannot run in a JVM unit test, but this can, and a session that fails to round-trip
 * signs the collector out every time he opens the app.
 */
object SessionCodec {

    fun encode(session: Session): String = JSONObject()
        .put("token", session.token)
        .put("id", session.user.id)
        .put("name", session.user.name)
        .put("username", session.user.username ?: JSONObject.NULL)
        .put("role", session.user.role)
        .put("locale", session.user.locale ?: JSONObject.NULL)
        .put("phone", session.user.phone ?: JSONObject.NULL)
        .toString()

    /** Null rather than a half-read session: a token with no user is not a session. */
    fun decode(text: String): Session? = runCatching {
        val row = JSONObject(text)
        val token = row.optString("token")
        if (token.isEmpty()) return@runCatching null

        Session(
            token = token,
            user = MotoUser(
                id = row.optInt("id"),
                name = row.optString("name"),
                username = row.optString("username").takeIf { it.isNotEmpty() && it != "null" },
                role = row.optString("role"),
                locale = row.optString("locale").takeIf { it.isNotEmpty() && it != "null" },
                phone = row.optString("phone").takeIf { it.isNotEmpty() && it != "null" },
            ),
        )
    }.getOrNull()
}
