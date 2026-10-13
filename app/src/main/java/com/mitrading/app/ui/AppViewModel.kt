package com.mitrading.app.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mitrading.app.alerts.AlertWorker
import com.mitrading.app.data.AlertCond
import com.mitrading.app.data.AlertRule
import com.mitrading.app.data.AssetType
import com.mitrading.app.data.Candle
import com.mitrading.app.data.EarningsEvent
import com.mitrading.app.data.Fmp
import com.mitrading.app.data.Importer
import com.mitrading.app.data.Instrument
import com.mitrading.app.data.LogEntry
import com.mitrading.app.data.NewsItem
import com.mitrading.app.data.Position
import com.mitrading.app.data.PriceTarget
import com.mitrading.app.data.Quote
import com.mitrading.app.data.Ratings
import com.mitrading.app.data.Repo
import com.mitrading.app.data.Store
import com.mitrading.app.data.Timeframe
import com.mitrading.app.data.Twelve
import com.mitrading.app.data.Yahoo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class Screen(val label: String) {
    MAIN("Favoritos"),
    SEARCH("Buscar"),
    PORTFOLIO("Cartera"),
    ALERTS("Alertas"),
    NEWS("Noticias"),
    SETTINGS("Ajustes")
}

enum class Osc { RSI, STOCH }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val repo = Repo(store)

    var screen by mutableStateOf(Screen.MAIN)

    // ---------- Favoritos y cotizaciones ----------
    val favorites = mutableStateListOf<Instrument>().apply { addAll(store.favorites()) }
    val quotes = mutableStateMapOf<String, Quote>()
    var lastRefresh by mutableStateOf(0L)

    // ---------- Gráfico ----------
    var selected by mutableStateOf(favorites.firstOrNull())
    var tf by mutableStateOf(Timeframe.D1)
    var candles by mutableStateOf<List<Candle>>(emptyList())
    var dailyCandles by mutableStateOf<List<Candle>>(emptyList())
    var chartSource by mutableStateOf("")
    var chartCurrency by mutableStateOf("")
    var chartLoading by mutableStateOf(false)
    var chartError by mutableStateOf<String?>(null)
    var showMa by mutableStateOf(true)
    var showSr by mutableStateOf(true)
    var osc by mutableStateOf(Osc.RSI)
    var chartFull by mutableStateOf(false)
    private var chartJob: Job? = null

    // ---------- Detalle ----------
    var target by mutableStateOf<PriceTarget?>(null)
    var ratings by mutableStateOf<Ratings?>(null)
    var instNews by mutableStateOf<List<NewsItem>>(emptyList())
    private var detailJob: Job? = null

    // ---------- Búsqueda ----------
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<Instrument>>(emptyList())
    var searching by mutableStateOf(false)
    var searchError by mutableStateOf<String?>(null)
    private var searchJob: Job? = null

    // ---------- Alertas ----------
    val alerts = mutableStateListOf<AlertRule>().apply { addAll(store.alerts()) }
    var alertLog by mutableStateOf(store.log())
    var alertPresetSymbol by mutableStateOf<String?>(null)
    var checkingAlerts by mutableStateOf(false)

    // ---------- Noticias ----------
    var news by mutableStateOf<List<NewsItem>>(emptyList())
    var earnings by mutableStateOf<List<EarningsEvent>>(emptyList())
    var newsLoading by mutableStateOf(false)
    private var newsLoadedAt = 0L

    // ---------- Cartera ----------
    val positions = mutableStateListOf<Position>().apply { addAll(store.positions()) }
    var importMessage by mutableStateOf<String?>(null)
    var importing by mutableStateOf(false)

    // ---------- Ajustes ----------
    var twelveKey by mutableStateOf(store.twelveKey)
    var fmpKey by mutableStateOf(store.fmpKey)
    var useTwelve by mutableStateOf(store.useTwelve)
    var twelveTest by mutableStateOf<String?>(null)
    var fmpTest by mutableStateOf<String?>(null)

    init {
        selected?.let { select(it) }
        viewModelScope.launch {
            while (isActive) {
                refreshQuotes()
                delay(60_000)
            }
        }
    }

    // =================================================================
    //  Cotizaciones
    // =================================================================
    suspend fun refreshQuotes() {
        val list = (favorites.toList() + positionInstruments()).distinctBy { it.symbol }
        val sem = Semaphore(4)
        val results = withContext(Dispatchers.IO) {
            list.map { inst ->
                async { sem.withPermit { inst.symbol to runCatching { repo.quote(inst) }.getOrNull() } }
            }.awaitAll()
        }
        results.forEach { (sym, q) -> if (q != null) quotes[sym] = q }
        lastRefresh = System.currentTimeMillis()
    }

    fun refreshAll() {
        viewModelScope.launch { refreshQuotes() }
        selected?.let { loadChart(it, force = true) }
    }

    // =================================================================
    //  Favoritos
    // =================================================================
    fun isFavorite(symbol: String) = favorites.any { it.symbol == symbol }

    fun toggleFavorite(inst: Instrument) {
        val idx = favorites.indexOfFirst { it.symbol == inst.symbol }
        if (idx >= 0) favorites.removeAt(idx) else {
            favorites.add(inst)
            viewModelScope.launch {
                runCatching { repo.quote(inst) }.getOrNull()?.let { quotes[inst.symbol] = it }
            }
        }
        store.saveFavorites(favorites.toList())
    }

    // =================================================================
    //  Gráfico y detalle
    // =================================================================
    fun select(inst: Instrument) {
        selected = inst
        loadChart(inst)
        loadDetail(inst)
    }

    fun open(inst: Instrument) {
        select(inst)
        screen = Screen.MAIN
    }

    fun changeTf(newTf: Timeframe) {
        tf = newTf
        selected?.let { loadChart(it) }
    }

    private fun loadChart(inst: Instrument, force: Boolean = false) {
        chartJob?.cancel()
        chartJob = viewModelScope.launch {
            chartLoading = true
            chartError = null
            try {
                val s = repo.candles(inst, tf, force)
                if (selected?.symbol != inst.symbol) return@launch
                candles = s.candles
                chartSource = s.source
                chartCurrency = s.currency.ifBlank { quotes[inst.symbol]?.currency ?: "" }
                dailyCandles = if (tf == Timeframe.D1) s.candles else
                    runCatching { repo.candles(inst, Timeframe.D1).candles }.getOrDefault(emptyList())
                if (s.candles.isEmpty()) chartError = "No hay datos para este periodo."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                candles = emptyList()
                chartError = "No se pudieron cargar los datos (${e.message ?: "error de red"})."
            } finally {
                chartLoading = false
            }
        }
    }

    private fun loadDetail(inst: Instrument) {
        detailJob?.cancel()
        target = null
        ratings = null
        instNews = emptyList()
        detailJob = viewModelScope.launch {
            if (quotes[inst.symbol] == null) {
                runCatching { repo.quote(inst) }.getOrNull()?.let { quotes[inst.symbol] = it }
            }
            if (inst.type == AssetType.STOCK) {
                val (t, r) = repo.target(inst)
                target = t
                ratings = r
            }
            instNews = runCatching { repo.news(listOf(inst)) }.getOrDefault(emptyList()).take(6)
        }
    }

    // =================================================================
    //  Búsqueda
    // =================================================================
    fun search(q: String) {
        searchQuery = q
        searchJob?.cancel()
        if (q.isBlank()) {
            searchResults = emptyList()
            searchError = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            searching = true
            searchError = null
            try {
                searchResults = Yahoo.search(q).distinctBy { it.symbol }
                if (searchResults.isEmpty()) searchError = "Sin resultados. Prueba con el ticker (p. ej. AAPL) o el ISIN."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                searchError = "Error de conexión al buscar."
            } finally {
                searching = false
            }
        }
    }

    // =================================================================
    //  Alertas
    // =================================================================
    fun addAlert(inst: Instrument, cond: AlertCond, value: Double) {
        val rule = AlertRule(
            id = System.currentTimeMillis(), symbol = inst.symbol, name = inst.name, cond = cond, value = value
        )
        alerts.add(0, rule)
        store.saveAlerts(alerts.toList())
    }

    fun toggleAlert(rule: AlertRule) {
        val i = alerts.indexOfFirst { it.id == rule.id }
        if (i >= 0) {
            alerts[i] = rule.copy(enabled = !rule.enabled, armed = true)
            store.saveAlerts(alerts.toList())
        }
    }

    fun removeAlert(rule: AlertRule) {
        alerts.removeAll { it.id == rule.id }
        store.saveAlerts(alerts.toList())
    }

    fun checkAlertsNow(context: Context) {
        viewModelScope.launch {
            checkingAlerts = true
            runCatching { AlertWorker.checkAlerts(context.applicationContext) }
            alerts.clear()
            alerts.addAll(store.alerts())
            alertLog = store.log()
            checkingAlerts = false
        }
    }

    fun reloadAlerts() {
        alerts.clear()
        alerts.addAll(store.alerts())
        alertLog = store.log()
    }

    // =================================================================
    //  Noticias
    // =================================================================
    fun loadNews(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - newsLoadedAt < 10 * 60_000 && news.isNotEmpty()) return
        viewModelScope.launch {
            newsLoading = true
            val insts = favorites.toList()
            news = runCatching { repo.news(insts) }.getOrDefault(emptyList())
            earnings = runCatching { repo.earnings(insts) }.getOrDefault(emptyList())
            newsLoadedAt = System.currentTimeMillis()
            newsLoading = false
        }
    }

    // =================================================================
    //  Cartera
    // =================================================================
    private fun positionInstruments(): List<Instrument> = positions
        .filter { !it.symbol.startsWith("ISIN:") && !it.symbol.startsWith("NAME:") }
        .map { Instrument(it.symbol, it.name, AssetType.OTHER) }

    fun importFile(context: Context, uri: Uri, broker: String) {
        viewModelScope.launch {
            importing = true
            importMessage = null
            val result = withContext(Dispatchers.IO) { Importer.import(context, uri, broker) }
            if (result.positions.isEmpty()) {
                importMessage = result.message
                importing = false
                return@launch
            }
            // Resolver ISIN o nombres a símbolos buscando en Yahoo.
            val resolved = result.positions.map { p ->
                if (p.symbol.startsWith("ISIN:") || p.symbol.startsWith("NAME:")) {
                    val q = p.symbol.substringAfter(':')
                    val hit = runCatching { Yahoo.search(q).firstOrNull() }.getOrNull()
                    if (hit != null) p.copy(symbol = hit.symbol, name = if (p.name.startsWith("ISIN:") || p.name.isBlank()) hit.name else p.name) else p
                } else p
            }
            val others = positions.filter { it.broker != broker }
            positions.clear()
            positions.addAll(others + resolved)
            store.savePositions(positions.toList())
            importMessage = result.message
            importing = false
            refreshQuotes()
        }
    }

    fun addPosition(symbolRaw: String, qty: Double, price: Double, broker: String) {
        viewModelScope.launch {
            val sym = Importer.mapBrokerSymbol(symbolRaw)
            val hit = runCatching { Yahoo.search(sym).firstOrNull { it.symbol.equals(sym, true) } ?: Yahoo.search(sym).firstOrNull() }.getOrNull()
            val p = Position(hit?.symbol ?: sym, hit?.name ?: sym, qty, price, broker)
            positions.add(p)
            store.savePositions(positions.toList())
            refreshQuotes()
        }
    }

    fun removePosition(p: Position) {
        positions.remove(p)
        store.savePositions(positions.toList())
    }

    fun addPositionsToFavorites() {
        for (p in positions) {
            if (p.symbol.startsWith("ISIN:") || p.symbol.startsWith("NAME:")) continue
            if (!isFavorite(p.symbol)) favorites.add(Instrument(p.symbol, p.name, guessType(p.symbol)))
        }
        store.saveFavorites(favorites.toList())
    }

    private fun guessType(sym: String): AssetType = when {
        sym.startsWith("^") -> AssetType.INDEX
        sym.endsWith("=F") -> AssetType.FUTURE
        else -> AssetType.STOCK
    }

    // =================================================================
    //  Ajustes
    // =================================================================
    fun saveKeys() {
        store.twelveKey = twelveKey
        store.fmpKey = fmpKey
        store.useTwelve = useTwelve
        selected?.let { loadChart(it, force = true); loadDetail(it) }
        viewModelScope.launch { refreshQuotes() }
    }

    fun testTwelve() {
        viewModelScope.launch {
            twelveTest = "Probando…"
            twelveTest = runCatching { Twelve.test(twelveKey.trim()) }.getOrElse { "Error de conexión: ${it.message}" }
        }
    }

    fun testFmp() {
        viewModelScope.launch {
            fmpTest = "Probando…"
            fmpTest = runCatching { Fmp.test(fmpKey.trim()) }.getOrElse { "Error de conexión: ${it.message}" }
        }
    }

    // =================================================================
    //  Texto compartido desde otras apps
    // =================================================================
    fun handleShared(text: String) {
        val clean = text.lines().firstOrNull { it.isNotBlank() }?.trim()?.take(60) ?: return
        screen = Screen.SEARCH
        search(clean)
    }

    // =================================================================
    //  Abrir apps de los brokers
    // =================================================================
    fun openBroker(context: Context, broker: String) {
        val packages = if (broker == "XTB") listOf("pl.xtb.xstation5", "com.xtb.xstation5", "pl.xtb.xstation")
        else listOf("de.traderepublic.app")
        val pm = context.packageManager
        for (pkg in packages) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
        }
        val query = if (broker == "XTB") "XTB" else "Trade Republic"
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(query)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(query)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun logText(): List<LogEntry> = alertLog
}
