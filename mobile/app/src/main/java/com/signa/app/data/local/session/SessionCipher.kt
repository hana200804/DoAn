package com.signa.app.data.local.session

interface SessionCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(payload: String): String
}
