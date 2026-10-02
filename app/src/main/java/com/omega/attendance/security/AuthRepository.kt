package com.omega.attendance.security

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityStore by preferencesDataStore("omega_security")
enum class UserRole { ADMIN, OPERATOR }
data class SecurityConfig(val hasAdminPin: Boolean = false, val autoLockMinutes: Int = 5)

object PinHasher {
    private const val ITERATIONS = 120_000
    fun create(pin: CharArray): Pair<String, String> { val salt = ByteArray(24).also(SecureRandom()::nextBytes); return Base64.getEncoder().encodeToString(salt) to hash(pin, salt) }
    fun verify(pin: CharArray, salt: String, expected: String): Boolean { val actual = hash(pin, Base64.getDecoder().decode(salt)); return java.security.MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray()) }
    private fun hash(pin: CharArray, salt: ByteArray): String = try { Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin, salt, ITERATIONS, 256)).encoded) } finally { pin.fill('\u0000') }
}

@Singleton
class AuthRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys { val hash = stringPreferencesKey("admin_pin_hash"); val salt = stringPreferencesKey("admin_pin_salt"); val timeout = intPreferencesKey("auto_lock_minutes") }
    val config = context.securityStore.data.map { SecurityConfig(it[Keys.hash] != null && it[Keys.salt] != null, it[Keys.timeout] ?: 5) }
    suspend fun setAdminPin(pin: String) { require(pin.length >= 4 && pin.all(Char::isDigit)); val (salt, hash) = PinHasher.create(pin.toCharArray()); context.securityStore.edit { it[Keys.salt] = salt; it[Keys.hash] = hash } }
    suspend fun verify(pin: String): Boolean { val values = context.securityStore.data.first(); val salt = values[Keys.salt] ?: return false; val expected = values[Keys.hash] ?: return false; return PinHasher.verify(pin.toCharArray(), salt, expected) }
}
