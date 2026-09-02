package app.stackd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.stackd.core.theme.StackdTheme
import app.stackd.core.ui.FloatingTimerPill
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as StackdApplication).container

        setContent {
            StackdTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // The session restores from storage asynchronously, so the
                    // start destination CANNOT be read synchronously in onCreate —
                    // doing that raced the restore and dropped a signed-in user on
                    // the Auth screen (and left server calls unauthenticated, which
                    // showed as "You're not signed in" on Start). Observe the
                    // status instead: hold a splash while Initializing, then route
                    // by the settled result, reactively.
                    val status by container.auth.sessionStatus
                        .collectAsStateWithLifecycle(initialValue = SessionStatus.Initializing)

                    when (status) {
                        is SessionStatus.Initializing -> Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                        else -> {
                            val signedIn = status is SessionStatus.Authenticated
                            val start = if (signedIn) Dest.Dashboard.route else Dest.Auth.route
                            val navController = rememberNavController()
                            Box(Modifier.fillMaxSize()) {
                                StackdNavHost(
                                    navController = navController,
                                    startDestination = start,
                                )
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
    }
}
