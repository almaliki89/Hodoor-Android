package com.omega.attendance.data.importer

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.xml.parsers.DocumentBuilderFactory

data class StaffImportRow(val rowNumber: Int, val fullName: String, val jobTitle: String, val specialty: String?)

class StaffDocxParser @Inject constructor() {
    fun parse(input: InputStream): List<StaffImportRow> = RosterRowsParser.staff(parseRows(input))

    fun parseRows(input: InputStream): List<List<String>> {
        val xml = ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") return@use zip.readBytes()
                entry = zip.nextEntry
            }
            error("ملف Word لا يحتوي على مستند صالح")
        }
        require(xml.size <= 10 * 1024 * 1024) { "ملف Word أكبر من الحد المسموح" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml))
        val rows = document.getElementsByTagNameNS("*", "tr")
        return buildList {
            for (index in 0 until rows.length) {
                val row = rows.item(index) as? Element ?: continue
                val cells = row.getElementsByTagNameNS("*", "tc")
                add((0 until cells.length).map { cellIndex ->
                    val textNodes = (cells.item(cellIndex) as Element).getElementsByTagNameNS("*", "t")
                    (0 until textNodes.length).joinToString(" ") { textNodes.item(it).textContent }.normalize()
                })
            }
        }
    }

    private fun String.normalize(): String = replace(Regex("\\s+"), " ").trim()
}
