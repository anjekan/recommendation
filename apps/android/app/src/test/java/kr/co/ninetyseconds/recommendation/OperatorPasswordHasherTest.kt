package kr.co.ninetyseconds.recommendation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorPasswordHasherTest {
    @Test
    fun `default operator username is admin`() {
        assertEquals("admin", OperatorCredentialsStore.DEFAULT_USERNAME)
    }

    @Test
    fun `accepts the correct password and rejects a wrong one`() {
        val salt = ByteArray(16) { it.toByte() }
        val hash = OperatorPasswordHasher.derive("new-password-123", salt)

        assertEquals(32, hash.size)
        assertTrue(OperatorPasswordHasher.matches("new-password-123", salt, hash))
        assertFalse(OperatorPasswordHasher.matches("wrong-password", salt, hash))
    }

    @Test
    fun `same password has a different hash with another salt`() {
        val first = OperatorPasswordHasher.derive("new-password-123", ByteArray(16))
        val second = OperatorPasswordHasher.derive("new-password-123", ByteArray(16) { 1 })

        assertFalse(first.contentEquals(second))
    }
}
