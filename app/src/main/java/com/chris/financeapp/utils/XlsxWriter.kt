package com.chris.financeapp.utils

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {
    fun writeXlsx(headers: List<String>, rows: List<List<Any>>, outputStream: OutputStream) {
        val zip = ZipOutputStream(outputStream)

        // 1. [Content_Types].xml
        zip.putNextEntry(ZipEntry("[Content_Types].xml"))
        zip.write("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
            </Types>
        """.trimIndent().toByteArray())
        zip.closeEntry()

        // 2. _rels/.rels
        zip.putNextEntry(ZipEntry("_rels/.rels"))
        zip.write("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent().toByteArray())
        zip.closeEntry()

        // 3. xl/workbook.xml
        zip.putNextEntry(ZipEntry("xl/workbook.xml"))
        zip.write("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>
                    <sheet name="Projections" sheetId="1" r:id="rId1"/>
                </sheets>
            </workbook>
        """.trimIndent().toByteArray())
        zip.closeEntry()

        // 4. xl/_rels/workbook.xml.rels
        zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
        zip.write("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent().toByteArray())
        zip.closeEntry()

        // 5. xl/worksheets/sheet1.xml
        zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
        
        val sheetBuilder = StringBuilder()
        sheetBuilder.append("""
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
        """.trimIndent())

        // Helper to convert index to Excel column name (A, B, C, ... AA, AB, ...)
        fun getColName(colIdx: Int): String {
            var temp = colIdx
            val name = StringBuilder()
            while (temp >= 0) {
                name.insert(0, ('A' + (temp % 26)))
                temp = (temp / 26) - 1
            }
            return name.toString()
        }

        // Add Headers row (Row 1)
        sheetBuilder.append("<row r=\"1\">")
        headers.forEachIndexed { colIdx, header ->
            val ref = "${getColName(colIdx)}1"
            val escaped = header.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            sheetBuilder.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>$escaped</t></is></c>")
        }
        sheetBuilder.append("</row>")

        // Data Rows (Row 2 onwards)
        rows.forEachIndexed { rowIdx, rowData ->
            val rNum = rowIdx + 2
            sheetBuilder.append("<row r=\"$rNum\">")
            rowData.forEachIndexed { colIdx, value ->
                val ref = "${getColName(colIdx)}$rNum"
                when (value) {
                    is Number -> {
                        sheetBuilder.append("<c r=\"$ref\" t=\"n\"><v>$value</v></c>")
                    }
                    is Boolean -> {
                        val bVal = if (value) "1" else "0"
                        sheetBuilder.append("<c r=\"$ref\" t=\"b\"><v>$bVal</v></c>")
                    }
                    else -> {
                        val escaped = value.toString().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                        sheetBuilder.append("<c r=\"$ref\" t=\"inlineStr\"><is><t>$escaped</t></is></c>")
                    }
                }
            }
            sheetBuilder.append("</row>")
        }

        sheetBuilder.append("""
                </sheetData>
            </worksheet>
        """.trimIndent())

        zip.write(sheetBuilder.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.close()
    }
}
