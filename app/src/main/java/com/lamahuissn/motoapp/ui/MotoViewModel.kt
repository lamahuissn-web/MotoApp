package com.lamahuissn.motoapp.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lamahuissn.motoapp.R
import com.lamahuissn.motoapp.data.ApiCall
import com.lamahuissn.motoapp.data.AreaSheet
import com.lamahuissn.motoapp.data.AreasBoard
import com.lamahuissn.motoapp.data.MotoApi
import com.lamahuissn.motoapp.data.MotoArea
import com.lamahuissn.motoapp.data.Session
import com.lamahuissn.motoapp.data.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the collector is. */
sealed interface Screen {
    data object Login : Screen
    data object Areas : Screen
    data class Houses(val areaId: Int, val areaName: String) : Screen
}

/** Something to tell the collector, in the office's words, with the code for whoever repairs it. */
data class Problem(val message: String, val code: String?)

/**
 * The app's one state holder.
 *
 * One holder rather than a view-model per screen, because these screens are one journey through one
 * session: the token, the area, the sheet and the busy flag all change together, and a phone that is
 * carried between a door and an office keeps one signed-in session.
 *
 * Every failure becomes a sentence from the server when the server gave one — «هذا الشهر بلا فاتورة»
 * is a sentence a collector can act on, and «حدث خطأ» is not.
 */
class MotoViewModel(application: Application) : AndroidViewModel(application) {

    private val vault = TokenVault(application)

    private val _session = MutableStateFlow(vault.load())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(if (_session.value == null) Screen.Login else Screen.Areas)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _board = MutableStateFlow<Load<AreasBoard>>(Load.Idle)
    val board: StateFlow<Load<AreasBoard>> = _board.asStateFlow()

    private val _sheet = MutableStateFlow<Load<AreaSheet>>(Load.Idle)
    val sheet: StateFlow<Load<AreaSheet>> = _sheet.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()

    init {
        // A collector who signed in yesterday opens the sheet he is about to walk, not a login box.
        if (_session.value != null) {
            loadAreas()
        }
    }

    fun signIn(identity: String, password: String) {
        if (_busy.value) return

        _busy.value = true
        _problem.value = null

        viewModelScope.launch {
            when (val call = MotoApi.login(identity.trim(), password, deviceName())) {
                is ApiCall.Success -> {
                    vault.save(call.value)
                    _session.value = call.value
                    _screen.value = Screen.Areas
                    _busy.value = false
                    loadAreas()
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
        _board.value = Load.Idle
        _sheet.value = Load.Idle
        _problem.value = null
        _screen.value = Screen.Login

        if (token != null) {
            viewModelScope.launch { MotoApi.logout(token) }
        }
    }

    fun loadAreas() {
        val token = _session.value?.token ?: return

        _board.value = Load.Busy
        _problem.value = null

        viewModelScope.launch {
            when (val call = MotoApi.areas(token)) {
                is ApiCall.Success -> _board.value = Load.Ready(call.value)
                else -> {
                    _problem.value = problem(call)
                    _board.value = Load.Failed(_problem.value?.message)
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

        _sheet.value = Load.Busy
        _problem.value = null

        viewModelScope.launch {
            when (val call = MotoApi.houses(token, id)) {
                is ApiCall.Success -> _sheet.value = Load.Ready(call.value)
                else -> {
                    _problem.value = problem(call)
                    _sheet.value = Load.Failed(_problem.value?.message)
                }
            }
        }
    }

    fun backToAreas() {
        _screen.value = Screen.Areas
        _sheet.value = Load.Idle
        _problem.value = null
    }

    fun clearProblem() {
        _problem.value = null
    }

    private fun deviceName(): String = ("MotoApp · " + (Build.MODEL ?: "phone")).take(60)

    /**
     * Turns a failed call into something to show.
     *
     * An expired session and a retired account both mean the same thing to the phone — sign in again
     * — so both clear the stored token and put the login screen back, while the server's own sentence
     * explains why. Everything else is shown beside the screen it belongs to, with the code kept for
     * whoever repairs it.
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
                    message = call.answer.message
                        ?: app.getString(R.string.problem_server_refused),
                    code = call.answer.code,
                )
            }

            is ApiCall.Unreadable -> Problem(app.getString(R.string.problem_unreadable), call.answer.code)

            is ApiCall.Unreachable -> Problem(app.getString(R.string.problem_unreachable), null)

            is ApiCall.Success<*> -> Problem(app.getString(R.string.problem_unexpected), null)
        }
    }
}
