package com.omega.attendance.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-protected backup container; the ZIP payload is never written unencrypted to shared storage. */
object BackupCrypto {
    private val magic = "OMEGABK2".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val ITERATIONS = 210_000

    fun isEncrypted(prefix: ByteArray): Boolean = prefix.contentEquals(magic)

    suspend fun <T> encrypt(output: OutputStream, password: String, writePlaintext: suspend (OutputStream) -> T): T {
        require(password.length >= 12) { "كلمة مرور النسخة الاحتياطية يجب أن تكون 12 حرفًا على الأقل" }
        val salt = ByteArray(SALT_SIZE).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_SIZE).also(SecureRandom()::nextBytes)
        val header = magic + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, nonce))
            updateAAD(header)
        }
        output.write(header)
        return CipherOutputStream(output, cipher).use { writePlaintext(it) }
    }

    fun decrypt(input: InputStream, password: String, output: OutputStream) {
        val header = ByteArray(magic.size + SALT_SIZE + NONCE_SIZE)
        readFully(input, header)
        require(isEncrypted(header.copyOf(magic.size))) { "صيغة النسخة الاحتياطية غير معروفة" }
        val salt = header.copyOfRange(magic.size, magic.size + SALT_SIZE)
        val nonce = header.copyOfRange(magic.size + SALT_SIZE, header.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, nonce))
            updateAAD(header)
        }
        CipherInputStream(input, cipher).use { it.copyTo(output) }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun readFully(input: InputStream, bytes: ByteArray) {
        var read = 0
        while (read < bytes.size) {
            val count = input.read(bytes, read, bytes.size - read)
            require(count > 0) { "ملف النسخة الاحتياطية غير مكتمل" }
            read += count
        }
    }
}
