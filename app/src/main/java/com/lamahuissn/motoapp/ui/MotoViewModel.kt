package com.lamahuissn.motoapp.ui

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.ApiCall
import com.lamahuissn.motoapp.data.AreaSheet
import com.lamahuissn.motoapp.data.AreasBoard
import com.lamahuissn.motoapp.data.AttemptKey
import com.lamahuissn.motoapp.data.CollectionAnswer
import com.lamahuissn.motoapp.data.HandoverAnswer
import com.lamahuissn.motoapp.data.House
import com.lamahuissn.motoapp.data.ManagerBoard
import com.lamahuissn.motoapp.data.MotoApi
import com.lamahuissn.motoapp.data.MotoArea
import com.lamahuissn.motoapp.data.OfflineCache
import com.lamahuissn.motoapp.data.QueueDecision
import com.lamahuissn.motoapp.data.QueueRunner
import com.lamahuissn.motoapp.data.QueueState
import com.lamahuissn.motoapp.data.QueuedWrite
import com.lamahuissn.motoapp.data.ReadingAnswer
import com.lamahuissn.motoapp.data.ReceiptMessage
import com.lamahuissn.motoapp.data.ReversalAnswer
import com.lamahuissn.motoapp.data.SearchHit
import com.lamahuissn.motoapp.data.Session
import com.lamahuissn.motoapp.data.WalletSummary
import com.lamahuissn.motoapp.data.TokenVault
import com.lamahuissn.motoapp.data.WriteQueueStore
import com.lamahuissn.motoapp.work.QueueNotifier
import com.lamahuissn.motoapp.work.QueueSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** Where the collector is. */
sealed interface Screen {
    data object Login : Screen
    data object Areas : Screen
    /** The manager's board — the home of a manager's session (spec 009). */
    data object Manager : Screen
    /** The writes the office has not seen yet (spec 010). */
    data object Queue : Screen
    /** The collector's own purse (spec 011) — the one screen about him rather than about a house. */
    data object Wallet : Screen
    data class Houses(val areaId: Int, val areaName: String) : Screen
    data class House(val areaId: Int, val areaName: String, val houseId: Int) : Screen
}

/** Something to tell the collector, in the office's words, with the code for whoever repairs it. */
data class Problem(val message: String, val code: String?)

/** Which of the two writes an outcome came from — the screen shows each one where the web does. */
enum class OutcomeKind { Reading, Collection, Refused, Unknown }

/**
 * What happened to the last reading or collection.
 *
 * `retryable` is the important field: a receipt that never reached the server can be sent again with
 * the SAME key, and the collector must be offered exactly that, not a fresh attempt.
 */
data class Outcome(
    val kind: OutcomeKind,
    val title: String,
    val facts: List<Pair<String, String>> = emptyList(),
    val problem: Problem? = null,
    val retryable: Boolean = false,
    /** The receipt as words, when this outcome is a collection — handed on, not retyped. */
    val receipt: String? = null,
    /**
     * The queue row this outcome is about.
     *
     * A write is a row with a life of its own: it can be applied while the card is still on screen. The key
     * is what lets the card FOLLOW its row instead of freezing the moment it was born in — a card still
     * saying «بانتظار الشبكة» about a payment the office already took is worse than no card.
     */
    val key: String? = null,
)

/**
 * The app's one state holder.
 *
 * One holder rather than a view-model per screen, because these screens are one journey through one
 * session: the token, the area, the sheet, the house and the busy flag all change together.
 *
 * The rules of the money are enforced here rather than in a dialog:
 *
 *  - **One attempt, one key.** A key is made when the collector confirms, kept while the answer is
 *    unknown, and reused for every retry of that attempt — never regenerated, because a regenerated key
 *    is a second receipt for money handed over once.
 *  - **A refusal is an answer.** When the server refuses (`refuse.period_not_invoiced`, `refuse.
 *    reading_lower`), the attempt is over: the office's own sentence is shown and kept on the row, and
 *    the row never retries itself.
 *  - **A write is on disk before it is sent** (spec 010). The queue is the collector's work, not a
 *    variable: a phone that dies in a basement must not take a collection with it.
 */
class MotoViewModel(application: Application) : AndroidViewModel(application) {

    private val vault = TokenVault(application)

    private val _session = MutableStateFlow(vault.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(homeFor(_session.value))
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _board = MutableStateFlow<Load<AreasBoard>>(Load.Idle)
    val board: StateFlow<Load<AreasBoard>> = _board.asStateFlow()

    private val _sheet = MutableStateFlow<Load<AreaSheet>>(Load.Idle)
    val sheet: StateFlow<Load<AreaSheet>> = _sheet.asStateFlow()

    private val _house = MutableStateFlow<Load<House>>(Load.Idle)
    val house: StateFlow<Load<House>> = _house.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()

    private val _outcome = MutableStateFlow<Outcome?>(null)
    val outcome: StateFlow<Outcome?> = _outcome.asStateFlow()

    /**
     * The office's lira rate, as the last read answered it.
     *
     * It belongs to the office, so it comes from the server with every sheet — never typed by a
     * collector from memory, and null while the office has set none (a lira collection then has to be
     * refused, which is the honest outcome).
     */
    private val _lbpRate = MutableStateFlow<Double?>(null)
    val lbpRate: StateFlow<Double?> = _lbpRate.asStateFlow()

    /**
     * What the collector has asked the network by name.
     *
     * Search is not cached like the screens are: it is a question about a moment, and a stale answer to
     * «where is this house» is worse than a wait.
     */
    private val _search = MutableStateFlow<Load<List<SearchHit>>>(Load.Idle)
    val search: StateFlow<Load<List<SearchHit>>> = _search.asStateFlow()

    private var searchJob: Job? = null

    /**
     * The manager's board (spec 009).
     *
     * Read-only by the owner's decision, and only ever loaded for a manager — the endpoint refuses a
     * collector anyway, but the app should not ask for what it has no right to see.
     */
    private val _manager = MutableStateFlow<Load<ManagerBoard>>(Load.Idle)
    val manager: StateFlow<Load<ManagerBoard>> = _manager.asStateFlow()

    /**
     * The manager recording a collector's handover (spec 009, phase B) — the phone's one admin write.
     *
     * It is deliberately NOT queued: a handover the office never answered is not a handover, the manager is
     * standing in the office where the network is, and a payment received by nobody must not read as one.
     * The key is per attempt, so a double tap records one handover.
     */
    private val _handover = MutableStateFlow<Load<HandoverAnswer>>(Load.Idle)
    val handover: StateFlow<Load<HandoverAnswer>> = _handover.asStateFlow()

    /**
     * The manager taking a receipt back (spec 009, phase C) — the most dangerous write in this app.
     *
     * Nothing is queued here and nothing is retried by itself: the reason the manager typed travels with the
     * request, the office's answer is the only thing that makes it true, and the phone never decides what a
     * reversal did to a balance.
     */
    private val _reversal = MutableStateFlow<Load<ReversalAnswer>>(Load.Idle)
    val reversal: StateFlow<Load<ReversalAnswer>> = _reversal.asStateFlow()

    /**
     * The writes the office has not seen yet (spec 010) — on disk before they are sent.
     *
     * The file is the record and the single source of truth: every change is a read-modify-write of it,
     * and the flow below is only the current collector's VIEW of it. A collection the collector is
     * holding cash for must never exist only in a variable that a killed app takes with it.
     */
    private val queueStore = WriteQueueStore(application)

    private val _queue = MutableStateFlow(queueStore.load().filter { it.userId == _session.value?.user?.id })
    val queue: StateFlow<List<QueuedWrite>> = _queue.asStateFlow()

    /** Where the queue was opened from, so its back returns there. */
    private var queueOrigin: Screen? = null

    /**
     * What the last successful read answered, kept in MEMORY only, for as long as this session lives.
     *
     * A collector walks back and forth through the same streets: he opens an area, goes into a house,
     * comes back, opens the next one. Re-reading what he read thirty seconds ago is what makes the app
     * feel slow, so a screen he has already seen opens instantly from here while the same request goes
     * out quietly behind it — the phone shows the office's last answer, then the office's current one,
     * and never a spinner over data it already has.
     *
     * It is deliberately not written to the phone: a copy of the office's money on disk outlives the
     * session, and a stale figure on a house door is worse than a half-second wait.
     */
    private var boardCache: AreasBoard? = null
    private val sheetCache = HashMap<Int, AreaSheet>()
    private val houseCache = HashMap<Int, House>()

    init {
        // A collector who signed in yesterday opens the sheet he is about to walk, not a login box —
        // and a manager opens his board.
        when (homeFor(_session.value)) {
            Screen.Areas -> loadAreas()
            Screen.Manager -> loadManager()
            else -> Unit
        }

        // Anything the last session left owed goes out as soon as the app opens, and again whenever the
        // phone finds a network. The collector should never have to remember to press a button for it.
        flush()
        watchNetwork()
    }

    /**
     * The phone's own idea of «the network is back».
     *
     * Without this the queue only drains when a screen is opened, which is the behaviour that left the
     * collector sitting on one page waiting for a bar of signal. `ACCESS_NETWORK_STATE` is already in
     * the manifest; a phone that refuses the registration still flushes on every screen he opens.
     */
    private fun watchNetwork() {
        val manager = getApplication<Application>().getSystemService(ConnectivityManager::class.java) ?: return

        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    flush()
                }
            })
        }
    }

    /** Where a session's holder belongs: a manager reads his board, a collector walks his areas. */
    private fun homeFor(session: Session?): Screen = when {
        session == null -> Screen.Login
        session.user.isAdmin -> Screen.Manager
        else -> Screen.Areas
    }

    /* ------------------------------------------------------------------ the door */

    fun signIn(identity: String, password: String) {
        if (_busy.value) return

        _busy.value = true
        _problem.value = null

        viewModelScope.launch {
            when (val call = MotoApi.login(identity.trim(), password, deviceName())) {
                is ApiCall.Success -> {
                    vault.save(call.value)
                    _session.value = call.value
                    _screen.value = homeFor(call.value)
                    _busy.value = false
                    refreshQueue()

                    when (_screen.value) {
                        Screen.Manager -> loadManager()
                        else -> loadAreas()
                    }

                    // Whatever this collector left owed on this phone goes out now that he is signed in.
                    flush()
                }

                else -> {
                    _problem.value = problem(call)
                    _busy.value = false
                }
            }
        }
    }

    fun signOut() {
        val token = _session.value?.token
        val me = _session.value?.user?.id

        vault.clear()
        _session.value = null
        boardCache = null
        sheetCache.clear()
        houseCache.clear()

        // The office's figures do not outlive the session that read them — a phone two collectors share
        // must not show one man's balances to the next. The QUEUE is not cleared: that is his work, and
        // it waits for his own session to send it.
        me?.let { offline.clear(it) }
        _staleAsOf.value = null
        _board.value = Load.Idle
        _sheet.value = Load.Idle
        _house.value = Load.Idle
        _manager.value = Load.Idle
        _wallet.value = Load.Idle
        _search.value = Load.Idle
        _problem.value = null
        _outcome.value = null
        refreshQueue()
        _screen.value = Screen.Login

        if (token != null) {
            viewModelScope.launch { MotoApi.logout(token) }
        }
    }

    /* --------------------------------------------------------------- the manager */

    /**
     * The manager's board: the office's month and what each collector is holding.
     *
     * Read-only by decision (spec 009, phase A) — the phone asks for no figures it cannot see a second
     * time on the desk, and it writes nothing at all from a manager's pocket.
     */
    fun loadManager() {
        if (_manager.value !is Load.Ready) {
            _manager.value = Load.Busy
        }

        _problem.value = null

        viewModelScope.launch { readManager() }
    }

    private suspend fun readManager() {
        val token = _session.value?.token ?: return

        when (val call = MotoApi.managerBoard(token)) {
            is ApiCall.Success -> _manager.value = Load.Ready(call.value)

            else -> {
                _problem.value = problem(call)

                if (_manager.value !is Load.Ready) {
                    _manager.value = Load.Failed(_problem.value?.message)
                }
            }
        }
    }

    /**
     * The collector's own purse (spec 011).
     *
     * Read on open and on pull-to-refresh like every other screen, and kept for a day without signal with
     * the time it was read — an old figure labelled as old is better than a blank screen, and the office
     * decides the money at write time anyway.
     */
    private val _wallet = MutableStateFlow<Load<WalletSummary>>(Load.Idle)
    val wallet: StateFlow<Load<WalletSummary>> = _wallet.asStateFlow()

    fun openWallet() {
        _screen.value = Screen.Wallet
        loadWallet()
    }

    fun loadWallet() {
        if (_wallet.value !is Load.Ready) {
            _wallet.value = Load.Busy
        }

        _problem.value = null

        viewModelScope.launch { readWallet() }
    }

    private suspend fun readWallet() {
        val token = _session.value?.token ?: return
        val me = _session.value?.user?.id ?: return

        when (val call = MotoApi.wallet(token, sink = { offline.put(me, OfflineCache.WALLET, it) })) {
            is ApiCall.Success -> {
                _staleAsOf.value = null
                _wallet.value = Load.Ready(call.value)
            }

            else -> {
                _problem.value = problem(call)

                // Only when the session holds nothing: a live answer is never overwritten by an older copy.
                if (_wallet.value !is Load.Ready) {
                    val stored = offline.body(me, OfflineCache.WALLET)?.let { MotoApi.parseWalletBody(it) }

                    if (stored != null) {
                        _staleAsOf.value = offline.storedAt(me, OfflineCache.WALLET)
                        _wallet.value = Load.Ready(stored)
                    } else {
                        _wallet.value = Load.Failed(_problem.value?.message)
                    }
                }
            }
        }
    }

    fun openAreas() {
        _screen.value = Screen.Areas
        loadAreas()
    }

    /**
     * Record that a collector handed cash to the office (spec 009, phase B).
     *
     * The amount travels as the manager typed it and the office decides: above what the collector holds,
     * the engine refuses by name with the figure he does hold, and that sentence is what the sheet shows —
     * the phone never subtracts to check it.
     */
    fun recordHandover(collectorId: Int, amount: String, date: String, note: String?) {
        val token = _session.value?.token ?: return
        if (_handover.value is Load.Busy) return

        _handover.value = Load.Busy

        viewModelScope.launch {
            val call = MotoApi.handover(
                token = token,
                collectorId = collectorId,
                amount = amount,
                date = date,
                note = note,
                key = AttemptKey.next(),
            )

            when (call) {
                is ApiCall.Success -> {
                    _handover.value = Load.Ready(call.value)

                    // The board is a different board now: that collector is holding less. The refusal path
                    // says nothing to the board — the office refused, so nothing changed.
                    readManager()
                }

                else -> _handover.value = Load.Failed(problem(call).message)
            }
        }
    }

    fun clearHandover() {
        _handover.value = Load.Idle
    }

    /**
     * Take one receipt back (spec 009, phase C).
     *
     * The reason is sent exactly as typed — it is the sentence the office will read in the trail. A refusal
     * comes back in the office's own words (including «the money this receipt credited was already spent»),
     * and the board is only re-read when the office says the receipt is gone.
     */
    fun reverseReceipt(paymentId: Int, reason: String) {
        val token = _session.value?.token ?: return
        if (_reversal.value is Load.Busy) return

        _reversal.value = Load.Busy

        viewModelScope.launch {
            val call = MotoApi.reversal(
                token = token,
                paymentId = paymentId,
                reason = reason.trim(),
                key = AttemptKey.next(),
            )

            when (call) {
                is ApiCall.Success -> {
                    _reversal.value = Load.Ready(call.value)

                    // A reversal changes what the household owes, so wherever the manager is standing is
                    // re-read: the file he reversed from, and his board when that is what is behind him.
                    loadHouse()
                    if (_session.value?.user?.isAdmin == true) readManager()
                }

                else -> _reversal.value = Load.Failed(problem(call).message)
            }
        }
    }

    fun clearReversal() {
        _reversal.value = Load.Idle
    }

    /**
     * A receipt he wants to send again (spec 011 §8).
     *
     * The words are the office's, fetched fresh rather than kept on the phone: a receipt is a document, and
     * a copy that lived in the app for a week is a second version of it. On a failure the office's own
     * sentence is what the screen shows — the tap has to say something either way.
     */
    private val _receiptToShare = MutableStateFlow<ReceiptMessage?>(null)
    val receiptToShare: StateFlow<ReceiptMessage?> = _receiptToShare.asStateFlow()

    fun shareReceiptAgain(paymentId: Int) {
        val token = _session.value?.token ?: return

        viewModelScope.launch {
            when (val call = MotoApi.receiptText(token, paymentId)) {
                is ApiCall.Success -> {
                    _problem.value = null
                    _receiptToShare.value = call.value
                }

                else -> _problem.value = problem(call)
            }
        }
    }

    fun clearReceiptToShare() {
        _receiptToShare.value = null
    }

    /**
     * Back from the day's areas.
     *
     * A manager reached the network from his board, so the network is a step he took and can take back;
     * refreshing the board on the way in keeps the figures he returns to current with what he just saw.
     * A collector's areas ARE his home — there is nowhere above them to go.
     */
    fun backFromAreas() {
        if (_session.value?.user?.isAdmin != true) return

        _screen.value = Screen.Manager
        loadManager()
    }

    /**
     * What the phone's own back button does: it walks the same journey the arrows do, so a phone held
     * in one hand never closes mid-task. Returns false when this screen IS home, which lets the system
     * end the app the way the user expects.
     */
    fun back(): Boolean = when (_screen.value) {
        is Screen.House -> {
            backToSheet()
            true
        }

        is Screen.Houses -> {
            backToAreas()
            true
        }

        Screen.Queue -> {
            backFromQueue()
            true
        }

        // A tab, not a step: back from the purse goes to where a collector walks from.
        Screen.Wallet -> {
            openAreas()
            true
        }

        Screen.Areas -> {
            if (_session.value?.user?.isAdmin == true) {
                backFromAreas()
                true
            } else {
                false
            }
        }

        else -> false
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /**
     * Where a household opened from OUTSIDE the area sheets came from — the manager's board, or a
     * search. Those houses carry `areaId = 0` (there is no sheet behind them), so back has to be told
     * where «back» is; without this, the manager's board was a screen he could not return to.
     */
    private var houseOrigin: Screen? = null

    /** The phone's copy of the office's last answer, so the app OPENS with no network at all. */
    private val offline = OfflineCache(application)

    /**
     * When the figures on screen came from the phone's own copy instead of the office — null while they
     * are live. The screens say so: an old balance must never pass for a fresh one.
     */
    private val _staleAsOf = MutableStateFlow<String?>(null)
    val staleAsOf: StateFlow<String?> = _staleAsOf.asStateFlow()

    /** Open a household the manager picked from the board — there is no area sheet behind it. */
    fun openHouseById(id: Int) {
        houseOrigin = Screen.Manager
        _screen.value = Screen.House(areaId = 0, areaName = "", houseId = id)
        loadHouse(id)
    }

    /* ------------------------------------------------------------- the collector */

    /**
     * Pull the screen down to ask again — the gesture every phone user already knows.
     *
     * It reads whatever the CURRENT screen is about, and holds the gesture's own indicator up until that
     * answer is in: a refresh that shows nothing while it runs reads as a refresh that did nothing.
     */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true

        viewModelScope.launch {
            try {
                when (val current = _screen.value) {
                    Screen.Manager -> readManager()
                    Screen.Queue -> flushNow()
                    Screen.Wallet -> readWallet()
                    is Screen.Houses -> readSheet(current.areaId)
                    is Screen.House -> readHouse(current.houseId)
                    Screen.Login -> Unit
                    else -> readAreas()
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun loadAreas() {
        // Whatever the office answered last is on screen at once; the fresh answer replaces it when it
        // arrives. A spinner only ever appears over a screen this session has never read.
        boardCache?.let { _board.value = Load.Ready(it) } ?: run { _board.value = Load.Busy }
        _problem.value = null

        viewModelScope.launch { readAreas() }
    }

    /** The read itself, awaitable so a pull gesture can hold its indicator until the answer is in. */
    private suspend fun readAreas() {
        val token = _session.value?.token ?: return
        val me = _session.value?.user?.id ?: return

        when (val call = MotoApi.areas(token, sink = { offline.put(me, OfflineCache.BOARD, it) })) {
            is ApiCall.Success -> {
                boardCache = call.value
                _staleAsOf.value = null
                _board.value = Load.Ready(call.value)
                _lbpRate.value = call.value.lbpRate

                // The network just worked: anything the office still owes an answer for goes out now.
                flushNow()
            }

            else -> {
                _problem.value = problem(call)

                if (boardCache == null) {
                    // No answer now, and nothing read in this session: the phone's own copy is the only
                    // way in, and it says how old it is.
                    val stored = offline.body(me, OfflineCache.BOARD)?.let { MotoApi.parseBoard(it) }

                    if (stored != null) {
                        _staleAsOf.value = offline.storedAt(me, OfflineCache.BOARD)
                        _board.value = Load.Ready(stored)
                        _lbpRate.value = stored.lbpRate
                    } else {
                        _board.value = Load.Failed(_problem.value?.message)
                    }
                }
            }
        }
    }

    fun openArea(area: MotoArea) {
        val id = area.id ?: return

        _screen.value = Screen.Houses(id, area.name)
        loadSheet(id)
    }

    fun loadSheet(routeId: Int? = null) {
        val id = routeId ?: (screen.value as? Screen.Houses)?.areaId ?: return

        sheetCache[id]?.let { _sheet.value = Load.Ready(it) } ?: run { _sheet.value = Load.Busy }
        _problem.value = null

        viewModelScope.launch { readSheet(id) }
    }

    private suspend fun readSheet(id: Int) {
        val token = _session.value?.token ?: return
        val me = _session.value?.user?.id ?: return

        when (val call = MotoApi.houses(token, id, sink = { offline.put(me, OfflineCache.sheet(id), it) })) {
            is ApiCall.Success -> {
                sheetCache[id] = call.value
                _staleAsOf.value = null
                _sheet.value = Load.Ready(call.value)
                call.value.lbpRate?.let { _lbpRate.value = it }
            }

            else -> {
                _problem.value = problem(call)

                if (sheetCache[id] == null) {
                    val stored = offline.body(me, OfflineCache.sheet(id))?.let { MotoApi.parseSheet(it) }

                    if (stored != null) {
                        _staleAsOf.value = offline.storedAt(me, OfflineCache.sheet(id))
                        _sheet.value = Load.Ready(stored)
                    } else {
                        _sheet.value = Load.Failed(_problem.value?.message)
                    }
                }
            }
        }
    }

    fun openHouse(house: House) {
        val from = screen.value as? Screen.Houses

        _screen.value = Screen.House(
            areaId = from?.areaId ?: 0,
            areaName = from?.areaName ?: house.areaName,
            houseId = house.id,
        )

        loadHouse(house.id)
    }

    /**
     * Find a household by name, meter number or phone — the panel's own global search.
     *
     * Typing is debounced by 300ms because every keystroke would otherwise be a request from a street
     * with a bad signal; one character asks nothing at all (the server says so too, rather than the
     * app deciding what a real query is).
     */
    fun search(query: String) {
        searchJob?.cancel()

        val token = _session.value?.token ?: return
        val trimmed = query.trim()

        if (trimmed.length < 2) {
            _search.value = Load.Idle
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            _search.value = Load.Busy

            when (val call = MotoApi.search(token, trimmed)) {
                is ApiCall.Success -> _search.value = Load.Ready(call.value)
                is ApiCall.Unreachable -> _search.value = Load.Failed(null)
                else -> _search.value = Load.Ready(emptyList())
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _search.value = Load.Idle
    }

    /**
     * Open a household from the purse's review: there is no area sheet behind it, so back returns to the
     * purse he was reading — the same road the search hits take.
     */
    fun openHouseFromWallet(houseId: Int, name: String) {
        houseOrigin = Screen.Wallet
        _screen.value = Screen.House(areaId = 0, areaName = name, houseId = houseId)
        loadHouse(houseId)
    }

    /** Open a household found by search: there is no area sheet behind it, so going back goes home. */
    fun openSearchHit(hit: SearchHit) {
        clearSearch()
        houseOrigin = Screen.Areas
        _screen.value = Screen.House(areaId = 0, areaName = hit.route.orEmpty(), houseId = hit.id)
        loadHouse(hit.id)
    }

    fun loadHouse(houseId: Int? = null) {
        val id = houseId ?: (screen.value as? Screen.House)?.houseId ?: return

        houseCache[id]?.let { _house.value = Load.Ready(it) } ?: run { _house.value = Load.Busy }
        _problem.value = null

        viewModelScope.launch { readHouse(id) }
    }

    private suspend fun readHouse(id: Int) {
        val token = _session.value?.token ?: return
        val me = _session.value?.user?.id ?: return

        when (val call = MotoApi.house(token, id, sink = { offline.put(me, OfflineCache.house(id), it) })) {
            is ApiCall.Success -> {
                houseCache[id] = call.value
                _staleAsOf.value = null
                _house.value = Load.Ready(call.value)
            }

            else -> {
                _problem.value = problem(call)

                if (houseCache[id] == null) {
                    val stored = offline.body(me, OfflineCache.house(id))?.let { MotoApi.parseHouseBody(it) }

                    if (stored != null) {
                        _staleAsOf.value = offline.storedAt(me, OfflineCache.house(id))
                        _house.value = Load.Ready(stored)
                    } else {
                        _house.value = Load.Failed(_problem.value?.message)
                    }
                }
            }
        }
    }

    fun backToAreas() {
        _screen.value = Screen.Areas
        _sheet.value = Load.Idle
        _house.value = Load.Idle
        _problem.value = null
    }

    fun backToSheet() {
        val from = screen.value as? Screen.House
        val origin = houseOrigin
        houseOrigin = null

        // A household opened from a search or from the manager's board has no sheet behind it: going
        // back goes where it was opened from, rather than asking the server for the houses of area «0».
        _screen.value = from?.takeIf { it.areaId != 0 }
            ?.let { Screen.Houses(it.areaId, it.areaName) }
            ?: origin
            ?: Screen.Areas

        _house.value = Load.Idle
        _problem.value = null

        if (_screen.value is Screen.Houses) {
            loadSheet((_screen.value as Screen.Houses).areaId)
        }
    }

    fun clearProblem() {
        _problem.value = null
    }

    fun clearOutcome() {
        _outcome.value = null
    }

    /* ------------------------------------------------------------ the two writes */

    /**
     * A meter reading, taken at the door.
     *
     * Recorded on disk first (spec 010), then sent. Pressing the same reading twice while the first is
     * still unanswered is the same attempt — it keeps the key it already has, because a fresh key is a
     * second bill for one month's meter.
     */
    fun submitReading(currentReading: String, note: String?, estimated: Boolean, estimatedKwh: String, reason: String?) {
        val house = (_house.value as? Load.Ready)?.value ?: return
        val me = _session.value?.user?.id ?: return

        val candidate = QueuedWrite(
            kind = QueuedWrite.Kind.Reading,
            key = AttemptKey.next(),
            userId = me,
            houseId = house.id,
            houseName = house.name,
            createdAt = LocalDateTime.now().toString(),
            period = period(),
            currentReading = currentReading,
            estimatedKwh = if (estimated) estimatedKwh else null,
            reason = reason,
            note = note,
        )

        walk(listOf(owedDuplicate(me, candidate) ?: candidate), report = true)
    }

    /** Money taken at the door, in one currency. */
    fun submitCollection(amount: String, currency: String, rate: String?, note: String?) {
        val house = (_house.value as? Load.Ready)?.value ?: return
        val me = _session.value?.user?.id ?: return

        val candidate = QueuedWrite(
            kind = QueuedWrite.Kind.Collection,
            key = AttemptKey.next(),
            userId = me,
            houseId = house.id,
            houseName = house.name,
            createdAt = LocalDateTime.now().toString(),
            // The day the money was TAKEN, written down now: a payment collected at eleven at night and
            // sent in the morning still belongs to the night it was collected.
            collectedOn = LocalDate.now().toString(),
            amount = amount,
            currency = currency,
            rate = rate,
            note = note,
        )

        walk(listOf(owedDuplicate(me, candidate) ?: candidate), report = true)
    }

    /**
     * «أعد المحاولة» on a write whose answer never arrived.
     *
     * Sends this house's owed rows again with the keys they already have — never a fresh attempt — and
     * reports what the office says this time.
     */
    fun retryPending() {
        val me = _session.value?.user?.id ?: return
        val houseId = (_screen.value as? Screen.House)?.houseId
        val owed = QueueDecision.openEntries(QueueDecision.forUser(_queue.value, me))
            .filter { houseId == null || it.houseId == houseId }

        if (owed.isEmpty()) flush() else walk(owed, report = true)
    }

    /* --------------------------------------------------------------- the queue */

    /**
     * Send these rows, oldest first, and write down what the office said about each.
     *
     * `report` is for a write the collector is watching: he gets the office's own answer on screen. The
     * queue's own flush reports nothing — a queued row going out is not news.
     *
     * The walk stops at the first row the network did not answer: ten failures in a row are one fact.
     */
    private fun walk(entries: List<QueuedWrite>, report: Boolean) {
        val token = _session.value?.token ?: return
        if (entries.isEmpty()) return

        viewModelScope.launch {
            if (report) {
                _busy.value = true
                _outcome.value = null
            }

            var last: QueuedWrite? = null
            var lastCall: ApiCall<*>? = null

            for (entry in entries) {
                // On disk FIRST, before the request leaves the phone (spec 010's first invariant): a
                // phone killed mid-request must still know the collector took this reading or this cash.
                update(entry)

                val call = QueueRunner.carry(entry, token)
                val settled = QueueRunner.settleTo(entry, call)

                update(settled)
                last = settled
                lastCall = call

                if (!QueueDecision.continueAfter(settled)) break
            }

            tellThePhone()

            if (report) {
                _busy.value = false
                _outcome.value = outcome(last, lastCall)
            }

            // A write the office applied makes the house a different house: re-read it so the door shows
            // the office's own figures rather than what the collector saw before he paid.
            last?.takeIf { it.state == QueueState.Done }?.let { refreshAfterWrite(it.houseId) }
        }
    }

    /**
     * Send everything the office still owes an answer for — the queue draining itself.
     *
     * Runs when the app opens, when the phone finds a network, by hand from the queue screen, and after a
     * write the collector watched. It only ever sends the CURRENT collector's rows: the office attributes
     * a receipt to the token that sent it, so another collector's rows wait for their own session.
     */
    fun flush() {
        viewModelScope.launch { flushNow() }
    }

    /** The flush itself, awaitable — so a pull gesture can hold its indicator until the queue drains. */
    private suspend fun flushNow() {
        val me = _session.value?.user?.id ?: return
        val token = _session.value?.token ?: return

        // One implementation, shared with the job Android runs when the phone is in a pocket.
        val report = QueueRunner.drain(queueStore, me, token)

        refreshQueue()
        tellThePhone()

        if (report.sentAnything) refreshAfterWrite(null)
    }

    /**
     * Let the phone speak without being opened (spec 010, phase B).
     *
     * Two things at once: Android is asked to drain the queue the moment a network appears (app open or
     * not), and the collector is told, in a notification, that something of his has not reached the office.
     * When nothing is owed the reminder goes — a notification that outlives its fact is noise.
     */
    private fun tellThePhone() {
        val context = getApplication<Application>()
        val owed = QueueDecision.openEntries(_queue.value).size

        if (owed > 0) {
            QueueSync.schedule(context)
            QueueNotifier.waiting(context, owed)
        } else {
            QueueNotifier.clear(context)
        }
    }

    /** The office's own words for a write the collector just watched. */
    private fun outcome(entry: QueuedWrite?, call: ApiCall<*>?): Outcome? {
        val app = getApplication<Application>()
        if (entry == null || call == null) return null

        return when {
            entry.state == QueueState.Done && call is ApiCall.Success<*> -> when (val value = call.value) {
                is ReadingAnswer -> readingOutcome(value)
                is CollectionAnswer -> collectionOutcome(value)
                else -> null
            }

            entry.state == QueueState.NeedsReconciliation -> Outcome(
                kind = OutcomeKind.Refused,
                title = app.getString(R.string.outcome_refused_title),
                facts = listOf(app.getString(R.string.outcome_refused_hint) to ""),
                problem = problem(call),
            )

            entry.state == QueueState.Done -> null

            else -> Outcome(
                kind = OutcomeKind.Unknown,
                // The collector is holding cash: «recorded, waiting» is the sentence he needs, and it is
                // not the same sentence for money and for a reading.
                title = app.getString(
                    if (entry.isMoney) R.string.outcome_queued_collection_title else R.string.outcome_queued_reading_title,
                ),
                facts = listOf(
                    entry.houseName to if (entry.isMoney) Money.usd(entry.amount.orEmpty()) else entry.currentReading.orEmpty(),
                    app.getString(R.string.outcome_queued_hint) to "",
                ),
                problem = problem(call),
                retryable = true,
                key = entry.key,
            )
        }
    }

    /**
     * Back to the manager's board from wherever he is (the bottom bar's first door).
     *
     * It refreshes on the way in for the same reason the areas' back does: the figures he returns to
     * should be the figures as they are, not as they were when he left.
     */
    fun openBoard() {
        if (_session.value?.user?.isAdmin != true) return

        queueOrigin = null
        _screen.value = Screen.Manager
        loadManager()
    }

    /**
     * The collector came back to the app: re-read what the phone holds, and let the queue go.
     *
     * The job Android runs while the phone is in a pocket has its own view of the world. Without this, a
     * screen could keep saying «بانتظار الشبكة» over a payment that went out while the app was in the
     * background — the same lie, one resume later.
     */
    fun onResumed() {
        if (_session.value == null) return

        refreshQueue()
        flush()
    }

    /** Where the queue is opened from, so its back returns there. */
    fun openQueue() {
        queueOrigin = _screen.value
        _screen.value = Screen.Queue
    }

    fun backFromQueue() {
        val origin = queueOrigin
        queueOrigin = null

        _screen.value = origin ?: Screen.Areas
    }

    /**
     * Throw a row away on purpose.
     *
     * It does not disappear: the row stays visible as `Discarded` with the date, because money that a
     * tap can silently remove is money nobody can audit. The confirmation on screen says so in words.
     */
    fun discard(entry: QueuedWrite) {
        mutate { rows ->
            val index = rows.indexOfFirst { it.key == entry.key }
            if (index >= 0) rows[index] = rows[index].copy(state = QueueState.Discarded)
        }

        tellThePhone()
    }

    /* ------------------------------------------------------------------ internals */

    /** The row this exact write already has while it is still owed — a second press is not a new attempt. */
    private fun owedDuplicate(me: Int, candidate: QueuedWrite): QueuedWrite? = _queue.value
        .firstOrNull { it.isOpen && it.userId == me && it.sameWriteAs(candidate) }

    /**
     * Every change to the queue is a read-modify-write of the ONE file.
     *
     * The file is the record; a second copy in memory is a second thing that can disagree with it. The
     * flow the screens read is only the current collector's view of what came back.
     */
    private fun mutate(block: (MutableList<QueuedWrite>) -> Unit) {
        val rows = queueStore.load().toMutableList()
        block(rows)
        queueStore.save(rows)
        _queue.value = QueueDecision.forUser(rows, _session.value?.user?.id ?: -1)
    }

    private fun update(entry: QueuedWrite) = mutate { rows ->
        val index = rows.indexOfFirst { it.key == entry.key }
        if (index >= 0) rows[index] = entry else rows.add(entry)
    }

    /** Re-read the queue for whoever is signed in now. The file itself is never cleared by signing out. */
    private fun refreshQueue() {
        _queue.value = QueueDecision.forUser(queueStore.load(), _session.value?.user?.id ?: -1)
        reconcileOutcome()
    }

    /**
     * The card follows the row it is about.
     *
     * A write recorded in a dead spot can be applied by the queue while its card is still on screen — the
     * app's own flush, or the job Android runs with the phone in a pocket. Leaving «بانتظار الشبكة» over a
     * payment the office has already taken is worse than showing nothing, so the outcome is rebuilt from
     * what the row became: applied → «وصل المكتب» with the office's own receipt, refused → its reason.
     */
    private fun reconcileOutcome() {
        val shown = _outcome.value ?: return
        val key = shown.key ?: return
        val row = _queue.value.firstOrNull { it.key == key } ?: return

        val app = getApplication<Application>()
        val amount = if (row.isMoney) Money.usd(row.amount.orEmpty()) else row.currentReading.orEmpty()

        val settled = when (row.state) {
            QueueState.Done -> Outcome(
                kind = if (row.isMoney) OutcomeKind.Collection else OutcomeKind.Reading,
                title = app.getString(R.string.outcome_delivered_title),
                facts = listOf(row.houseName to amount),
                receipt = row.receiptText,
                key = row.key,
            )

            QueueState.NeedsReconciliation -> Outcome(
                kind = OutcomeKind.Refused,
                title = app.getString(R.string.outcome_refused_title),
                facts = listOf(row.houseName to amount),
                problem = Problem(row.lastError.orEmpty(), null),
                key = row.key,
            )

            else -> return
        }

        if (settled != shown) {
            _outcome.value = settled
        }
    }

    /* ------------------------------------------------------------------ internals */

    private fun period(): String {
        val sheetPeriod = (_sheet.value as? Load.Ready)?.value?.period

        return sheetPeriod ?: LocalDate.now().toString().substring(0, 7)
    }

    private fun refreshAfterWrite(houseId: Int?) {
        // A flush has no single house: the day's board is what a collector is looking at when rows go
        // out in the background, so that is what gets re-read.
        if (houseId == null) {
            if (screen.value is Screen.Areas) loadAreas()
            return
        }

        loadHouse(houseId)

        val areaId = (screen.value as? Screen.House)?.areaId
        if (areaId != null && areaId != 0) {
            loadSheet(areaId)
        }
    }

    private fun readingOutcome(answer: ReadingAnswer): Outcome = Outcome(
        kind = OutcomeKind.Reading,
        title = getApplication<Application>().getString(R.string.outcome_reading_title),
        facts = listOf(
            getApplication<Application>().getString(R.string.outcome_invoice) to (answer.bill.number ?: answer.bill.period),
            getApplication<Application>().getString(R.string.outcome_consumption) to (answer.reading.consumption + " kWh"),
            getApplication<Application>().getString(R.string.outcome_total) to Money.usd(answer.bill.total),
            getApplication<Application>().getString(R.string.outcome_remaining) to Money.usd(answer.house.balance),
        ),
    )

    private fun collectionOutcome(answer: CollectionAnswer): Outcome {
        val rows = mutableListOf(
            getApplication<Application>().getString(R.string.outcome_receipt) to answer.payment.number,
            getApplication<Application>().getString(R.string.outcome_collected) to Money.usd(answer.payment.amount),
            getApplication<Application>().getString(R.string.outcome_applied) to Money.usd(answer.payment.applied),
        )

        answer.settled.forEach { month ->
            if (month.label != null) {
                rows += month.label to Money.usd(month.amount)
            }
        }

        if (answer.payment.credited != "0.00") {
            rows += getApplication<Application>().getString(R.string.outcome_credited) to Money.usd(answer.payment.credited)
        }

        rows += getApplication<Application>().getString(R.string.outcome_remaining) to Money.usd(answer.house.balance)

        return Outcome(
            kind = OutcomeKind.Collection,
            title = getApplication<Application>().getString(R.string.outcome_collection_title),
            facts = rows,
            receipt = answer.receiptText,
        )
    }

    private fun deviceName(): String = ("MotoApp · " + (Build.MODEL ?: "phone")).take(60)

    /**
     * Turns a failed call into something to show.
     *
     * An expired session and a retired account both mean the same thing to the phone — sign in again —
     * so both clear the stored token and put the login screen back, while the server's own sentence
     * explains why.
     */
    private fun problem(call: ApiCall<*>): Problem {
        val app = getApplication<Application>()

        return when (call) {
            is ApiCall.Refused -> {
                if (call.answer.code == "unauthenticated" || call.answer.code == "account_disabled") {
                    vault.clear()
                    _session.value = null
                    _screen.value = Screen.Login
                }

                Problem(
                    message = call.answer.message ?: app.getString(R.string.problem_server_refused),
                    code = call.answer.code,
                )
            }

            is ApiCall.Unreadable -> Problem(app.getString(R.string.problem_unreadable), call.answer.code)

            is ApiCall.Unreachable -> Problem(app.getString(R.string.problem_unreachable), null)

            is ApiCall.Success<*> -> Problem(app.getString(R.string.problem_unexpected), null)
        }
    }

}
