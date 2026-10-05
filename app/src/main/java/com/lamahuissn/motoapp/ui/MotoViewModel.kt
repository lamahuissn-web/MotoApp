package com.lamahuissn.motoapp.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.ApiCall
import com.lamahuissn.motoapp.data.AreaSheet
import com.lamahuissn.motoapp.data.AreasBoard
import com.lamahuissn.motoapp.data.AttemptKey
import com.lamahuissn.motoapp.data.CollectionAnswer
import com.lamahuissn.motoapp.data.House
import com.lamahuissn.motoapp.data.ManagerBoard
import com.lamahuissn.motoapp.data.MotoApi
import com.lamahuissn.motoapp.data.MotoArea
import com.lamahuissn.motoapp.data.ReadingAnswer
import com.lamahuissn.motoapp.data.SearchHit
import com.lamahuissn.motoapp.data.Session
import com.lamahuissn.motoapp.data.TokenVault
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Where the collector is. */
sealed interface Screen {
    data object Login : Screen
    data object Areas : Screen
    /** The manager's board — the home of a manager's session (spec 009). */
    data object Manager : Screen
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
)

/**
 * The app's one state holder.
 *
 * One holder rather than a view-model per screen, because these screens are one journey through one
 * session: the token, the area, the sheet, the house and the busy flag all change together.
 *
 * The two rules of the money are enforced here rather than in a dialog:
 *
 *  - **One attempt, one key.** A key is made when the collector confirms, kept while the answer is
 *    unknown, and reused for every retry of that attempt — never regenerated, because a regenerated key
 *    is a second receipt for money handed over once.
 *  - **A refusal is an answer.** When the server refuses (`refuse.period_not_invoiced`, `refuse.
 *    reading_lower`), the attempt is over: the key is dropped and the office's own sentence is shown.
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

    /** The attempt whose answer is not known yet, kept across retries (see this class's docblock). */
    private var pending: Attempt? = null

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

                    when (_screen.value) {
                        Screen.Manager -> loadManager()
                        else -> loadAreas()
                    }
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

        vault.clear()
        _session.value = null
        boardCache = null
        sheetCache.clear()
        houseCache.clear()
        _board.value = Load.Idle
        _sheet.value = Load.Idle
        _house.value = Load.Idle
        _manager.value = Load.Idle
        _search.value = Load.Idle
        _problem.value = null
        _outcome.value = null
        pending = null
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
        val token = _session.value?.token ?: return

        if (_manager.value !is Load.Ready) {
            _manager.value = Load.Busy
        }

        _problem.value = null

        viewModelScope.launch {
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
    }

    fun openAreas() {
        _screen.value = Screen.Areas
        loadAreas()
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

    /**
     * Where a household opened from OUTSIDE the area sheets came from — the manager's board, or a
     * search. Those houses carry `areaId = 0` (there is no sheet behind them), so back has to be told
     * where «back» is; without this, the manager's board was a screen he could not return to.
     */
    private var houseOrigin: Screen? = null

    /** Open a household the manager picked from the board — there is no area sheet behind it. */
    fun openHouseById(id: Int) {
        houseOrigin = Screen.Manager
        _screen.value = Screen.House(areaId = 0, areaName = "", houseId = id)
        loadHouse(id)
    }

    /* ------------------------------------------------------------- the collector */

    fun loadAreas() {
        val token = _session.value?.token ?: return

        // Whatever the office answered last is on screen at once; the fresh answer replaces it when it
        // arrives. A spinner only ever appears over a screen this session has never read.
        boardCache?.let { _board.value = Load.Ready(it) } ?: run { _board.value = Load.Busy }
        _problem.value = null

        viewModelScope.launch {
            when (val call = MotoApi.areas(token)) {
                is ApiCall.Success -> {
                    boardCache = call.value
                    _board.value = Load.Ready(call.value)
                    _lbpRate.value = call.value.lbpRate
                }

                else -> {
                    _problem.value = problem(call)
                    if (boardCache == null) {
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
        val token = _session.value?.token ?: return

        sheetCache[id]?.let { _sheet.value = Load.Ready(it) } ?: run { _sheet.value = Load.Busy }
        _problem.value = null

        viewModelScope.launch {
            when (val call = MotoApi.houses(token, id)) {
                is ApiCall.Success -> {
                    sheetCache[id] = call.value
                    _sheet.value = Load.Ready(call.value)
                    call.value.lbpRate?.let { _lbpRate.value = it }
                }

                else -> {
                    _problem.value = problem(call)
                    if (sheetCache[id] == null) {
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

    /** Open a household found by search: there is no area sheet behind it, so going back goes home. */
    fun openSearchHit(hit: SearchHit) {
        clearSearch()
        houseOrigin = Screen.Areas
        _screen.value = Screen.House(areaId = 0, areaName = hit.route.orEmpty(), houseId = hit.id)
        loadHouse(hit.id)
    }

    fun loadHouse(houseId: Int? = null) {
        val id = houseId ?: (screen.value as? Screen.House)?.houseId ?: return
        val token = _session.value?.token ?: return

        houseCache[id]?.let { _house.value = Load.Ready(it) } ?: run { _house.value = Load.Busy }
        _problem.value = null

        viewModelScope.launch {
            when (val call = MotoApi.house(token, id)) {
                is ApiCall.Success -> {
                    houseCache[id] = call.value
                    _house.value = Load.Ready(call.value)
                }

                else -> {
                    _problem.value = problem(call)
                    if (houseCache[id] == null) {
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

    /** A meter reading, taken at the door. */
    fun submitReading(currentReading: String, note: String?, estimated: Boolean, estimatedKwh: String, reason: String?) {
        val house = (_house.value as? Load.Ready)?.value ?: return
        val token = _session.value?.token ?: return
        val app = getApplication<Application>()

        // The same payload keeps the key it already had: a retry is the SAME attempt, and a fresh key
        // would be a second invoice for one month's meter.
        val signature = "reading|${house.id}|$currentReading|${note.orEmpty()}|${if (estimated) estimatedKwh else ""}|${reason.orEmpty()}"
        val key = pending?.takeIf { it.houseId == house.id && it.payload == signature }?.key ?: AttemptKey.next()

        val attempt = Attempt.Reading(
            key = key,
            houseId = house.id,
            period = period(),
            currentReading = currentReading,
            note = note,
            estimatedKwh = if (estimated) estimatedKwh else null,
            reason = reason,
            payload = signature,
        )

        pending = attempt
        _busy.value = true
        _outcome.value = null

        viewModelScope.launch {
            val call = if (estimated) {
                MotoApi.estimate(token, house.id, attempt.period, estimatedKwh, reason.orEmpty(), attempt.key)
            } else {
                MotoApi.read(token, house.id, attempt.period, currentReading, note, attempt.key)
            }

            _busy.value = false

            when (call) {
                is ApiCall.Success -> {
                    pending = null
                    _outcome.value = readingOutcome(call.value)
                    refreshAfterWrite(house.id)
                }

                is ApiCall.Refused -> {
                    // The server answered: this attempt is over, key and all.
                    pending = null
                    _outcome.value = Outcome(
                        kind = OutcomeKind.Refused,
                        title = app.getString(R.string.outcome_refused_title),
                        facts = listOf(app.getString(R.string.outcome_refused_hint) to ""),
                        problem = problem(call),
                    )
                }

                else -> {
                    // Unknown: the key stays, and the collector is offered the SAME attempt again.
                    _outcome.value = Outcome(
                        kind = OutcomeKind.Unknown,
                        title = app.getString(R.string.outcome_unknown_title),
                        facts = listOf(app.getString(R.string.outcome_unknown_hint) to ""),
                        problem = problem(call),
                        retryable = true,
                    )
                }
            }
        }
    }

    /** Money taken at the door, in one currency. */
    fun submitCollection(amount: String, currency: String, rate: String?, note: String?) {
        val house = (_house.value as? Load.Ready)?.value ?: return
        val token = _session.value?.token ?: return
        val app = getApplication<Application>()

        val attempt = Attempt.Collection(
            key = AttemptKey.next(),
            houseId = house.id,
            amount = amount,
            currency = currency,
            rate = rate,
            note = note,
            payload = "$amount|$currency|${rate.orEmpty()}",
        )

        pending = attempt
        _busy.value = true
        _outcome.value = null

        viewModelScope.launch {
            val call = MotoApi.collect(
                token = token,
                subscriberId = house.id,
                amount = amount,
                currency = currency,
                rate = rate,
                method = if (currency == "lbp") "cash_lbp" else "cash_usd",
                note = note,
                collectedOn = LocalDate.now().toString(),
                key = attempt.key,
            )

            _busy.value = false

            when (call) {
                is ApiCall.Success -> {
                    pending = null
                    _outcome.value = collectionOutcome(call.value)
                    refreshAfterWrite(house.id)
                }

                is ApiCall.Refused -> {
                    pending = null
                    _outcome.value = Outcome(
                        kind = OutcomeKind.Refused,
                        title = app.getString(R.string.outcome_refused_title),
                        facts = listOf(app.getString(R.string.outcome_refused_hint) to ""),
                        problem = problem(call),
                    )
                }

                else -> _outcome.value = Outcome(
                    kind = OutcomeKind.Unknown,
                    title = app.getString(R.string.outcome_unknown_title),
                    facts = listOf(app.getString(R.string.outcome_unknown_hint) to ""),
                    problem = problem(call),
                    retryable = true,
                )
            }
        }
    }

    /** Send the attempt whose answer is still unknown — with its own key, never a new one. */
    fun retryPending() {
        when (val attempt = pending) {
            is Attempt.Reading -> submitReading(
                currentReading = attempt.currentReading,
                note = attempt.note,
                estimated = ! attempt.estimatedKwh.isNullOrBlank(),
                estimatedKwh = attempt.estimatedKwh.orEmpty(),
                reason = attempt.reason,
            )

            is Attempt.Collection -> submitCollection(
                amount = attempt.amount,
                currency = attempt.currency,
                rate = attempt.rate,
                note = attempt.note,
            )

            null -> Unit
        }
    }

    /* ------------------------------------------------------------------ internals */

    private fun period(): String {
        val sheetPeriod = (_sheet.value as? Load.Ready)?.value?.period

        return sheetPeriod ?: LocalDate.now().toString().substring(0, 7)
    }

    private fun refreshAfterWrite(houseId: Int) {
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

    /** The writes this app makes, as values it can keep and resend. */
    private sealed interface Attempt {
        val key: String
        val houseId: Int
        val payload: String

        data class Reading(
            override val key: String,
            override val houseId: Int,
            val period: String,
            val currentReading: String,
            val note: String?,
            val estimatedKwh: String?,
            val reason: String?,
            override val payload: String,
        ) : Attempt

        data class Collection(
            override val key: String,
            override val houseId: Int,
            val amount: String,
            val currency: String,
            val rate: String?,
            val note: String?,
            override val payload: String,
        ) : Attempt
    }
}
