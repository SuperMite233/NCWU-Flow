package cn.edu.ncwu.flow.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import cn.edu.ncwu.flow.model.ImportedVerificationCode
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

object ActivityVerifycodeImporter {
    fun importFromUri(context: Context, uri: Uri, studentId: String): Result<List<ImportedVerificationCode>> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use(InputStream::readBytes)
            ?: error("无法读取所选文件")
        val rows = if (bytes.isZip()) parseSpreadsheetXml(bytes) else parseDelimited(bytes)
        val matched = CampusParser.parseImportedVerificationCodes(rows, studentId)
        if (matched.isEmpty()) error(if (studentId.isBlank()) "文件中没有可用验证码" else "文件中没有找到当前学号对应的验证码")
        matched
    }

    private fun parseSpreadsheetXml(bytes: ByteArray): List<List<String>> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/sharedStrings.xml" || entry.name == "xl/worksheets/sheet1.xml") {
                    entries[entry.name] = zip.readBytes()
                }
            }
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheet = entries["xl/worksheets/sheet1.xml"] ?: error("未找到工作表")
        return parseSheet(sheet, sharedStrings)
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        val values = mutableListOf<String>()
        var current = StringBuilder()
        var insideSharedString = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    current = StringBuilder()
                    insideSharedString = true
                }
                XmlPullParser.TEXT -> if (insideSharedString) current.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") values += current.toString()
            }
            if (event == XmlPullParser.END_TAG && parser.name == "si") insideSharedString = false
            event = parser.next()
        }
        return values
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(ByteArrayInputStream(bytes), "UTF-8")
        }
        val rows = mutableListOf<List<String>>()
        var cells: MutableMap<Int, String>? = null
        var rowNumber = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        cells = linkedMapOf()
                        rowNumber = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: rows.size + 1
                    }
                    "c" -> if (cells != null) {
                        val reference = parser.getAttributeValue(null, "r").orEmpty()
                        val type = parser.getAttributeValue(null, "t").orEmpty()
                        val value = readCell(parser)
                        cells?.set(columnIndex(reference), if (type == "s") {
                            sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                        } else value)
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "row") {
                    val current = cells.orEmpty()
                    while (rows.size < rowNumber) {
                        rows.add(emptyList())
                    }
                    val width = (current.keys.maxOrNull() ?: 4) + 1
                    rows.set(rowNumber - 1, (0 until width).map { current[it].orEmpty() })
                    cells = null
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun readCell(parser: XmlPullParser): String {
        val depth = parser.depth
        var value = ""
        var event = parser.next()
        while (!(event == XmlPullParser.END_TAG && parser.depth == depth && parser.name == "c")) {
            if (event == XmlPullParser.TEXT) value += parser.text
            event = parser.next()
        }
        return value
    }

    private fun parseDelimited(bytes: ByteArray): List<List<String>> {
        val utf8 = bytes.toString(Charsets.UTF_8)
        val text = if (utf8.count { it == '\uFFFD' } > 2) String(bytes, Charset.forName("GBK")) else utf8
        return text.lineSequence()
            .filter { it.isNotBlank() }
            .map { line -> line.split(if ('\t' in line) '\t' else ',').map(String::trim) }
            .toList()
    }

    private fun columnIndex(reference: String): Int {
        val letters = reference.takeWhile { it.isLetter() }
        return letters.fold(0) { result, char -> result * 26 + (char.uppercaseChar() - 'A' + 1) } - 1
    }

    private fun ByteArray.isZip(): Boolean = size >= 2 && this[0] == 'P'.code.toByte() && this[1] == 'K'.code.toByte()
}
