package com.omega.attendance.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore("omega_settings")

data class AppPreferences(
    val schoolName: String = "مدرستي",
    val startMinutes: Int = 450,
    val lateMinutes: Int = 465,
    val scannerCooldownMillis: Long = 900,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val activeAcademicYearId: Long = 0,
    val theme: String = "SYSTEM"
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val schoolName = stringPreferencesKey("school_name")
        val startMinutes = intPreferencesKey("start_minutes")
        val lateMinutes = intPreferencesKey("late_minutes")
        val scannerCooldown = longPreferencesKey("scanner_cooldown")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val activeYear = longPreferencesKey("active_year")
        val theme = stringPreferencesKey("theme")
    }

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { values ->
        AppPreferences(
            schoolName = values[Keys.schoolName] ?: "مدرستي",
            startMinutes = values[Keys.startMinutes] ?: 450,
            lateMinutes = values[Keys.lateMinutes] ?: 465,
            scannerCooldownMillis = values[Keys.scannerCooldown] ?: 900,
            soundEnabled = values[Keys.sound] ?: true,
            vibrationEnabled = values[Keys.vibration] ?: true,
            activeAcademicYearId = values[Keys.activeYear] ?: 0,
            theme = values[Keys.theme] ?: "SYSTEM"
        )
    }

    suspend fun updateSchool(name: String, activeYearId: Long) = context.dataStore.edit {
        it[Keys.schoolName] = name.trim(); it[Keys.activeYear] = activeYearId
    }

    suspend fun updateAttendance(start: Int, late: Int, cooldown: Long, sound: Boolean, vibration: Boolean) = context.dataStore.edit {
        it[Keys.startMinutes] = start; it[Keys.lateMinutes] = late
        it[Keys.scannerCooldown] = cooldown.coerceIn(300, 5_000)
        it[Keys.sound] = sound; it[Keys.vibration] = vibration
    }

    suspend fun restore(value: AppPreferences) = context.dataStore.edit {
        it[Keys.schoolName] = value.schoolName; it[Keys.startMinutes] = value.startMinutes; it[Keys.lateMinutes] = value.lateMinutes
        it[Keys.scannerCooldown] = value.scannerCooldownMillis; it[Keys.sound] = value.soundEnabled; it[Keys.vibration] = value.vibrationEnabled
        it[Keys.activeYear] = value.activeAcademicYearId; it[Keys.theme] = value.theme
    }
}
