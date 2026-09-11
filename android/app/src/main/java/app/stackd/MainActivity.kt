package app.stackd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.stackd.core.theme.StackdTheme
import app.stackd.core.ui.FloatingTimerPill
import app.stackd.core.ui.GlobalRealtimeToasts
import app.stackd.core.ui.OfflineBanner
import app.stackd.core.ui.QueueBadge
import app.stackd.core.workmanager.FinalizeQueueWorker
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.flowOf

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
                            val uid = if (signedIn) container.auth.currentUserId else null

                            // Pending-finalize count for the queue badge. When
                            // signed out there's nothing to observe, so fall back
                            // to a constant-0 flow and the badge stays hidden.
                            val pendingCount by remember(uid) {
                                if (uid != null) container.finalizeQueue.sizeFlow(uid)
                                else flowOf(0)
                            }.collectAsStateWithLifecycle(initialValue = 0)

                            val snackbarHost = remember { SnackbarHostState() }

                            // Root-scope realtime → social toasts, mirroring web.
                            GlobalRealtimeToasts(
                                client = container.client,
                                userId = uid,
                                host = snackbarHost,
                            )

                            Box(Modifier.fillMaxSize()) {
                                StackdNavHost(
                                    navController = navController,
                                    startDestination = start,
                                )
                                val entry by navController.currentBackStackEntryAsState()

                                // Offline banner pinned to the top; queue badge
                                // and floating timer share the bottom.
                                OfflineBanner(modifier = Modifier.align(Alignment.TopCenter))

                                QueueBadge(
                                    ownerId = uid,
                                    count = pendingCount,
                                    onRetry = {
                                        uid?.let {
                                            FinalizeQueueWorker.flush(applicationContext, it)
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 16.dp, bottom = 24.dp),
                                )

                                FloatingTimerPill(
                                    isOnRoomScreen = entry?.destination?.route == Dest.Room.route,
                                    onOpenRoom = { code -> navController.navigate(Dest.Room.of(code)) },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 32.dp),
                                )

                                // Sits above the floating timer pill so a toast
                                // isn't drawn behind it.
                                SnackbarHost(
                                    hostState = snackbarHost,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 96.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
