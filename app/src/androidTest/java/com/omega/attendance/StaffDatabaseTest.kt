package com.omega.attendance

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.omega.attendance.data.local.database.MIGRATION_1_2
import com.omega.attendance.data.local.database.MIGRATION_2_3
import com.omega.attendance.data.local.database.MIGRATION_3_4
import com.omega.attendance.data.local.database.OmegaDatabase
import com.omega.attendance.data.importer.StaffImportRow
import com.omega.attendance.data.importer.StaffDocxParser
import com.omega.attendance.data.repository.StaffRepository
import com.omega.attendance.data.settings.SettingsRepository
import com.omega.attendance.domain.usecase.RecordManualStaffAttendanceUseCase
import com.omega.attendance.domain.usecase.RegisterAttendanceUseCase
import com.omega.attendance.domain.usecase.RegistrationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class StaffDatabaseTest {
    @get:Rule val migration = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OmegaDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    @Test fun migrationPreservesExistingSettingsAndCreatesStaffTable() {
        val original = migration.createDatabase("staff-migration.db", 1)
        original.execSQL("INSERT INTO app_settings (`key`, `value`, `updatedAt`) VALUES ('test-existing', 'preserved', 1)")
        original.close()
        val migrated = migration.runMigrationsAndValidate("staff-migration.db", 2, true, MIGRATION_1_2)
        migrated.query("SELECT value FROM app_settings WHERE `key`='test-existing'").use { cursor -> Assert.assertTrue(cursor.moveToFirst()); Assert.assertEquals("preserved", cursor.getString(0)) }
        migrated.query("SELECT COUNT(*) FROM school_staff").use { cursor -> Assert.assertTrue(cursor.moveToFirst()); Assert.assertEquals(0, cursor.getInt(0)) }
        migrated.close()
    }

    @Test fun staffDocxParserWorksWithAndroidXmlProvider() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:tbl><w:tr><w:tc><w:p/></w:tc><w:tc><w:p><w:r><w:t>أحمد محمد علي حسن</w:t></w:r></w:p></w:tc><w:tc><w:p/></w:tc><w:tc><w:p><w:r><w:t>مدرس</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>رياضيات</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>"""
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val row = StaffDocxParser().parse(ByteArrayInputStream(bytes)).single()

        Assert.assertEquals("أحمد محمد علي حسن", row.fullName)
        Assert.assertEquals("رياضيات", row.specialty)
    }

    @Test fun staffCrudIsIndependentAndAudited() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OmegaDatabase::class.java).build()
        try {
            val repository = StaffRepository(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            val id = repository.add("مدير تجريبي", "المدير", "07700000000", null)
            Assert.assertEquals("المدير", repository.search("مدير").first().single().jobTitle)
            repository.edit(id, "موظف تجريبي", "موظف الخدمات", null, "ملاحظة")
            Assert.assertEquals("موظف الخدمات", repository.search("الخدمات").first().single().jobTitle)
            repository.archive(id, true)
            Assert.assertEquals("ARCHIVED", repository.search("موظف").first().single().status)
            repository.archive(id, false)
            repository.delete(id)
            Assert.assertTrue(repository.search("موظف").first().isEmpty())
            Assert.assertEquals(setOf("STAFF_DELETED", "STAFF_RESTORED", "STAFF_ARCHIVED", "STAFF_EDITED", "STAFF_CREATED"), db.auditDao().page(10, 0).map { it.action }.toSet())
        } finally { db.close() }
    }

    @Test fun allStaffCanBeDeletedInOneAuditedTransaction() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OmegaDatabase::class.java).build()
        try {
            val repository = StaffRepository(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            repository.add("موظف أول", "مدرس", null, null)
            repository.add("موظف ثان", "كاتب", null, null)

            val result = repository.deleteAll()

            Assert.assertEquals(2, result.people)
            Assert.assertTrue(db.staffDao().all().isEmpty())
            Assert.assertEquals("STAFF_DELETED_ALL", db.auditDao().page(1, 0).single().action)
        } finally { db.close() }
    }

    @Test fun migrationCreatesSeparateStaffQrAndAttendanceWithoutLosingStaff() {
        val original = migration.createDatabase("staff-migration-2-3.db", 2)
        original.execSQL("INSERT INTO school_staff (`uuid`, `fullName`, `jobTitle`, `status`, `createdAt`, `updatedAt`) VALUES ('staff-uuid', 'موظف محفوظ', 'مدرس', 'ACTIVE', 1, 1)")
        original.close()
        val migrated = migration.runMigrationsAndValidate("staff-migration-2-3.db", 3, true, MIGRATION_2_3)
        migrated.query("SELECT qrValue FROM school_staff WHERE uuid='staff-uuid'").use { cursor -> Assert.assertTrue(cursor.moveToFirst()); Assert.assertEquals("STF:staff-uuid", cursor.getString(0)) }
        migrated.query("SELECT COUNT(*) FROM staff_attendance").use { cursor -> Assert.assertTrue(cursor.moveToFirst()); Assert.assertEquals(0, cursor.getInt(0)) }
        migrated.close()
    }

    @Test fun migrationAddsSpecialtyWithoutLosingStaff() {
        val original = migration.createDatabase("staff-migration-3-4.db", 3)
        original.execSQL("INSERT INTO school_staff (`uuid`, `qrValue`, `fullName`, `jobTitle`, `status`, `createdAt`, `updatedAt`) VALUES ('staff-uuid-4', 'STF:staff-uuid-4', 'موظف محفوظ', 'مدرس', 'ACTIVE', 1, 1)")
        original.close()
        val migrated = migration.runMigrationsAndValidate("staff-migration-3-4.db", 4, true, MIGRATION_3_4)
        migrated.query("SELECT fullName, specialty FROM school_staff WHERE uuid='staff-uuid-4'").use { cursor ->
            Assert.assertTrue(cursor.moveToFirst())
            Assert.assertEquals("موظف محفوظ", cursor.getString(0))
            Assert.assertTrue(cursor.isNull(1))
        }
        migrated.close()
    }

    @Test fun docxImportUpdatesMatchingStaffAndDoesNotDuplicateRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OmegaDatabase::class.java).build()
        try {
            val repository = StaffRepository(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            repository.add("مصطفى تركي رميض", "مدرس", null, null)
            val rows = listOf(
                StaffImportRow(1, "مصطفى تركي رميض جبر", "مدرس", "انكليزي"),
                StaffImportRow(2, "أحمد محمد علي حسن", "مدرس", "رياضيات"),
                StaffImportRow(2, "أحمد محمد علي حسن", "مدرس", "رياضيات")
            )

            val result = repository.import(rows)

            Assert.assertEquals(1, result.added)
            Assert.assertEquals(1, result.updated)
            Assert.assertEquals(0, result.unchanged)
            Assert.assertEquals(2, db.staffDao().all().size)
            Assert.assertEquals("انكليزي", repository.search("مصطفى").first().single().specialty)
            Assert.assertEquals(1, db.auditDao().page(10, 0).count { it.action == "STAFF_IMPORTED" })
        } finally { db.close() }
    }

    @Test fun forgottenStaffCardCanBeRegisteredManuallyOnlyOnce() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OmegaDatabase::class.java).build()
        try {
            val repository = StaffRepository(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            val id = repository.add("موظف بلا بطاقة", "مدرس", null, null)
            val manual = RecordManualStaffAttendanceUseCase(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            Assert.assertTrue(manual(id))
            Assert.assertFalse(manual(id))
            Assert.assertTrue(repository.search("بلا بطاقة").first().single().qrValue.startsWith("STF:"))
            Assert.assertEquals("MANUAL", db.staffAttendanceDao().forDate(java.time.LocalDate.now().toEpochDay()).first().single().checkInType.name)
            val today = java.time.LocalDate.now().toEpochDay()
            Assert.assertEquals(1, repository.activeCount().first())
            Assert.assertEquals(1, repository.attendanceCount(today).first())
            Assert.assertEquals(1, repository.attendanceCount(today, com.omega.attendance.data.local.entity.AttendanceStatus.PRESENT).first())
            Assert.assertEquals(0, repository.attendanceCount(today, com.omega.attendance.data.local.entity.AttendanceStatus.LATE).first())
        } finally { db.close() }
    }

    @Test fun staffQrIsRoutedOnlyToStaffAttendance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, OmegaDatabase::class.java).build()
        try {
            val repository = StaffRepository(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            val id = repository.add("مدرس QR", "مدرس", null, null)
            val member = db.staffDao().byId(id)!!
            val register = RegisterAttendanceUseCase(db, db.studentDao(), db.attendanceDao(), db.staffDao(), db.staffAttendanceDao(), db.structureDao(), SettingsRepository(context))
            Assert.assertTrue(register(member.qrValue) is RegistrationResult.Success)
            Assert.assertEquals(RegistrationResult.Duplicate, register(member.qrValue))
            Assert.assertEquals(RegistrationResult.Unknown, register(member.qrValue.replace("STF:", "STD:")))
            Assert.assertEquals(id, db.staffAttendanceDao().forDate(java.time.LocalDate.now().toEpochDay()).first().single().staffId)
            Assert.assertTrue(db.attendanceDao().forDate(java.time.LocalDate.now().toEpochDay()).first().isEmpty())
        } finally { db.close() }
    }

    @Test fun staffReportsIncludeRegisteredAndAbsentActiveStaff() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OmegaDatabase::class.java).build()
        try {
            val repository = StaffRepository(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            val presentId = repository.add("مدرس حاضر", "مدرس", null, null)
            repository.add("مدير غائب", "مدير", null, null)
            val manual = RecordManualStaffAttendanceUseCase(db, db.staffDao(), db.staffAttendanceDao(), db.auditDao())
            Assert.assertTrue(manual(presentId))
            val today = java.time.LocalDate.now().toEpochDay()

            val daily = db.staffAttendanceDao().dailyReport(today, null, null).first()
            Assert.assertEquals(2, daily.size)
            Assert.assertEquals("PRESENT", daily.single { it.staffId == presentId }.status)
            Assert.assertEquals("ABSENT", daily.single { it.jobTitle == "مدير" }.status)
            Assert.assertEquals(1, db.staffAttendanceDao().dailyReport(today, "ABSENT", "مدير").first().size)

            val range = db.staffAttendanceDao().rangeReport(today, today, null).first()
            Assert.assertEquals(1, range.single { it.staffId == presentId }.present)
            Assert.assertEquals(0, range.single { it.jobTitle == "مدير" }.present)
        } finally { db.close() }
    }
}
