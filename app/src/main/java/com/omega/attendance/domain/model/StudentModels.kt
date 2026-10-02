package com.omega.attendance.domain.model

data class NewStudent(
    val studentNumber: String,
    val fullName: String,
    val gradeId: Long,
    val classId: Long,
    val sectionId: Long,
    val hallId: Long?,
    val academicYearId: Long,
    val parentPhone: String? = null,
    val notes: String? = null
)

data class ImportRow(
    val rowNumber: Int,
    val studentNumber: String,
    val fullName: String,
    val grade: String,
    val className: String,
    val section: String,
    val hall: String,
    val parentPhone: String
)

data class ImportIssue(val row: Int, val message: String)
data class ImportPreview(val total: Int, val valid: List<ImportRow>, val issues: List<ImportIssue>)
