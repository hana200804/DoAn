package com.signa.app.data.local.session

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

class PasswordHasher @Inject constructor() {
    private val random = SecureRandom()

    fun newSalt(): String = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))

    fun hash(value: String, salt: String): String {
        val spec = PBEKeySpec(value.toCharArray(), Base64.getDecoder().decode(salt), 120_000, 256)
        return try {
            Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally {
            spec.clearPassword()
        }
    }

    fun matches(value: String, salt: String, expectedHash: String): Boolean = MessageDigest.isEqual(
        Base64.getDecoder().decode(hash(value, salt)),
        Base64.getDecoder().decode(expectedHash)
    )
}
