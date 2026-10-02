package com.omega.attendance

import com.omega.attendance.data.importer.StaffDocxParser
import com.omega.attendance.data.local.entity.StaffEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class StaffDocxImportTest {
    @Test fun staffSchemaContainsSpecialty() {
        assertTrue(StaffEntity::class.java.declaredFields.any { it.name == "specialty" })
    }

    @Test fun readsOnlyStaffRowsFromWordTable() {
        val xml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:tbl>
              <w:tr><w:tc><w:p><w:r><w:t>ت</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>الاسم الرباعي</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>ملاحظات</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>العنوان الوظيفي</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>الاختصاص</w:t></w:r></w:p></w:tc></w:tr>
              <w:tr><w:tc><w:p/></w:tc><w:tc><w:p><w:r><w:t>أحمد محمد علي حسن</w:t></w:r></w:p></w:tc><w:tc><w:p/></w:tc><w:tc><w:p><w:r><w:t>مدرس</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>رياضيات</w:t></w:r></w:p></w:tc></w:tr>
            </w:tbl></w:body></w:document>""".trimIndent()
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }.toByteArray()

        val rows = StaffDocxParser().parse(ByteArrayInputStream(bytes))

        assertEquals(1, rows.size)
        assertEquals(1, rows.single().rowNumber)
        assertEquals("أحمد محمد علي حسن", rows.single().fullName)
        assertEquals("مدرس", rows.single().jobTitle)
        assertEquals("رياضيات", rows.single().specialty)
    }
}
