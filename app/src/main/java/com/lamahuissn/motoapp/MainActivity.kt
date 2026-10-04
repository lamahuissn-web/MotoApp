package com.lamahuissn.motoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamahuissn.motoapp.ui.AreasScreen
import com.lamahuissn.motoapp.ui.HousesScreen
import com.lamahuissn.motoapp.ui.LoginScreen
import com.lamahuissn.motoapp.ui.MotoViewModel
import com.lamahuissn.motoapp.ui.Screen
import com.lamahuissn.motoapp.ui.theme.MotoAppTheme

/**
 * The app's one activity.
 *
 * The screens are one journey through one session — sign in, the day's areas, one area's houses, and
 * a reading and a collection from a house — so they are Composed here from one state holder rather
 * than being separate activities. A phone carried from a door to an office keeps one signed-in
 * session, one HTTP client and one cache.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MotoAppTheme {
                val model: MotoViewModel = viewModel()
                MotoApp(model)
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

    when (val current = screen) {
        Screen.Login -> LoginScreen(
            busy = busy,
            problem = problem,
            onSignIn = model::signIn,
        )

        Screen.Areas -> {
            val board by model.board.collectAsState()

            AreasScreen(
                userName = session?.user?.name.orEmpty(),
                load = board,
                problem = problem,
                onRefresh = model::loadAreas,
                onSignOut = model::signOut,
                onOpen = model::openArea,
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
            )
        }
    }
}
