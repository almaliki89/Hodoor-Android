package com.omega.attendance

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.omega.attendance.backup.BackupCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class BackupCryptoAndroidTest {
    @Test fun encryptedBackupRoundTripsAndRejectsWrongPasswordOnAndroid() = runBlocking {
        val data = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        BackupCrypto.encrypt(encrypted, "school-private-passphrase") { it.write(data) }
        val bytes = encrypted.toByteArray()
        assertTrue(BackupCrypto.isEncrypted(bytes.copyOf(8)))
        val decrypted = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(bytes), "school-private-passphrase", decrypted)
        assertArrayEquals(data, decrypted.toByteArray())
        assertTrue(runCatching {
            BackupCrypto.decrypt(ByteArrayInputStream(bytes), "wrong-private-passphrase", ByteArrayOutputStream())
        }.isFailure)
    }
}
