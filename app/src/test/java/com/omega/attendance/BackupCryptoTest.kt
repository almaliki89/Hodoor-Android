package com.omega.attendance

import com.omega.attendance.backup.BackupCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class BackupCryptoTest {
    private val password = "a-long-private-passphrase"

    @Test fun encryptedBackupRoundTripsWithoutPlaintextHeader() = runBlocking {
        val original = "SQLite format 3\u0000 plus private data".toByteArray()
        val encrypted = ByteArrayOutputStream()
        BackupCrypto.encrypt(encrypted, password) { it.write(original) }
        val bytes = encrypted.toByteArray()
        assertTrue(BackupCrypto.isEncrypted(bytes.copyOf(8)))
        assertTrue(!bytes.copyOfRange(0, 16).contentEquals(original.copyOfRange(0, 16)))
        val decrypted = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(bytes), password, decrypted)
        assertArrayEquals(original, decrypted.toByteArray())
    }

    @Test fun wrongPasswordAndTamperingAreRejected() = runBlocking {
        val encrypted = ByteArrayOutputStream()
        BackupCrypto.encrypt(encrypted, password) { it.write("secret".toByteArray()) }
        val bytes = encrypted.toByteArray()
        assertTrue(runCatching {
            BackupCrypto.decrypt(ByteArrayInputStream(bytes), "different-passphrase", ByteArrayOutputStream())
        }.isFailure)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching {
            BackupCrypto.decrypt(ByteArrayInputStream(bytes), password, ByteArrayOutputStream())
        }.isFailure)
    }
}
