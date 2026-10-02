package com.omega.attendance.export.excel

import android.content.Context
import android.net.Uri
import com.omega.attendance.data.local.entity.StudentEntity
import com.omega.attendance.data.local.entity.HallEntity
import com.omega.attendance.data.local.dao.ClassOption
import com.omega.attendance.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import org.xmlpull.v1.XmlPullParser
import android.util.Xml
import java.io.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XlsxCodec @Inject constructor(@ApplicationContext private val context: Context) {
    suspend fun writeStudents(uri: Uri, rows: List<StudentEntity>, options: List<ClassOption>, halls: List<HallEntity>, schoolName: String) {
        val classes = options.associateBy { it.classId }
        val hallNames = halls.associateBy { it.id }
        val data = buildList {
            add(listOf("رقم الطالب", "اسم الطالب", "المرحلة", "الصف", "الشعبة", "القاعة", "هاتف ولي الأمر", "رمز QR", "الحالة"))
            rows.forEach { student ->
                val option = classes[student.classId]
                add(listOf(
                    student.studentNumber, student.fullName, option?.gradeName.orEmpty(), option?.className.orEmpty(),
                    option?.sectionName.orEmpty(), student.hallId?.let { hallNames[it]?.name }.orEmpty(), student.parentPhone.orEmpty(), student.qrValue,
                    if (student.status.name == "ACTIVE") "نشط" else "مؤرشف"
                ))
            }
        }
        writeTable(uri, data, "قائمة الطلبة", schoolName, "عدد الطلبة: ${rows.size}")
    }

    suspend fun writeTemplate(uri: Uri) = writeTable(
        uri,
        listOf(listOf("ت", "اسم الطالب"), listOf("1", "أحمد محمد علي")),
        "نموذج استيراد أسماء الطلبة", "يأخذ التطبيق الأسماء فقط", "يُختار الصف والقاعة داخل التطبيق"
    )

    suspend fun writeTable(uri: Uri, rows: List<List<String>>, title: String = "تقرير الحضور", subtitle: String = "", summary: String = "") {
        require(rows.isNotEmpty() && rows.first().isNotEmpty())
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                entry(zip, "[Content_Types].xml", contentTypes())
                entry(zip, "_rels/.rels", rootRels())
                entry(zip, "xl/workbook.xml", workbook(title.take(31)))
                entry(zip, "xl/_rels/workbook.xml.rels", workbookRels())
                entry(zip, "xl/styles.xml", styles())
                entry(zip, "xl/worksheets/sheet1.xml", sheet(rows, title, subtitle, summary))
            }
        } ?: error("تعذر فتح ملف Excel للكتابة")
        val valid = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize > 100 } == true
        require(valid) { "ملف Excel الناتج غير صالح" }
    }

    suspend fun readRows(uri: Uri): List<List<String>> {
        val files = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)!!.use { input -> ZipInputStream(BufferedInputStream(input)).use { zip -> var item = zip.nextEntry; while (item != null) { if (!item.isDirectory && (item.name == "xl/sharedStrings.xml" || item.name == "xl/worksheets/sheet1.xml")) files[item.name] = zip.readBytes(); item = zip.nextEntry } } }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        return files["xl/worksheets/sheet1.xml"]?.let { parseSheet(it, shared) } ?: error("ملف Excel لا يحتوي ورقة بيانات")
    }

    suspend fun readImport(uri: Uri, existingNumbers: Set<String>, existingNames: Set<String> = emptySet()): ImportPreview {
        val files = mutableMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)!!.use { input -> ZipInputStream(BufferedInputStream(input)).use { zip -> var item = zip.nextEntry; while (item != null) { if (!item.isDirectory && (item.name == "xl/sharedStrings.xml" || item.name == "xl/worksheets/sheet1.xml")) files[item.name] = zip.readBytes(); item = zip.nextEntry } } }
        val shared = files["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val raw = files["xl/worksheets/sheet1.xml"]?.let { parseSheet(it, shared) } ?: error("ملف Excel لا يحتوي ورقة بيانات")
        val issues = mutableListOf<ImportIssue>(); val valid = mutableListOf<ImportRow>(); val seenNumbers = mutableSetOf<String>(); val seenNames = mutableSetOf<String>()
        val knownNames = existingNames.mapTo(mutableSetOf()) { it.normalizeKey() }
        val headerIndex = raw.indexOfFirst { row -> row.getOrNull(0)?.trim() in setOf("رقم الطالب", "Student Number") }
        if (headerIndex < 0) return readNamesOnlyRoster(raw, knownNames)
        val dataRows = raw.drop(headerIndex + 1).filter { row -> row.any { it.isNotBlank() } }
        dataRows.forEachIndexed { index, values ->
            val row = headerIndex + index + 2; val cells = values + List((7 - values.size).coerceAtLeast(0)) { "" }
            val number = cells[0].trim(); val name = cells[1].trim()
            val nameKey = name.normalizeKey()
            when {
                name.isBlank() -> issues += ImportIssue(row, "اسم الطالب مفقود")
                nameKey in knownNames -> issues += ImportIssue(row, "اسم الطالب موجود مسبقًا")
                !seenNames.add(nameKey) -> issues += ImportIssue(row, "اسم الطالب مكرر داخل الملف")
                number.isNotBlank() && number in existingNumbers -> issues += ImportIssue(row, "رقم الطالب موجود مسبقًا")
                number.isNotBlank() && !seenNumbers.add(number) -> issues += ImportIssue(row, "رقم الطالب مكرر داخل الملف")
                cells[2].isBlank() || cells[3].isBlank() -> issues += ImportIssue(row, "المرحلة أو الصف مفقود")
                else -> valid += ImportRow(row, number, name, cells[2], cells[3], cells[4], cells[5], cells[6])
            }
        }
        return ImportPreview(dataRows.size, valid, issues)
    }

    private fun readNamesOnlyRoster(raw: List<List<String>>, knownNames: Set<String>): ImportPreview {
        val headerIndex = raw.indexOfFirst { row -> row.any { it.normalizeKey() in setOf("اسم الطالب", "الاسم الكامل", "student name") } }
        require(headerIndex >= 0) { "لم يتم العثور على عناوين أعمدة الطلبة" }
        val headers = raw[headerIndex].map { it.normalizeKey() }
        val nameColumn = headers.indexOfFirst { it in setOf("اسم الطالب", "الاسم الكامل", "student name") }
        val serialColumn = headers.indexOfFirst { it in setOf("ت", "تسلسل", "#") }
        require(nameColumn >= 0 && serialColumn >= 0) { "ملف الأسماء يحتاج عمودي التسلسل واسم الطالب" }
        val title = raw.take(headerIndex).flatten().joinToString(" ").normalizeKey()
        val grade = listOf("الرابع", "الخامس", "السادس").firstOrNull { title.contains(it.normalizeKey()) }.orEmpty()
        val track = when {
            title.contains("ادبي") -> "أدبي"
            title.contains("علمي") -> "علمي"
            else -> ""
        }
        val className = listOf(grade, track).filter(String::isNotBlank).joinToString(" ")
        val issues = mutableListOf<ImportIssue>()
        val valid = mutableListOf<ImportRow>()
        val seenNames = mutableSetOf<String>()
        val rows = raw.drop(headerIndex + 1).mapIndexedNotNull { offset, values ->
            val serial = values.getOrNull(serialColumn).orEmpty().trim()
            val name = values.getOrNull(nameColumn).orEmpty().trim()
            if (serial.toIntOrNull() == null || name.isBlank()) null else Triple(headerIndex + offset + 2, serial, name)
        }
        rows.forEach { (rowNumber, _, name) ->
            val key = name.normalizeKey()
            when {
                className.isBlank() -> issues += ImportIssue(rowNumber, "تعذر معرفة الصف من عنوان الملف")
                key in knownNames -> issues += ImportIssue(rowNumber, "اسم الطالب موجود مسبقًا")
                !seenNames.add(key) -> issues += ImportIssue(rowNumber, "اسم الطالب مكرر داخل الملف")
                else -> valid += ImportRow(rowNumber, "", name, grade, className, track, "", "")
            }
        }
        return ImportPreview(rows.size, valid, issues)
    }

    private fun sharedStrings(bytes: ByteArray): List<String> = parseXml(bytes) { parser ->
        val result = mutableListOf<String>(); var current = StringBuilder(); var inSi = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) { when (parser.eventType) { XmlPullParser.START_TAG -> if (parser.name == "si") { inSi = true; current = StringBuilder() }; XmlPullParser.TEXT -> if (inSi) current.append(parser.text); XmlPullParser.END_TAG -> if (parser.name == "si") { result += current.toString(); inSi = false } }; parser.next() }; result
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> = parseXml(bytes) { parser ->
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); var type = ""; var value = ""; var inValue = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) { when (parser.eventType) { XmlPullParser.START_TAG -> when (parser.name) { "row" -> row = mutableListOf(); "c" -> { val target = cellColumn(parser.getAttributeValue(null, "r").orEmpty()); while (row.size < target) row += ""; type = parser.getAttributeValue(null, "t") ?: ""; value = "" }; "v", "t" -> inValue = true }; XmlPullParser.TEXT -> if (inValue) value += parser.text; XmlPullParser.END_TAG -> when (parser.name) { "v", "t" -> inValue = false; "c" -> row += if (type == "s") shared.getOrNull(value.toIntOrNull() ?: -1).orEmpty() else value; "row" -> rows += row } }; parser.next() }; rows
    }

    private fun <T> parseXml(bytes: ByteArray, block: (XmlPullParser) -> T): T = ByteArrayInputStream(bytes).use { input -> Xml.newPullParser().run { setInput(input, "UTF-8"); block(this) } }
    private fun entry(zip: ZipOutputStream, path: String, text: String) { zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
    private fun esc(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun sheet(rows: List<List<String>>, title: String, subtitle: String, summary: String) = buildString {
        val headers = rows.first()
        val lastColumn = column(headers.lastIndex)
        val lastRow = 6 + rows.size - 1
        append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        append("<dimension ref=\"A1:$lastColumn$lastRow\"/>")
        append("<sheetViews><sheetView workbookViewId=\"0\" rightToLeft=\"1\" showGridLines=\"0\"><pane ySplit=\"6\" topLeftCell=\"A7\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
        append("<sheetFormatPr defaultRowHeight=\"20\"/><cols>")
        headers.forEachIndexed { index, header ->
            val width = when (header) { "اسم الطالب", "الاسم الكامل" -> 30; "رمز QR" -> 44; "رقم الطالب" -> 22; "الصف" -> 23; else -> 18 }
            append("<col min=\"${index + 1}\" max=\"${index + 1}\" width=\"$width\" customWidth=\"1\"/>")
        }
        append("</cols><sheetData>")
        append("<row r=\"1\" ht=\"34\" customHeight=\"1\">${cell("A1", title, 1)}</row>")
        append("<row r=\"2\" ht=\"26\" customHeight=\"1\">${cell("A2", subtitle, 2)}</row>")
        append("<row r=\"3\" ht=\"26\" customHeight=\"1\">${cell("A3", summary, 5)}</row>")
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
        append("<row r=\"4\" ht=\"23\" customHeight=\"1\">${cell("A4", "تاريخ الإنشاء: $generated", 2)}</row>")
        append("<row r=\"5\" ht=\"10\" customHeight=\"1\"/>")
        append("<row r=\"6\" ht=\"30\" customHeight=\"1\">")
        headers.forEachIndexed { index, header -> append(cell("${column(index)}6", header, 3)) }
        append("</row>")
        rows.drop(1).forEachIndexed { rowIndex, values ->
            val rowNumber = rowIndex + 7
            append("<row r=\"$rowNumber\" ht=\"23\" customHeight=\"1\">")
            headers.indices.forEach { index ->
                val value = values.getOrNull(index).orEmpty()
                val header = headers[index]
                val numeric = header in setOf("#", "أيام الحضور", "أيام التأخر", "أيام الغياب", "أيام الإجازة")
                val percent = header == "نسبة الحضور"
                val style = if (percent) 6 else if (rowIndex % 2 == 0) 4 else 0
                append(cell("${column(index)}$rowNumber", value, style, numeric, percent))
            }
            append("</row>")
        }
        append("</sheetData><mergeCells count=\"4\">")
        (1..4).forEach { row -> append("<mergeCell ref=\"A$row:$lastColumn$row\"/>") }
        append("</mergeCells><autoFilter ref=\"A6:$lastColumn$lastRow\"/>")
        append("<pageMargins left=\"0.25\" right=\"0.25\" top=\"0.5\" bottom=\"0.5\" header=\"0.2\" footer=\"0.2\"/>")
        append("<pageSetup orientation=\"landscape\" paperSize=\"9\" fitToWidth=\"1\" fitToHeight=\"0\"/></worksheet>")
    }

    private fun cell(ref: String, value: String, style: Int, numeric: Boolean = false, percent: Boolean = false): String {
        val number = when {
            percent -> value.removeSuffix("%").toDoubleOrNull()?.div(100)
            numeric -> value.toDoubleOrNull()
            else -> null
        }
        return if (number != null) "<c r=\"$ref\" s=\"$style\"><v>$number</v></c>"
        else "<c r=\"$ref\" s=\"$style\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(value)}</t></is></c>"
    }
    private fun column(index: Int): String { var n = index + 1; var s = ""; while (n > 0) { n--; s = ('A'.code + n % 26).toChar() + s; n /= 26 }; return s }
    private fun cellColumn(reference: String): Int = reference.takeWhile(Char::isLetter).fold(0) { value, letter -> value * 26 + (letter.uppercaseChar() - 'A' + 1) } - 1
    private fun String.normalizeKey(): String = replace(Regex("\\s+"), " ").trim().replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
    private fun contentTypes() = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
    private fun rootRels() = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
    private fun workbook(sheetName: String) = """<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${esc(sheetName)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    private fun workbookRels() = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
    private fun styles() = """<?xml version="1.0" encoding="UTF-8"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="3"><font><sz val="11"/><name val="Arial"/><color rgb="FF263238"/></font><font><b/><sz val="18"/><name val="Arial"/><color rgb="FFFFFFFF"/></font><font><b/><sz val="11"/><name val="Arial"/><color rgb="FFFFFFFF"/></font></fonts><fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FF176B5B"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEFF6F3"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left/><right/><top/><bottom style="thin"><color rgb="FFDCE7E3"/></bottom><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="7"><xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf><xf numFmtId="0" fontId="2" fillId="2" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf><xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf><xf numFmtId="0" fontId="0" fillId="3" borderId="0" xfId="0" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf><xf numFmtId="10" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyAlignment="1"><alignment horizontal="right" vertical="center" readingOrder="2"/></xf></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
