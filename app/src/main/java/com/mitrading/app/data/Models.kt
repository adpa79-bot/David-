package com.mitrading.app.data

enum class AssetType(val label: String) {
    STOCK("Acción"),
    INDEX("Índice"),
    FUTURE("Futuro"),
    ETF("ETF/ETC"),
    OTHER("Otro");

    companion object {
        fun fromYahoo(quoteType: String?): AssetType = when (quoteType?.uppercase()) {
            "EQUITY" -> STOCK
            "INDEX" -> INDEX
            "FUTURE" -> FUTURE
            "ETF", "MUTUALFUND" -> ETF
            else -> OTHER
        }
    }
}

/** Un instrumento. [symbol] es el símbolo de Yahoo (IBE.MC, AAPL, ^GSPC, GC=F…). */
data class Instrument(
    val symbol: String,
    val name: String,
    val type: AssetType,
    val exchange: String = "",
    val currency: String = ""
)

data class Candle(
    val t: Long, // segundos epoch (UTC)
    val o: Double,
    val h: Double,
    val l: Double,
    val c: Double,
    val v: Double
)

data class Quote(
    val price: Double,
    val prevClose: Double,
    val currency: String,
    val source: String
) {
    val change: Double get() = price - prevClose
    val changePct: Double get() = if (prevClose != 0.0) change / prevClose * 100.0 else 0.0
}

data class Series(val candles: List<Candle>, val source: String, val currency: String)

enum class Timeframe(val label: String, val desc: String) {
    H1("1h", "de 1 hora"),
    H4("4h", "de 4 horas"),
    D1("Día", "diarias"),
    W1("Sem", "semanales"),
    MN("Mes", "mensuales"),
    Y1("Año", "anuales")
}

enum class AlertCond(val label: String) {
    ABOVE("Precio ≥"),
    BELOW("Precio ≤"),
    RSI_HIGH("RSI sobrecompra"),
    RSI_LOW("RSI sobreventa"),
    PCT("Variación diaria %"),
    MA50_CROSS("Cruce MM50")
}

data class AlertRule(
    val id: Long,
    val symbol: String,
    val name: String,
    val cond: AlertCond,
    val value: Double,
    val enabled: Boolean = true,
    val armed: Boolean = true,
    val lastTriggered: Long = 0L
)

data class Position(
    val symbol: String,
    val name: String,
    val qty: Double,
    val avgPrice: Double,
    val broker: String
)

enum class NewsCat(val label: String) {
    RESULTS("Resultados"),
    ANALYSTS("Analistas"),
    COMMODITIES("Materias primas"),
    MACRO("Macro"),
    GENERAL("General")
}

data class NewsItem(
    val title: String,
    val publisher: String,
    val time: Long, // segundos epoch
    val url: String,
    val symbol: String,
    val summary: String = "",
    val cat: NewsCat = NewsCat.GENERAL
)

data class PriceTarget(val low: Double, val avg: Double, val high: Double, val median: Double)

data class Ratings(
    val strongBuy: Int,
    val buy: Int,
    val hold: Int,
    val sell: Int,
    val strongSell: Int,
    val consensus: String
) {
    val total: Int get() = strongBuy + buy + hold + sell + strongSell
}

data class EarningsEvent(val symbol: String, val date: String, val epsEstimated: Double?)

data class LogEntry(val time: Long, val text: String)
