package com.mitrading.app.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream

/**
 * Importa extractos de posiciones u operaciones exportados desde XTB (Excel .xlsx o CSV)
 * y Trade Republic (CSV). Detecta las columnas por su encabezado.
 */
object Importer {

    data class Result(val positions: List<Position>, val message: String)

    fun import(context: Context, uri: Uri, broker: String): Result {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return Result(emptyList(), "No se pudo abrir el archivo.")
        val tables: List<List<List<String>>> = try {
            if (bytes.size > 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) readXlsx(bytes)
            else listOf(readCsv(decode(bytes)))
        } catch (e: Exception) {
            return Result(emptyList(), "No se pudo leer el archivo: ${e.message}")
        }
        for (table in tables) {
            val parsed = parseTable(table, broker)
            if (parsed != null && parsed.isNotEmpty()) {
                return Result(parsed, "Importadas ${parsed.size} posiciones de $broker.")
            }
        }
        return Result(
            emptyList(),
            "No he reconocido las columnas del archivo. Debe tener al menos una columna de símbolo, ISIN o nombre y otra de cantidad (volumen, cantidad, acciones…)."
        )
    }

    // ---------------- Lectura de archivos ----------------

    private fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charsets.ISO_8859_1) else utf8.removePrefix("﻿")
    }

    fun readCsv(text: String): List<List<String>> {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sample = lines.take(10).joinToString("\n")
        val delim = listOf(';', ',', '\t').maxByOrNull { d -> sample.count { it == d } } ?: ';'
        return lines.map { splitCsvLine(it, delim) }
    }

    private fun splitCsvLine(line: String, delim: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == delim && !quoted -> { out += sb.toString().trim(); sb.clear() }
                else -> sb.append(ch)
            }
            i++
        }
        out += sb.toString().trim()
        return out
    }

    /** Lee todas las hojas de un .xlsx (es un ZIP con XML dentro). */
    fun readXlsx(bytes: ByteArray): List<List<List<String>>> {
        var shared: List<String> = emptyList()
        val sheets = sortedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml") shared = parseSharedStrings(zip.readBytes())
                else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) sheets[name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return sheets.values.map { parseSheet(it, shared) }
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(xml), "UTF-8")
        var current: StringBuilder? = null
        var inT = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> current = StringBuilder()
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT) current?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "t" -> inT = false
                    "si" -> { out += current?.toString() ?: ""; current = null }
                }
            }
            ev = p.next()
        }
        return out
    }

    private fun colIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n - 1
    }

    private fun parseSheet(xml: ByteArray, shared: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val p = Xml.newPullParser()
        p.setInput(ByteArrayInputStream(xml), "UTF-8")
        var row: MutableMap<Int, String>? = null
        var cellCol = 0
        var cellType = ""
        var value = StringBuilder()
        var inValue = false
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> row = mutableMapOf()
                    "c" -> {
                        cellCol = colIndex(p.getAttributeValue(null, "r") ?: "A")
                        cellType = p.getAttributeValue(null, "t") ?: ""
                        value = StringBuilder()
                    }
                    "v", "t" -> inValue = true
                }
                XmlPullParser.TEXT -> if (inValue) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> inValue = false
                    "c" -> {
                        val raw = value.toString()
                        val text = if (cellType == "s") shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: "" else raw
                        row?.put(cellCol, text.trim())
                    }
                    "row" -> {
                        val r = row
                        if (r != null && r.isNotEmpty()) {
                            val max = r.keys.maxOrNull() ?: 0
                            rows += (0..max).map { r[it] ?: "" }
                        }
                        row = null
                    }
                }
            }
            ev = p.next()
        }
        return rows
    }

    // ---------------- Interpretación ----------------

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private enum class Col { SYMBOL, ISIN, NAME, QTY, PRICE, SIDE }

    private val patterns: Map<Col, List<String>> = mapOf(
        Col.ISIN to listOf("isin"),
        Col.SYMBOL to listOf("symbol", "simbolo", "ticker", "instrumento", "instrument", "activo", "producto", "product"),
        Col.NAME to listOf("nombre", "name", "descripcion", "description", "titulo", "valor"),
        Col.QTY to listOf("volume", "volumen", "cantidad", "quantity", "shares", "acciones", "titulos", "participaciones", "units", "unidades", "stuck", "anzahl", "lotes", "lots"),
        Col.PRICE to listOf("open price", "precio de apertura", "precio apertura", "precio medio", "average price", "avg price", "precio de compra", "purchase price", "precio", "price", "kurs"),
        Col.SIDE to listOf("type", "tipo", "side", "operacion", "direction", "direccion", "accion")
    )

    private val priceExclusions = listOf("close", "cierre", "market", "actual", "current", "mercado", "sl", "tp", "stop", "take")

    private fun mapHeader(header: List<String>): Map<Col, Int> {
        val found = mutableMapOf<Col, Int>()
        val h = header.map { norm(it) }
        // Primero coincidencias exactas, después parciales.
        for (exact in listOf(true, false)) {
            for (col in listOf(Col.ISIN, Col.SYMBOL, Col.QTY, Col.PRICE, Col.SIDE, Col.NAME)) {
                if (found.containsKey(col)) continue
                val pats = patterns.getValue(col)
                loop@ for (pat in pats) {
                    for ((i, name) in h.withIndex()) {
                        if (name.isBlank() || found.containsValue(i)) continue
                        val match = if (exact) name == pat else name.contains(pat)
                        if (!match) continue
                        if (col == Col.PRICE && priceExclusions.any { name.split(' ', '_', '/').contains(it) || name.startsWith(it) }) continue
                        found[col] = i
                        break@loop
                    }
                }
            }
        }
        return found
    }

    fun parseNumber(s: String): Double? {
        var t = s.replace(" ", "").replace(" ", "").replace(Regex("[^0-9,.\\-+]"), "")
        if (t.isBlank()) return null
        val lastComma = t.lastIndexOf(',')
        val lastDot = t.lastIndexOf('.')
        t = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 -> t.replace(',', '.')
            else -> t
        }
        return t.toDoubleOrNull()
    }

    private fun parseTable(rows: List<List<String>>, broker: String): List<Position>? {
        val headerIdx = rows.take(40).indexOfFirst { r ->
            val m = mapHeader(r)
            m.containsKey(Col.QTY) && (m.containsKey(Col.SYMBOL) || m.containsKey(Col.ISIN) || m.containsKey(Col.NAME))
        }
        if (headerIdx < 0) return null
        val cols = mapHeader(rows[headerIdx])
        data class Acc(var name: String, var qty: Double, var cost: Double)
        val acc = linkedMapOf<String, Acc>()
        for (r in rows.drop(headerIdx + 1)) {
            fun cell(c: Col): String = cols[c]?.let { r.getOrNull(it) } ?: ""
            val qtyRaw = parseNumber(cell(Col.QTY)) ?: continue
            if (qtyRaw == 0.0) continue
            val rawSymbol = cell(Col.SYMBOL)
            val isin = cell(Col.ISIN)
            val name = cell(Col.NAME).ifBlank { rawSymbol }
            val key = when {
                rawSymbol.isNotBlank() && !looksLikeIsin(rawSymbol) -> mapBrokerSymbol(rawSymbol)
                isin.isNotBlank() -> "ISIN:$isin"
                looksLikeIsin(rawSymbol) -> "ISIN:$rawSymbol"
                name.isNotBlank() -> "NAME:$name"
                else -> continue
            }
            val side = norm(cell(Col.SIDE))
            val isSell = side.contains("sell") || side.contains("venta") || side.contains("verkauf") || side.contains("vender")
            val price = parseNumber(cell(Col.PRICE)) ?: 0.0
            val qty = kotlin.math.abs(qtyRaw)
            val a = acc.getOrPut(key) { Acc(name.ifBlank { key }, 0.0, 0.0) }
            if (isSell) {
                if (a.qty > 0) {
                    val avg = a.cost / a.qty
                    a.qty -= qty
                    a.cost = if (a.qty > 0) avg * a.qty else 0.0
                }
            } else {
                a.qty += qty
                a.cost += qty * price
            }
        }
        return acc.filter { it.value.qty > 1e-9 }.map { (k, a) ->
            Position(symbol = k, name = a.name, qty = a.qty, avgPrice = if (a.qty > 0) a.cost / a.qty else 0.0, broker = broker)
        }
    }

    private fun looksLikeIsin(s: String) = Regex("^[A-Z]{2}[A-Z0-9]{9}[0-9]$").matches(s.trim().uppercase())

    private val xtbSpecial = mapOf(
        "US500" to "^GSPC", "US100" to "^NDX", "US30" to "^DJI", "DE40" to "^GDAXI", "DE30" to "^GDAXI",
        "SPA35" to "^IBEX", "EU50" to "^STOXX50E", "FRA40" to "^FCHI", "UK100" to "^FTSE", "JP225" to "^N225",
        "GOLD" to "GC=F", "SILVER" to "SI=F", "OIL" to "BZ=F", "OIL.WTI" to "CL=F", "NATGAS" to "NG=F",
        "COPPER" to "HG=F", "WHEAT" to "ZW=F", "CORN" to "ZC=F", "SOYBEAN" to "ZS=F", "COFFEE" to "KC=F", "COCOA" to "CC=F"
    )

    /** Traduce símbolos de XTB (p. ej. IBE.ES, AAPL.US, US500) al formato de Yahoo. */
    fun mapBrokerSymbol(raw: String): String {
        val s = raw.trim().uppercase().replace(Regex("_\\d+$"), "")
        xtbSpecial[s]?.let { return it }
        val dot = s.lastIndexOf('.')
        if (dot < 0) return s
        val base = s.substring(0, dot)
        return when (s.substring(dot + 1)) {
            "US" -> base
            "ES" -> "$base.MC"
            "DE" -> "$base.DE"
            "FR" -> "$base.PA"
            "NL" -> "$base.AS"
            "IT" -> "$base.MI"
            "UK" -> "$base.L"
            "PT" -> "$base.LS"
            "BE" -> "$base.BR"
            "CH" -> "$base.SW"
            "DK" -> "$base.CO"
            "SE" -> "$base.ST"
            "NO" -> "$base.OL"
            "FI" -> "$base.HE"
            "PL" -> "$base.WA"
            else -> s
        }
    }
}
