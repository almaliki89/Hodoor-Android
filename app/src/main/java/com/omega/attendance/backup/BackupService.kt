package com.omega.attendance.backup

import android.content.Context
import android.net.Uri
import com.omega.attendance.BuildConfig
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.settings.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.time.Instant
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

data class BackupInfo(val appVersion: String, val schemaVersion: Int, val createdAt: String, val schoolName: String, val databaseBytes: Long, val encrypted: Boolean = true)

@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OmegaDatabase,
    private val settings: SettingsRepository
) {
    suspend fun create(uri: Uri, password: String): BackupInfo = withContext(Dispatchers.IO) {
        val info = context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            BackupCrypto.encrypt(output, password) { prepareBackup(it) }
        } ?: error("تعذر فتح ملف النسخة الاحتياطية")
        require(context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 128 } == true)
        val (plain, encrypted) = materialize(uri, password)
        try {
            require(encrypted)
            validate(plain, encrypted)
            info
        } finally {
            plain.delete()
        }
    }

    suspend fun inspect(uri: Uri, password: String): BackupInfo = withContext(Dispatchers.IO) {
        val (plain, encrypted) = materialize(uri, password)
        try { validate(plain, encrypted) } finally { plain.delete() }
    }

    suspend fun restore(uri: Uri, password: String): BackupInfo = withContext(Dispatchers.IO) {
        val (plain, encrypted) = materialize(uri, password)
        try { restorePlain(plain, encrypted, password) } finally { plain.delete() }
    }

    private suspend fun restorePlain(plain: File, encrypted: Boolean, password: String): BackupInfo {
        val info = validate(plain, encrypted)
        require(info.schemaVersion in 1..4) { "إصدار قاعدة البيانات في النسخة الاحتياطية غير مدعوم" }
        val restoreDir = File(context.cacheDir, "restore-staging").apply { mkdirs() }
        val incoming = File(restoreDir, "incoming.sqlite"); val settingsJson = arrayOfNulls<String>(1)
        try {
        plain.inputStream().use { input -> ZipInputStream(BufferedInputStream(input)).use { zip -> var entry = zip.nextEntry; while (entry != null) { when (entry.name) { "database.sqlite" -> incoming.outputStream().use(zip::copyTo); "settings.json" -> settingsJson[0] = zip.bufferedReader().readText() }; entry = zip.nextEntry } } }
        require(validSqlite(incoming)) { "قاعدة البيانات داخل النسخة غير صالحة" }
        val safety = File(context.cacheDir, "pre-restore-safety.omega")
        safety.outputStream().use { output ->
            BackupCrypto.encrypt(output, password) { prepareBackup(it) }
        }
        val (safetyPlain, safetyEncrypted) = materialize(Uri.fromFile(safety), password)
        try {
            require(safetyEncrypted)
            validate(safetyPlain, true)
        } finally {
            safetyPlain.delete()
        }
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close(); database.close()
        val target = context.getDatabasePath("omega_attendance.db"); require(target.parentFile?.canonicalPath == context.getDatabasePath("x").parentFile?.canonicalPath)
        incoming.copyTo(target, overwrite = true); File(target.path + "-wal").delete(); File(target.path + "-shm").delete()
        settingsJson[0]?.let { settings.restore(parseSettings(it)) }; return info
        } finally {
            incoming.delete()
        }
    }

    private suspend fun prepareBackup(output: OutputStream): BackupInfo {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
        val dbFile = context.getDatabasePath("omega_attendance.db"); require(dbFile.exists() && dbFile.length() > 0)
        val prefs = settings.preferences.first(); val created = Instant.now().toString()
        val info = BackupInfo(BuildConfig.VERSION_NAME, 3, created, prefs.schoolName, dbFile.length())
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry("database.sqlite")); dbFile.inputStream().use { input -> input.copyTo(zip) }; zip.closeEntry()
            write(zip, "settings.json", settingsJson(prefs).toString())
            write(zip, "metadata.json", JSONObject().put("backupVersion", 1).put("appVersion", info.appVersion).put("schemaVersion", info.schemaVersion).put("createdAt", info.createdAt).put("schoolName", info.schoolName).put("databaseBytes", info.databaseBytes).toString())
        }
        return info
    }

    private fun materialize(uri: Uri, password: String): Pair<File, Boolean> {
        val plain = File.createTempFile("omega-restore-", ".zip", context.cacheDir)
        try {
            val encrypted = context.contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                input.mark(8)
                val prefix = ByteArray(8)
                val read = input.read(prefix)
                input.reset()
                if (read >= 2 && prefix[0] == 'P'.code.toByte() && prefix[1] == 'K'.code.toByte()) {
                    plain.outputStream().use(input::copyTo)
                    false
                } else {
                    try {
                        plain.outputStream().use { BackupCrypto.decrypt(input, password, it) }
                    } catch (error: Exception) {
                        throw IllegalArgumentException("كلمة المرور غير صحيحة أو ملف النسخة الاحتياطية تالف", error)
                    }
                    true
                }
            } ?: error("تعذر قراءة ملف النسخة الاحتياطية")
            return plain to encrypted
        } catch (error: Exception) {
            plain.delete()
            throw error
        }
    }

    private fun validate(file: File, encrypted: Boolean): BackupInfo {
        var metadata: JSONObject? = null; var dbHeader: ByteArray? = null; var dbSize = 0L
        file.inputStream().use { input -> ZipInputStream(BufferedInputStream(input)).use { zip -> var entry = zip.nextEntry; while (entry != null) { when (entry.name) { "metadata.json" -> metadata = JSONObject(zip.bufferedReader().readText()); "database.sqlite" -> { dbHeader = ByteArray(16); var read = 0; while (read < 16) { val n = zip.read(dbHeader!!, read, 16 - read); if (n < 0) break; read += n }; dbSize = entry.size } }; entry = zip.nextEntry } } }
        val meta = requireNotNull(metadata) { "بيانات النسخة الاحتياطية مفقودة" }; require(dbHeader?.toString(Charsets.US_ASCII) == "SQLite format 3\u0000") { "ملف قاعدة البيانات غير صالح" }
        return BackupInfo(meta.getString("appVersion"), meta.getInt("schemaVersion"), meta.getString("createdAt"), meta.optString("schoolName"), meta.optLong("databaseBytes", dbSize), encrypted)
    }

    private fun validSqlite(file: File): Boolean = file.exists() && file.length() > 100 && file.inputStream().use { input -> ByteArray(16).also { input.read(it) }.toString(Charsets.US_ASCII) == "SQLite format 3\u0000" }
    private fun write(zip: ZipOutputStream, name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
    private fun settingsJson(p: AppPreferences) = JSONObject().put("schoolName", p.schoolName).put("startMinutes", p.startMinutes).put("lateMinutes", p.lateMinutes).put("scannerCooldown", p.scannerCooldownMillis).put("sound", p.soundEnabled).put("vibration", p.vibrationEnabled).put("activeYear", p.activeAcademicYearId).put("theme", p.theme)
    private fun parseSettings(value: String): AppPreferences { val o = JSONObject(value); return AppPreferences(o.optString("schoolName", "مدرستي"), o.optInt("startMinutes", 450), o.optInt("lateMinutes", 465), o.optLong("scannerCooldown", 900), o.optBoolean("sound", true), o.optBoolean("vibration", true), o.optLong("activeYear", 0), o.optString("theme", "SYSTEM")) }
}
