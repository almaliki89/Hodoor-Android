package com.omega.attendance.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.omega.attendance.domain.model.ImportIssue
import com.omega.attendance.domain.model.ImportPreview
import com.omega.attendance.domain.model.ImportRow
import com.omega.attendance.domain.model.StaffJobTitles
import com.omega.attendance.export.excel.XlsxCodec
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

fun isStaffQrReady(jobTitle: String): Boolean = jobTitle.isNotBlank()

object RosterRowsParser {
    fun students(rows: List<List<String>>, existingNames: Set<String>): ImportPreview {
        val known = existingNames.mapTo(mutableSetOf()) { it.key() }
        val seen = mutableSetOf<String>()
        val valid = mutableListOf<ImportRow>()
        val issues = mutableListOf<ImportIssue>()
        names(rows).forEach { (number, name) ->
            val key = name.key()
            when {
                key in known -> issues += ImportIssue(number, "اسم الطالب موجود مسبقًا")
                !seen.add(key) -> issues += ImportIssue(number, "اسم الطالب مكرر داخل الملف")
                else -> valid += ImportRow(number, "", name, "", "", "", "", "")
            }
        }
        require(valid.isNotEmpty() || issues.isNotEmpty()) { "لم يتم العثور على أسماء طلبة قابلة للقراءة في الملف" }
        return ImportPreview(valid.size + issues.size, valid, issues)
    }

    fun names(rows: List<List<String>>): List<Pair<Int, String>> {
        val allHeaders = studentHeaders + staffNameHeaders
        val header = rows.indexOfFirst { row -> row.any { cell -> cell.key() in allHeaders } }
        val nameColumn = if (header >= 0) rows[header].indexOfFirst { it.key() in allHeaders } else -1
        val data = if (header >= 0) rows.drop(header + 1) else rows
        return data.mapIndexedNotNull { offset, row ->
            val source = when {
                nameColumn >= 0 -> row.getOrNull(nameColumn).orEmpty()
                row.size >= 2 && row.first().trim().toIntOrNull() != null -> row[1]
                row.size > 1 -> row.firstOrNull { it.cleanedName().looksLikeName() }.orEmpty()
                else -> {
                    val line = row.joinToString(" ").normalize()
                    val title = StaffJobTitles.values.sortedByDescending(String::length).firstOrNull { Regex("(^|\\s)${Regex.escape(it)}(\\s|$)").containsMatchIn(line) }
                    if (title == null) line else line.substringBefore(title)
                }
            }
            val name = source.cleanedName()
            if (!name.looksLikeName()) null else Pair((if (header >= 0) header + 2 else 1) + offset, name)
        }
    }

    fun staff(rows: List<List<String>>): List<StaffImportRow> {
        val header = rows.indexOfFirst { row -> row.any { it.key() in staffNameHeaders } }
        if (header >= 0) {
            val headers = rows[header].map { it.key() }
            val name = headers.indexOfFirst { it in staffNameHeaders }
            val title = headers.indexOfFirst { it in titleHeaders }
            val specialty = headers.indexOfFirst { it in specialtyHeaders }
            if (name >= 0 && title >= 0) return rows.drop(header + 1).mapIndexedNotNull { index, row ->
                val fullName = row.getOrNull(name).orEmpty().cleanedName()
                val job = row.getOrNull(title).orEmpty().normalize()
                if (!fullName.looksLikeName() || job.isBlank()) null else StaffImportRow(index + 1, fullName, job, row.getOrNull(specialty).orEmpty().normalize().ifBlank { null })
            }
        }
        return rows.mapIndexedNotNull { index, row ->
            if (row.size >= 5 && row[1].cleanedName().looksLikeName() && row[3].isNotBlank()) {
                StaffImportRow(index + 1, row[1].cleanedName(), row[3].normalize(), row.getOrNull(4).orEmpty().normalize().ifBlank { null })
            } else {
                val line = row.joinToString(" ").normalize()
                val title = StaffJobTitles.values.sortedByDescending(String::length).firstOrNull { Regex("(^|\\s)${Regex.escape(it)}(\\s|$)").containsMatchIn(line) } ?: return@mapIndexedNotNull null
                val parts = line.split(title, limit = 2)
                val fullName = parts.first().cleanedName()
                if (!fullName.looksLikeName()) null else StaffImportRow(index + 1, fullName, title, parts.getOrNull(1).orEmpty().trim(' ', '-', '—', '|').ifBlank { null })
            }
        }
    }

    fun textRows(text: String): List<List<String>> = text.lineSequence().map { it.normalize() }.filter { it.isNotBlank() }.map { line ->
        val cells = line.split(Regex("\\s*[|\\t]\\s*")).filter { it.isNotBlank() }
        if (cells.size > 1) cells else listOf(line)
    }.toList()

    private val studentHeaders = setOf("اسم الطالب", "الاسم الكامل", "student name")
    private val staffNameHeaders = setOf("الاسم", "الاسم الكامل", "الاسم الرباعي", "اسم الموظف", "اسم الكادر", "staff name")
    private val titleHeaders = setOf("المسمى الوظيفي", "العنوان الوظيفي", "الوظيفة", "job title")
    private val specialtyHeaders = setOf("الاختصاص", "التخصص", "specialty")
    private fun String.cleanedName() = normalize().replace(Regex("^[\\d٠-٩]+[.)،:\\-]?\\s*"), "").trim(' ', '-', '—', '|')
    private fun String.looksLikeName() = split(' ').count { it.isNotBlank() } >= 2 && any { it.isLetter() } && key() !in studentHeaders && key() !in staffNameHeaders
    private fun String.normalize() = replace(Regex("\\s+"), " ").trim()
    private fun String.key() = normalize().lowercase().replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
}

@Singleton
class RosterDocumentImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val xlsx: XlsxCodec,
    private val docx: StaffDocxParser
) {
    suspend fun readStudents(uri: Uri, existingNames: Set<String>): ImportPreview = withContext(Dispatchers.IO) {
        val rows = when (extension(uri)) {
            "xlsx" -> xlsx.readRows(uri)
            "docx" -> open(uri, docx::parseRows)
            "pdf" -> pdfRows(uri)
            else -> error("صيغة الملف غير مدعومة. اختر Excel أو Word أو PDF")
        }
        RosterRowsParser.students(rows, existingNames)
    }

    suspend fun readStaff(uri: Uri): List<StaffImportRow> = withContext(Dispatchers.IO) {
        val rows = when (extension(uri)) {
            "xlsx" -> xlsx.readRows(uri)
            "docx" -> open(uri, docx::parseRows)
            "pdf" -> pdfRows(uri)
            else -> error("صيغة الملف غير مدعومة. اختر Excel أو Word أو PDF")
        }
        RosterRowsParser.names(rows).map { (row, name) -> StaffImportRow(row, name, "", null) }.also { require(it.isNotEmpty()) { "لم يتم العثور على أسماء كادر قابلة للقراءة في الملف" } }
    }

    private fun pdfRows(uri: Uri): List<List<String>> = open(uri) { input ->
        PDDocument.load(input).use { document ->
            val text = PDFTextStripper().apply { sortByPosition = true }.getText(document)
            require(text.isNotBlank()) { "ملف PDF لا يحتوي نصًا قابلًا للقراءة. إذا كان ممسوحًا كصورة فحوّله إلى Word أو Excel أولًا" }
            RosterRowsParser.textRows(text)
        }
    }

    private fun extension(uri: Uri): String {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return name?.substringAfterLast('.', "")?.lowercase().orEmpty()
    }

    private fun <T> open(uri: Uri, block: (java.io.InputStream) -> T): T = context.contentResolver.openInputStream(uri)?.use(block) ?: error("تعذر فتح الملف")
}
