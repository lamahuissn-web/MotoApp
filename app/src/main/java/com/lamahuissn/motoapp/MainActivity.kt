package com.lamahuissn.motoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lamahuissn.motoapp.ui.ConnectionScreen
import com.lamahuissn.motoapp.ui.theme.MotoAppTheme

/**
 * The app's one activity.
 *
 * Everything is Compose: the collector's sheet, the manager's month board and the sign-in door are
 * screens of this activity rather than separate activities, because a phone that is carried between
 * a door and an office keeps one signed-in session, one HTTP client and one cache.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MotoAppTheme {
                ConnectionScreen()
            }
        }
    }
}
