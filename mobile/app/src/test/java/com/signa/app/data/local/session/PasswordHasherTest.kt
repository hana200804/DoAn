package com.signa.app.data.local.session

import org.junit.Assert.*
import org.junit.Test

class PasswordHasherTest {
    @Test fun saltedHashesMatchOnlyTheOriginalPassword() {
        val hasher = PasswordHasher()
        val firstSalt = hasher.newSalt()
        val secondSalt = hasher.newSalt()
        val hash = hasher.hash("sample-password", firstSalt)
        assertNotEquals(hash, hasher.hash("sample-password", secondSalt))
        assertTrue(hasher.matches("sample-password", firstSalt, hash))
        assertFalse(hasher.matches("different-password", firstSalt, hash))
    }
}
