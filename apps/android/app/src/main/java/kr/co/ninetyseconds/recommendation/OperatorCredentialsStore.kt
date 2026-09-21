package kr.co.ninetyseconds.recommendation

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal class OperatorCredentialsStore(context: Context) {
    private val preferences = context.getSharedPreferences("operator-credentials", Context.MODE_PRIVATE)

    fun verify(username: String, password: String): Boolean {
        if (username != DEFAULT_USERNAME) return false
        val salt = preferences.getString(SALT, null)
        val hash = preferences.getString(HASH, null)
        if (salt == null && hash == null) {
            return MessageDigest.isEqual(password.toByteArray(), DEFAULT_PASSWORD.toByteArray())
        }
        if (salt == null || hash == null) return false
        return runCatching {
            OperatorPasswordHasher.matches(
                password, Base64.decode(salt, Base64.NO_WRAP), Base64.decode(hash, Base64.NO_WRAP),
            )
        }.getOrDefault(false)
    }

    fun changePassword(currentPassword: String, newPassword: String): Boolean {
        if (!verify(DEFAULT_USERNAME, currentPassword) || newPassword.length < 8) return false
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val hash = OperatorPasswordHasher.derive(newPassword, salt)
        return preferences.edit()
            .putString(SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .commit()
    }

    companion object {
        const val DEFAULT_USERNAME = "adimin"
        private const val DEFAULT_PASSWORD = "admin"
        private const val SALT = "password-salt"
        private const val HASH = "password-hash"
    }
}

internal object OperatorPasswordHasher {
    fun matches(password: String, salt: ByteArray, expectedHash: ByteArray): Boolean =
        MessageDigest.isEqual(derive(password, salt), expectedHash)

    fun derive(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

}
