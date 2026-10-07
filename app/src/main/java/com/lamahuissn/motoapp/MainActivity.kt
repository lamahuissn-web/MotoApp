package com.lamahuissn.motoapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lamahuissn.motoapp.ui.AreasScreen
import com.lamahuissn.motoapp.ui.HouseScreen
import com.lamahuissn.motoapp.ui.HousesScreen
import com.lamahuissn.motoapp.ui.LoginScreen
import com.lamahuissn.motoapp.ui.ManagerScreen
import com.lamahuissn.motoapp.ui.MotoViewModel
import com.lamahuissn.motoapp.ui.QueueScreen
import com.lamahuissn.motoapp.ui.Screen
import com.lamahuissn.motoapp.ui.WalletScreen
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

/**
 * Ask, once, to be allowed to speak to the collector from the notification shade.
 *
 * It is asked the first time a session is open rather than on launch: the request means something to a
 * person who is already signed in and has work to lose. Android remembers a refusal; `asked` only keeps
 * the dialog from being raised twice in one run.
 */
@Composable
private fun AskToBeToldWhenSomethingWaits() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!asked && !granted) {
            asked = true
            ask.launch(Manifest.permission.POST_NOTIFICATIONS)
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
    val queue by model.queue.collectAsState()
    val reversal by model.reversal.collectAsState()
    val wallet by model.wallet.collectAsState()
    val receiptToShare by model.receiptToShare.collectAsState()
    val correction by model.correction.collectAsState()

    // The phone should be able to tell him, unopened, that his recorded work has not reached the office
    // (spec 010). Asked once, from Android 13 up; a refusal costs the notifications and nothing else.
    if (session != null) AskToBeToldWhenSomethingWaits()

    // The phone's own back button walks the same journey the arrows do. On a home screen it stays
    // disabled, so the system ends the app the way the user expects instead of us swallowing the press.
    val canGoBack = when (screen) {
        is Screen.House, is Screen.Houses, Screen.Queue -> true
        Screen.Areas -> session?.user?.isAdmin == true
        else -> false
    }

    BackHandler(enabled = canGoBack) { model.back() }

    // Coming back to the app after the job that runs in a pocket: whatever it settled must show here.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) model.onResumed()
        }

        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    // The bar is for the screens somebody lives in; a house, a sheet or the queue's own detail is a step
    // under them, and a bar there would be a door back to where he already is.
    val topLevel = screen is Screen.Areas || screen is Screen.Manager ||
        screen is Screen.Queue || screen is Screen.Wallet
    val owed = queue.count { it.isOpen }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            when (val current = screen) {
        Screen.Login -> LoginScreen(
            busy = busy,
            problem = problem,
            onSignIn = model::signIn,
        )

        Screen.Areas -> {
            val board by model.board.collectAsState()
            val search by model.search.collectAsState()

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
                // Only a manager's phone draws the way into a reversal; a collector's file is a reading.
                canReverse = session?.user?.isAdmin == true,
                reversal = reversal,
                onReverse = model::reverseReceipt,
                onCloseReversal = model::clearReversal,
                // Which receipts are his own to send again — the endpoint's rule, drawn ahead of the tap.
                myId = session?.user?.id,
                correction = correction,
                onCorrect = model::correctReading,
                onCloseCorrection = model::clearCorrection,
                receiptToShare = receiptToShare,
                onSendReceipt = model::shareReceiptAgain,
                onShareDone = model::clearReceiptToShare,
            )
        }

        Screen.Wallet -> {
            // The collector's own purse — no bar item for a manager reaches it, but the branch is total.
            WalletScreen(
                load = wallet,
                problem = problem,
                onRefresh = model::loadWallet,
                onSignOut = model::signOut,
                staleAsOf = staleAsOf,
                refreshing = refreshing,
                onPullRefresh = model::refresh,
                onOpenHouse = model::openHouseFromWallet,
                receiptToShare = receiptToShare,
                onSendReceipt = model::shareReceiptAgain,
                onShareDone = model::clearReceiptToShare,
            )
        }

        Screen.Manager -> {
            val manager by model.manager.collectAsState()
            val handover by model.handover.collectAsState()

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
                handover = handover,
                onHandover = model::recordHandover,
                onCloseHandover = model::clearHandover,
                reversal = reversal,
                onReverse = model::reverseReceipt,
                onCloseReversal = model::clearReversal,
            )
        }
            }
        }

        if (topLevel) {
            MotoBottomBar(
                manager = session?.user?.isAdmin == true,
                current = screen,
                owed = owed,
                onManager = model::openBoard,
                onAreas = model::openAreas,
                onWallet = model::openWallet,
                onQueue = model::openQueue,
            )
        }
    }
}

/**
 * The two or three doors every signed-in user should have under his thumb.
 *
 * It exists for a reason the app was missing: «الطابور» was reachable only through a card that appears
 * when something is owed, so a collector who wanted to look at it — or a manager who wanted it from his
 * board — had no way in at all. A manager also gets his board as a door rather than only the button at the
 * bottom of the areas screen.
 *
 * It is drawn on the screens somebody lives in, never over a house or an area sheet.
 */
@Composable
private fun MotoBottomBar(
    manager: Boolean,
    current: Screen,
    owed: Int,
    onManager: () -> Unit,
    onAreas: () -> Unit,
    onWallet: () -> Unit,
    onQueue: () -> Unit,
) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.navigationBarsPadding(),
        ) {
            if (manager) {
                NavigationBarItem(
                    selected = current is Screen.Manager,
                    onClick = onManager,
                    icon = { Icon(Icons.Filled.Bolt, contentDescription = null) },
                    label = { Text(stringResource(R.string.mgr_subtitle)) },
                )
            }

            NavigationBarItem(
                selected = current is Screen.Areas,
                onClick = onAreas,
                icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                label = { Text(stringResource(R.string.areas_section)) },
            )

            if (!manager) {
                // «محفظتي» is the collector's own money: a manager has his board, and no pocket in these
                // books, so the door is not drawn for him at all.
                NavigationBarItem(
                    selected = current is Screen.Wallet,
                    onClick = onWallet,
                    icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null) },
                    label = { Text(stringResource(R.string.wallet_subtitle)) },
                )
            }

            NavigationBarItem(
                selected = current is Screen.Queue,
                onClick = onQueue,
                icon = {
                    // What is owed rides on the door itself: the one fact a man holding cash must not have
                    // to open a screen to remember.
                    if (owed > 0) {
                        BadgedBox(badge = { Badge { Text(owed.toString()) } }) {
                            Icon(Icons.Filled.CloudOff, contentDescription = null)
                        }
                    } else {
                        Icon(Icons.Filled.CloudOff, contentDescription = null)
                    }
                },
                label = { Text(stringResource(R.string.queue_subtitle)) },
            )
        }
    }
}
