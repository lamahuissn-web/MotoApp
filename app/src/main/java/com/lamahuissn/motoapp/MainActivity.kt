package com.lamahuissn.motoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamahuissn.motoapp.ui.AreasScreen
import com.lamahuissn.motoapp.ui.HouseScreen
import com.lamahuissn.motoapp.ui.HousesScreen
import com.lamahuissn.motoapp.ui.LoginScreen
import com.lamahuissn.motoapp.ui.ManagerScreen
import com.lamahuissn.motoapp.ui.MotoViewModel
import com.lamahuissn.motoapp.ui.QueueScreen
import com.lamahuissn.motoapp.ui.Screen
import com.lamahuissn.motoapp.ui.theme.MotoAppTheme

/**
 * The app's one activity.
 *
 * The screens are one journey through one session — sign in, the day's areas, one area's houses, and
 * a reading and a collection from a house — so they are Composed here from one state holder rather
 * than being separate activities. A phone carried from a door to an office keeps one signed-in
 * session, one HTTP client and one cache.
 *
 * Everything sits on a `Surface` painted with the theme's background: the window behind Compose is
 * the platform's, and a screen that relies on it comes out the colour of the window (the first build
 * showed exactly that, as a sheet of brand blue behind the login fields).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MotoAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val model: MotoViewModel = viewModel()
                    MotoApp(model)
                }
            }
        }
    }
}

@Composable
private fun MotoApp(model: MotoViewModel) {
    val screen by model.screen.collectAsState()
    val session by model.session.collectAsState()
    val busy by model.busy.collectAsState()
    val problem by model.problem.collectAsState()
    val staleAsOf by model.staleAsOf.collectAsState()
    val refreshing by model.refreshing.collectAsState()

    // The phone's own back button walks the same journey the arrows do. On a home screen it stays
    // disabled, so the system ends the app the way the user expects instead of us swallowing the press.
    val canGoBack = when (screen) {
        is Screen.House, is Screen.Houses, Screen.Queue -> true
        Screen.Areas -> session?.user?.isAdmin == true
        else -> false
    }

    BackHandler(enabled = canGoBack) { model.back() }

    when (val current = screen) {
        Screen.Login -> LoginScreen(
            busy = busy,
            problem = problem,
            onSignIn = model::signIn,
        )

        Screen.Areas -> {
            val board by model.board.collectAsState()
            val search by model.search.collectAsState()
            val queue by model.queue.collectAsState()

            AreasScreen(
                userName = session?.user?.name.orEmpty(),
                load = board,
                problem = problem,
                search = search,
                onSearch = model::search,
                onOpenHit = model::openSearchHit,
                onRefresh = model::loadAreas,
                onSignOut = model::signOut,
                onOpen = model::openArea,
                queue = queue,
                onOpenQueue = model::openQueue,
                staleAsOf = staleAsOf,
                refreshing = refreshing,
                onPullRefresh = model::refresh,
                onBack = if (session?.user?.isAdmin == true) model::backFromAreas else null,
            )
        }

        Screen.Queue -> {
            val queue by model.queue.collectAsState()

            QueueScreen(
                records = queue,
                busy = busy,
                onSend = model::flush,
                onDiscard = model::discard,
                onBack = model::backFromQueue,
                onSignOut = model::signOut,
                refreshing = refreshing,
                onPullRefresh = model::refresh,
            )
        }

        is Screen.Houses -> {
            val sheet by model.sheet.collectAsState()

            HousesScreen(
                areaName = current.areaName,
                load = sheet,
                problem = problem,
                onBack = model::backToAreas,
                onRetry = { model.loadSheet(current.areaId) },
                onOpenHouse = model::openHouse,
                staleAsOf = staleAsOf,
                refreshing = refreshing,
                onPullRefresh = model::refresh,
            )
        }

        is Screen.House -> {
            val house by model.house.collectAsState()
            val outcome by model.outcome.collectAsState()
            val rate by model.lbpRate.collectAsState()
            val queue by model.queue.collectAsState()

            HouseScreen(
                load = house,
                problem = problem,
                busy = busy,
                outcome = outcome,
                sheetRate = rate,
                onBack = model::backToSheet,
                onRetryLoad = { model.loadHouse(current.houseId) },
                onRead = model::submitReading,
                onCollect = model::submitCollection,
                onRetryWrite = model::retryPending,
                onDismissOutcome = model::clearOutcome,
                staleAsOf = staleAsOf,
                queueOwed = queue.count { it.isOpen },
                onOpenQueue = model::openQueue,
                refreshing = refreshing,
                onPullRefresh = model::refresh,
            )
        }

        Screen.Manager -> {
            val manager by model.manager.collectAsState()

            ManagerScreen(
                userName = session?.user?.name.orEmpty(),
                load = manager,
                problem = problem,
                onRefresh = model::loadManager,
                onOpenAreas = model::openAreas,
                onSignOut = model::signOut,
                onOpenHouse = model::openHouseById,
                refreshing = refreshing,
                onPullRefresh = model::refresh,
            )
        }
    }
}
