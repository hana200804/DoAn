package com.signa.app.data.fake

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeRepositoriesTest {
    @Test
    fun catalogUsesUniqueIdsAndUnknownIdReturnsNull() = runTest {
        val repository = FakeSignRepository()
        val signs = repository.observeSigns().first()
        assertEquals(signs.size, signs.map { it.id }.toSet().size)
        val sign = signs.single { it.speedLimitKmh == 60 }
        assertEquals(sign, repository.observeSign(sign.id).first())
        assertNull(repository.observeSign("missing-id").first())
    }
}
