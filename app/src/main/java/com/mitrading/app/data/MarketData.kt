package com.mitrading.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

// =====================================================================
//  Yahoo Finance (no oficial, sin clave). Acciones, índices, futuros, ETF.
// =====================================================================
object Yahoo {
    private const val BASE = "https://query1.finance.yahoo.com"
    private const val BASE2 = "https://query2.finance.yahoo.com"

    /** Prueba con query1 y, si falla, con query2. */
    private suspend fun fetch(url: String): String = try {
        Net.get(url)
    } catch (e: Exception) {
        Net.get(url.replace(BASE, BASE2))
    }

    suspend fun search(query: String): List<Instrument> {
        if (query.isBlank()) return emptyList()
        val url = "$BASE/v1/finance/search?q=${Net.enc(query)}&quotesCount=25&newsCount=0&lang=es-ES&region=ES"
        val root = JSONObject(fetch(url))
        val quotes = root.optJSONArray("quotes") ?: return emptyList()
        val out = mutableListOf<Instrument>()
        for (i in 0 until quotes.length()) {
            val q = quotes.optJSONObject(i) ?: continue
            val sym = q.optString("symbol", "")
            if (sym.isBlank()) continue
            val qt = q.optString("quoteType", "")
            if (qt.equals("CURRENCY", true) || qt.equals("OPTION", true)) continue
            val name = q.optString("longname", "").ifBlank { q.optString("shortname", sym) }
            out += Instrument(
                symbol = sym,
                name = name,
                type = AssetType.fromYahoo(qt),
                exchange = q.optString("exchDisp", q.optString("exchange", ""))
            )
        }
        return out
    }

    suspend fun candles(symbol: String, tf: Timeframe): Series {
        val (interval, range) = when (tf) {
            Timeframe.H1 -> "60m" to "6mo"
            Timeframe.H4 -> "60m" to "1y"
            Timeframe.D1 -> "1d" to "5y"
            Timeframe.W1 -> "1wk" to "10y"
            Timeframe.MN -> "1mo" to "max"
            Timeframe.Y1 -> "1mo" to "max"
        }
        val url = "$BASE/v8/finance/chart/${Net.enc(symbol)}?interval=$interval&range=$range&includePrePost=false"
        val res = JSONObject(fetch(url)).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val currency = res.optJSONObject("meta")?.optString("currency", "") ?: ""
        val ts = res.optJSONArray("timestamp") ?: JSONArray()
        val q = res.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val o = q.optJSONArray("open")
        val h = q.optJSONArray("high")
        val l = q.optJSONArray("low")
        val c = q.optJSONArray("close")
        val v = q.optJSONArray("volume")
        val list = ArrayList<Candle>(ts.length())
        if (o != null && h != null && l != null && c != null) {
            for (i in 0 until ts.length()) {
                if (o.isNull(i) || h.isNull(i) || l.isNull(i) || c.isNull(i)) continue
                val vol = if (v == null || v.isNull(i)) 0.0 else v.optDouble(i, 0.0)
                list += Candle(ts.getLong(i), o.getDouble(i), h.getDouble(i), l.getDouble(i), c.getDouble(i), vol)
            }
        }
        val out = when (tf) {
            Timeframe.H4 -> Agg.fourHours(list)
            Timeframe.Y1 -> Agg.yearly(list)
            else -> list
        }
        return Series(out, "Yahoo Finance (puede tener retraso)", currency)
    }

    suspend fun quote(symbol: String): Quote {
        val url = "$BASE/v8/finance/chart/${Net.enc(symbol)}?interval=1d&range=5d"
        val res = JSONObject(fetch(url)).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val meta = res.getJSONObject("meta")
        val closesArr = res.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0).optJSONArray("close")
        val closes = mutableListOf<Double>()
        if (closesArr != null) for (i in 0 until closesArr.length()) if (!closesArr.isNull(i)) closes += closesArr.getDouble(i)
        val price = meta.optDouble("regularMarketPrice", closes.lastOrNull() ?: 0.0)
        val prev = if (closes.size >= 2) closes[closes.size - 2] else meta.optDouble("chartPreviousClose", price)
        return Quote(price, prev, meta.optString("currency", ""), "Yahoo")
    }

    suspend fun news(symbol: String, count: Int = 8): List<NewsItem> {
        val url = "$BASE/v1/finance/search?q=${Net.enc(symbol)}&quotesCount=0&newsCount=$count&lang=es-ES&region=ES"
        val arr = JSONObject(fetch(url)).optJSONArray("news") ?: return emptyList()
        val out = mutableListOf<NewsItem>()
        for (i in 0 until arr.length()) {
            val n = arr.optJSONObject(i) ?: continue
            val title = n.optString("title", "")
            if (title.isBlank()) continue
            out += NewsItem(
                title = title,
                publisher = n.optString("publisher", ""),
                time = n.optLong("providerPublishTime", 0L),
                url = n.optString("link", ""),
                symbol = symbol,
                cat = NewsClassifier.classify(title, "")
            )
        }
        return out
    }
}

// =====================================================================
//  Twelve Data (con clave). Tiempo real EE. UU. gratis; Europa en planes Pro.
// =====================================================================
object Twelve {
    private const val BASE = "https://api.twelvedata.com"

    /** Traduce el símbolo de Yahoo al de Twelve Data. Devuelve null si no aplica (índices, futuros…). */
    fun mapSymbol(yahoo: String): Pair<String, String?>? {
        if (yahoo.contains('^') || yahoo.contains('=')) return null
        val dot = yahoo.lastIndexOf('.')
        if (dot < 0) return yahoo to null
        val base = yahoo.substring(0, dot)
        val exchange = when (yahoo.substring(dot + 1).uppercase()) {
            "MC" -> "BME"
            "DE", "F" -> "XETR"
            "PA", "AS", "BR", "LS" -> "Euronext"
            "MI" -> "MTA"
            "L" -> "LSE"
            "SW" -> "SIX"
            else -> return null
        }
        return base to exchange
    }

    private fun interval(tf: Timeframe) = when (tf) {
        Timeframe.H1 -> "1h"
        Timeframe.H4 -> "4h"
        Timeframe.D1 -> "1day"
        Timeframe.W1 -> "1week"
        Timeframe.MN, Timeframe.Y1 -> "1month"
    }

    private fun params(sym: Pair<String, String?>, key: String): String {
        val ex = sym.second?.let { "&exchange=${Net.enc(it)}" } ?: ""
        return "symbol=${Net.enc(sym.first)}$ex&apikey=${Net.enc(key)}"
    }

    suspend fun candles(yahooSymbol: String, tf: Timeframe, key: String): Series? {
        val sym = mapSymbol(yahooSymbol) ?: return null
        val size = if (tf == Timeframe.MN || tf == Timeframe.Y1) 600 else 1300
        val url = "$BASE/time_series?${params(sym, key)}&interval=${interval(tf)}&outputsize=$size&order=ASC"
        val root = JSONObject(Net.get(url))
        if (root.optString("status") != "ok") return null
        val values = root.optJSONArray("values") ?: return null
        val list = ArrayList<Candle>(values.length())
        for (i in 0 until values.length()) {
            val v = values.getJSONObject(i)
            val t = parseDate(v.getString("datetime"))
            list += Candle(
                t,
                v.getString("open").toDouble(),
                v.getString("high").toDouble(),
                v.getString("low").toDouble(),
                v.getString("close").toDouble(),
                v.optString("volume", "0").toDoubleOrNull() ?: 0.0
            )
        }
        list.sortBy { it.t }
        val out = if (tf == Timeframe.Y1) Agg.yearly(list) else list
        val currency = root.optJSONObject("meta")?.optString("currency", "") ?: ""
        return Series(out, "Twelve Data", currency)
    }

    suspend fun quote(yahooSymbol: String, key: String): Quote? {
        val sym = mapSymbol(yahooSymbol) ?: return null
        val root = JSONObject(Net.get("$BASE/quote?${params(sym, key)}"))
        if (root.has("code") && root.optString("status") == "error") return null
        val close = root.optString("close").toDoubleOrNull() ?: return null
        val prev = root.optString("previous_close").toDoubleOrNull() ?: close
        return Quote(close, prev, root.optString("currency", ""), "Twelve Data")
    }

    suspend fun test(key: String): String {
        val root = JSONObject(Net.get("$BASE/quote?symbol=AAPL&apikey=${Net.enc(key)}"))
        return if (root.optString("close").isNotBlank()) "Conexión correcta (AAPL ${root.optString("close")})"
        else "Error: ${root.optString("message", "respuesta inesperada")}"
    }

    private fun parseDate(s: String): Long = try {
        if (s.length > 10) LocalDateTime.parse(s.replace(' ', 'T')).toEpochSecond(ZoneOffset.UTC)
        else LocalDate.parse(s).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
    } catch (e: Exception) {
        0L
    }
}

// =====================================================================
//  Financial Modeling Prep (con clave): precio objetivo, consenso, noticias, resultados.
// =====================================================================
object Fmp {
    private const val BASE = "https://financialmodelingprep.com/stable"

    private suspend fun getArray(path: String): JSONArray? {
        val body = Net.get("$BASE/$path").trim()
        return if (body.startsWith("[")) JSONArray(body) else null
    }

    suspend fun target(symbol: String, key: String): PriceTarget? {
        val arr = getArray("price-target-consensus?symbol=${Net.enc(symbol)}&apikey=${Net.enc(key)}") ?: return null
        val o = arr.optJSONObject(0) ?: return null
        val avg = o.optDouble("targetConsensus", Double.NaN)
        if (avg.isNaN() || avg <= 0) return null
        return PriceTarget(
            low = o.optDouble("targetLow", avg),
            avg = avg,
            high = o.optDouble("targetHigh", avg),
            median = o.optDouble("targetMedian", avg)
        )
    }

    suspend fun ratings(symbol: String, key: String): Ratings? {
        val arr = getArray("grades-consensus?symbol=${Net.enc(symbol)}&apikey=${Net.enc(key)}") ?: return null
        val o = arr.optJSONObject(0) ?: return null
        val r = Ratings(
            o.optInt("strongBuy", 0), o.optInt("buy", 0), o.optInt("hold", 0),
            o.optInt("sell", 0), o.optInt("strongSell", 0), o.optString("consensus", "")
        )
        return if (r.total > 0) r else null
    }

    suspend fun news(symbols: List<String>, key: String): List<NewsItem> {
        if (symbols.isEmpty()) return emptyList()
        val arr = getArray("news/stock?symbols=${Net.enc(symbols.joinToString(","))}&limit=40&apikey=${Net.enc(key)}")
            ?: return emptyList()
        val out = mutableListOf<NewsItem>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.optString("title", "")
            if (title.isBlank()) continue
            val text = o.optString("text", "")
            out += NewsItem(
                title = title,
                publisher = o.optString("publisher", o.optString("site", "")),
                time = parseTime(o.optString("publishedDate", "")),
                url = o.optString("url", ""),
                symbol = o.optString("symbol", ""),
                summary = text.take(220),
                cat = NewsClassifier.classify(title, text)
            )
        }
        return out
    }

    suspend fun earnings(from: String, to: String, key: String): List<EarningsEvent> {
        val arr = getArray("earnings-calendar?from=$from&to=$to&apikey=${Net.enc(key)}") ?: return emptyList()
        val out = mutableListOf<EarningsEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val est = o.optDouble("epsEstimated", Double.NaN)
            out += EarningsEvent(o.optString("symbol", ""), o.optString("date", ""), if (est.isNaN()) null else est)
        }
        return out
    }

    suspend fun test(key: String): String {
        val t = target("AAPL", key)
        return if (t != null) "Conexión correcta (objetivo AAPL ${"%.2f".format(t.avg)})"
        else "No se obtuvieron datos: revisa la clave o el plan"
    }

    private fun parseTime(s: String): Long = try {
        LocalDateTime.parse(s.trim().replace(' ', 'T')).toEpochSecond(ZoneOffset.UTC)
    } catch (e: Exception) {
        Instant.now().epochSecond
    }
}

// =====================================================================
//  Agregación de velas (4 horas y anual)
// =====================================================================
object Agg {
    private fun merge(group: List<Candle>): Candle = Candle(
        t = group.first().t,
        o = group.first().o,
        h = group.maxOf { it.h },
        l = group.minOf { it.l },
        c = group.last().c,
        v = group.sumOf { it.v }
    )

    fun yearly(list: List<Candle>): List<Candle> =
        list.groupBy { Instant.ofEpochSecond(it.t).atZone(ZoneOffset.UTC).year }
            .toSortedMap()
            .values
            .map { merge(it) }

    fun fourHours(list: List<Candle>): List<Candle> {
        val out = mutableListOf<Candle>()
        var day = -1L
        var group = mutableListOf<Candle>()
        for (c in list) {
            val d = c.t / 86_400
            if (d != day || group.size == 4) {
                if (group.isNotEmpty()) out += merge(group)
                group = mutableListOf()
                day = d
            }
            group += c
        }
        if (group.isNotEmpty()) out += merge(group)
        return out
    }
}

// =====================================================================
//  Clasificación de noticias por palabras clave
// =====================================================================
object NewsClassifier {
    private val results = listOf(
        "earnings", "results", "quarter", "quarterly", "q1 ", "q2 ", "q3 ", "q4 ", "revenue", "profit", "eps",
        "resultados", "trimestre", "beneficio", "ingresos", "guidance"
    )
    private val analysts = listOf(
        "price target", "upgrade", "downgrade", "analyst", "rating", "outperform", "underperform",
        "overweight", "underweight", "precio objetivo", "recomendacion", "analista"
    )
    private val commodities = listOf(
        "opec", "opep", "oil", "crude", "brent", "wti", "gold", "silver", "copper", "wheat", "corn", "soy",
        "natural gas", "supply", "production", "output", "inventor", "harvest", "drought", "hurricane", "weather",
        "frost", "flood", "petroleo", "crudo", "oro", "plata", "cobre", "trigo", "maiz", "produccion",
        "sobreproduccion", "cosecha", "sequia", "huracan", "clima", "heladas", "inundacion"
    )
    private val macro = listOf(
        "fed ", "federal reserve", "ecb", "bce", "inflation", "cpi", "interest rate", "rate cut", "rate hike",
        "jobs report", "payrolls", "gdp", "recession", "tariff", "inflacion", "ipc", "tipos de interes", "pib",
        "aranceles", "banco central"
    )

    private fun norm(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun classify(title: String, text: String): NewsCat {
        val s = " " + norm("$title ${text.take(300)}") + " "
        return when {
            results.any { s.contains(it) } -> NewsCat.RESULTS
            analysts.any { s.contains(it) } -> NewsCat.ANALYSTS
            commodities.any { s.contains(it) } -> NewsCat.COMMODITIES
            macro.any { s.contains(it) } -> NewsCat.MACRO
            else -> NewsCat.GENERAL
        }
    }
}

// =====================================================================
//  Repositorio: elige la fuente y guarda en caché
// =====================================================================
class Repo(private val store: Store) {
    private data class Cached<T>(val time: Long, val value: T)

    private val seriesCache = HashMap<String, Cached<Series>>()

    private fun ttl(tf: Timeframe): Long = when (tf) {
        Timeframe.H1, Timeframe.H4 -> 60_000L
        Timeframe.D1 -> 120_000L
        else -> 15 * 60_000L
    }

    private fun twelveKey(): String? = store.twelveKey.takeIf { it.isNotBlank() && store.useTwelve }

    suspend fun candles(inst: Instrument, tf: Timeframe, force: Boolean = false): Series {
        val key = "${inst.symbol}|$tf"
        val now = System.currentTimeMillis()
        if (!force) seriesCache[key]?.let { if (now - it.time < ttl(tf)) return it.value }
        val tk = twelveKey()
        val fromTwelve = if (tk != null) runCatching { Twelve.candles(inst.symbol, tf, tk) }.getOrNull() else null
        val series = if (fromTwelve != null && fromTwelve.candles.size >= 30) fromTwelve else Yahoo.candles(inst.symbol, tf)
        seriesCache[key] = Cached(now, series)
        return series
    }

    suspend fun quote(inst: Instrument): Quote {
        val tk = twelveKey()
        if (tk != null) {
            runCatching { Twelve.quote(inst.symbol, tk) }.getOrNull()?.let { return it }
        }
        return Yahoo.quote(inst.symbol)
    }

    suspend fun target(inst: Instrument): Pair<PriceTarget?, Ratings?> {
        val key = store.fmpKey
        if (key.isBlank()) return null to null
        val t = runCatching { Fmp.target(inst.symbol, key) }.getOrNull()
        val r = runCatching { Fmp.ratings(inst.symbol, key) }.getOrNull()
        return t to r
    }

    suspend fun news(insts: List<Instrument>): List<NewsItem> {
        val key = store.fmpKey
        val out = mutableListOf<NewsItem>()
        val stockSymbols = insts.filter { it.type == AssetType.STOCK }.map { it.symbol }
        if (key.isNotBlank() && stockSymbols.isNotEmpty()) {
            out += runCatching { Fmp.news(stockSymbols, key) }.getOrDefault(emptyList())
        }
        val covered = out.map { it.symbol }.toSet()
        for (inst in insts) {
            if (inst.symbol in covered) continue
            out += runCatching { Yahoo.news(inst.symbol, 6) }.getOrDefault(emptyList())
        }
        return out.distinctBy { it.title }.sortedByDescending { it.time }
    }

    suspend fun earnings(insts: List<Instrument>): List<EarningsEvent> {
        val key = store.fmpKey
        if (key.isBlank()) return emptyList()
        val today = LocalDate.now()
        val all = runCatching { Fmp.earnings(today.toString(), today.plusDays(60).toString(), key) }
            .getOrDefault(emptyList())
        val wanted = insts.map { it.symbol.uppercase() }.toSet()
        return all.filter { it.symbol.uppercase() in wanted }.sortedBy { it.date }
    }
}
