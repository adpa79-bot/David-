package com.mitrading.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Guarda favoritos, alertas, cartera y ajustes en el propio dispositivo. */
class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("mitrading", Context.MODE_PRIVATE)

    var twelveKey: String
        get() = p.getString("twelveKey", "") ?: ""
        set(v) { p.edit().putString("twelveKey", v.trim()).apply() }

    var fmpKey: String
        get() = p.getString("fmpKey", "") ?: ""
        set(v) { p.edit().putString("fmpKey", v.trim()).apply() }

    var useTwelve: Boolean
        get() = p.getBoolean("useTwelve", true)
        set(v) { p.edit().putBoolean("useTwelve", v).apply() }

    // ---------- Favoritos ----------
    fun favorites(): List<Instrument> {
        val raw = p.getString("favs", null) ?: return DEFAULT_FAVORITES
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { instrumentFrom(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            DEFAULT_FAVORITES
        }
    }

    fun saveFavorites(list: List<Instrument>) {
        val arr = JSONArray()
        list.forEach { arr.put(instrumentTo(it)) }
        p.edit().putString("favs", arr.toString()).apply()
    }

    // ---------- Alertas ----------
    fun alerts(): List<AlertRule> {
        val raw = p.getString("alerts", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull {
                val o = arr.getJSONObject(it)
                val cond = runCatching { AlertCond.valueOf(o.getString("cond")) }.getOrNull() ?: return@mapNotNull null
                AlertRule(
                    id = o.getLong("id"),
                    symbol = o.getString("symbol"),
                    name = o.optString("name", o.getString("symbol")),
                    cond = cond,
                    value = o.optDouble("value", 0.0),
                    enabled = o.optBoolean("enabled", true),
                    armed = o.optBoolean("armed", true),
                    lastTriggered = o.optLong("last", 0L)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAlerts(list: List<AlertRule>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("symbol", it.symbol)
                    .put("name", it.name)
                    .put("cond", it.cond.name)
                    .put("value", it.value)
                    .put("enabled", it.enabled)
                    .put("armed", it.armed)
                    .put("last", it.lastTriggered)
            )
        }
        p.edit().putString("alerts", arr.toString()).apply()
    }

    // ---------- Registro de alertas disparadas ----------
    fun log(): List<LogEntry> {
        val raw = p.getString("log", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                LogEntry(o.getLong("t"), o.getString("x"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addLog(entry: LogEntry) {
        val list = (listOf(entry) + log()).take(30)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.time).put("x", it.text)) }
        p.edit().putString("log", arr.toString()).apply()
    }

    // ---------- Cartera ----------
    fun positions(): List<Position> {
        val raw = p.getString("positions", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Position(
                    symbol = o.getString("symbol"),
                    name = o.optString("name", o.getString("symbol")),
                    qty = o.optDouble("qty", 0.0),
                    avgPrice = o.optDouble("avg", 0.0),
                    broker = o.optString("broker", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePositions(list: List<Position>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("symbol", it.symbol)
                    .put("name", it.name)
                    .put("qty", it.qty)
                    .put("avg", it.avgPrice)
                    .put("broker", it.broker)
            )
        }
        p.edit().putString("positions", arr.toString()).apply()
    }

    companion object {
        fun instrumentTo(i: Instrument): JSONObject = JSONObject()
            .put("symbol", i.symbol)
            .put("name", i.name)
            .put("type", i.type.name)
            .put("exchange", i.exchange)
            .put("currency", i.currency)

        fun instrumentFrom(o: JSONObject): Instrument = Instrument(
            symbol = o.getString("symbol"),
            name = o.optString("name", o.getString("symbol")),
            type = runCatching { AssetType.valueOf(o.optString("type", "OTHER")) }.getOrDefault(AssetType.OTHER),
            exchange = o.optString("exchange", ""),
            currency = o.optString("currency", "")
        )

        val DEFAULT_FAVORITES = listOf(
            Instrument("IBE.MC", "Iberdrola", AssetType.STOCK, "BME", "EUR"),
            Instrument("SAN.MC", "Banco Santander", AssetType.STOCK, "BME", "EUR"),
            Instrument("AAPL", "Apple", AssetType.STOCK, "NASDAQ", "USD"),
            Instrument("NVDA", "NVIDIA", AssetType.STOCK, "NASDAQ", "USD"),
            Instrument("^GSPC", "US500 · S&P 500", AssetType.INDEX, "Índice", "USD"),
            Instrument("^GDAXI", "DE40 · DAX", AssetType.INDEX, "Índice", "EUR"),
            Instrument("^IBEX", "SPA35 · IBEX 35", AssetType.INDEX, "Índice", "EUR"),
            Instrument("GC=F", "Oro (futuro)", AssetType.FUTURE, "COMEX", "USD"),
            Instrument("BZ=F", "Petróleo Brent (futuro)", AssetType.FUTURE, "ICE", "USD")
        )

        /** Accesos rápidos a índices (equivalentes a los CFD) y materias primas. */
        val QUICK_MARKETS = listOf(
            Instrument("^GSPC", "US500 · S&P 500", AssetType.INDEX, "Índice", "USD"),
            Instrument("^NDX", "US100 · Nasdaq 100", AssetType.INDEX, "Índice", "USD"),
            Instrument("^DJI", "US30 · Dow Jones", AssetType.INDEX, "Índice", "USD"),
            Instrument("^GDAXI", "DE40 · DAX", AssetType.INDEX, "Índice", "EUR"),
            Instrument("^IBEX", "SPA35 · IBEX 35", AssetType.INDEX, "Índice", "EUR"),
            Instrument("^STOXX50E", "EU50 · Euro Stoxx 50", AssetType.INDEX, "Índice", "EUR"),
            Instrument("^FCHI", "FRA40 · CAC 40", AssetType.INDEX, "Índice", "EUR"),
            Instrument("^FTSE", "UK100 · FTSE 100", AssetType.INDEX, "Índice", "GBP"),
            Instrument("ES=F", "Futuro S&P 500", AssetType.FUTURE, "CME", "USD"),
            Instrument("NQ=F", "Futuro Nasdaq 100", AssetType.FUTURE, "CME", "USD"),
            Instrument("GC=F", "Oro (futuro)", AssetType.FUTURE, "COMEX", "USD"),
            Instrument("SI=F", "Plata (futuro)", AssetType.FUTURE, "COMEX", "USD"),
            Instrument("BZ=F", "Petróleo Brent (futuro)", AssetType.FUTURE, "ICE", "USD"),
            Instrument("CL=F", "Petróleo WTI (futuro)", AssetType.FUTURE, "NYMEX", "USD"),
            Instrument("NG=F", "Gas natural (futuro)", AssetType.FUTURE, "NYMEX", "USD"),
            Instrument("ZW=F", "Trigo (futuro)", AssetType.FUTURE, "CBOT", "USD"),
            Instrument("HG=F", "Cobre (futuro)", AssetType.FUTURE, "COMEX", "USD")
        )
    }
}
