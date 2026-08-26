package app.stackd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.stackd.core.theme.StackdTheme
import app.stackd.core.ui.FloatingTimerPill

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Land on a real screen, not the still-placeholder Landing. A returning
        // signed-in user goes straight to the dashboard; everyone else to auth.
        // Landing/Philosophy are marketing surfaces for a later phase.
        val container = (application as StackdApplication).container
        val start = if (container.auth.currentUserId != null) {
            Dest.Dashboard.route
        } else {
            Dest.Auth.route
        }

        setContent {
            StackdTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    Box(Modifier.fillMaxSize()) {
                        StackdNavHost(
                            navController = navController,
                            startDestination = start,
                        )
                        // Web's floating-timer: countdown chip anywhere outside
                        // the room while a session runs; tap returns to it.
                        val entry by navController.currentBackStackEntryAsState()
                        FloatingTimerPill(
                            isOnRoomScreen = entry?.destination?.route == Dest.Room.route,
                            onOpenRoom = { code -> navController.navigate(Dest.Room.of(code)) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 32.dp),
                        )
                    }
                }
            }
        }
    }
}
