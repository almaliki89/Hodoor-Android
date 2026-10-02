package com.omega.attendance.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `school_staff` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `fullName` TEXT NOT NULL, `jobTitle` TEXT NOT NULL, `phone` TEXT, `notes` TEXT, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_school_staff_uuid` ON `school_staff` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_school_staff_fullName` ON `school_staff` (`fullName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_school_staff_jobTitle` ON `school_staff` (`jobTitle`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_school_staff_status` ON `school_staff` (`status`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `school_staff_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `qrValue` TEXT NOT NULL, `fullName` TEXT NOT NULL, `jobTitle` TEXT NOT NULL, `phone` TEXT, `notes` TEXT, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("INSERT INTO `school_staff_new` (`id`, `uuid`, `qrValue`, `fullName`, `jobTitle`, `phone`, `notes`, `status`, `createdAt`, `updatedAt`) SELECT `id`, `uuid`, 'STF:' || `uuid`, `fullName`, `jobTitle`, `phone`, `notes`, `status`, `createdAt`, `updatedAt` FROM `school_staff`")
        db.execSQL("DROP TABLE `school_staff`")
        db.execSQL("ALTER TABLE `school_staff_new` RENAME TO `school_staff`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_school_staff_uuid` ON `school_staff` (`uuid`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_school_staff_qrValue` ON `school_staff` (`qrValue`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_school_staff_fullName` ON `school_staff` (`fullName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_school_staff_jobTitle` ON `school_staff` (`jobTitle`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_school_staff_status` ON `school_staff` (`status`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `staff_attendance` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `staffId` INTEGER NOT NULL, `staffNameSnapshot` TEXT NOT NULL, `jobTitleSnapshot` TEXT NOT NULL, `attendanceDate` INTEGER NOT NULL, `attendanceTime` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `status` TEXT NOT NULL, `checkInType` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, FOREIGN KEY(`staffId`) REFERENCES `school_staff`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_attendance_staffId_attendanceDate` ON `staff_attendance` (`staffId`, `attendanceDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_attendance_staffId` ON `staff_attendance` (`staffId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_attendance_attendanceDate` ON `staff_attendance` (`attendanceDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_attendance_status` ON `staff_attendance` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_attendance_timestamp` ON `staff_attendance` (`timestamp`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `school_staff` ADD COLUMN `specialty` TEXT")
    }
}
