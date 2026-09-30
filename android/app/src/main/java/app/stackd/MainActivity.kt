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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.stackd.core.crash.CrashRecorder
import app.stackd.core.theme.MonoLabel
import app.stackd.core.theme.Stackd
import app.stackd.core.ui.EmberButton
import app.stackd.core.ui.GhostButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.stackd.core.theme.StackdTheme
import app.stackd.core.ui.FloatingTimerPill
import app.stackd.core.ui.GlobalRealtimeToasts
import app.stackd.core.ui.SprintInvites
import app.stackd.core.ui.OfflineBanner
import app.stackd.core.ui.QueueBadge
import app.stackd.core.workmanager.FinalizeQueueWorker
import io.github.jan.supabase.auth.status.SessionStatus
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.graphics.Brush
import app.stackd.core.theme.Obsidian
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** Calm post-crash screen (web route-error-boundary). Retry and dismiss both re-enter the app. */
@Composable
private fun SessionInterrupted(detail: String, onContinue: () -> Unit) {
    val colors = Stackd.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("STACK'D / INTERRUPTED", style = MonoLabel, color = colors.textMuted)
        Spacer(Modifier.height(16.dp))
        Text(
            "Session interrupted",
            style = MaterialTheme.typography.displayMedium,
            color = colors.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Something went wrong and the app restarted. Your progress is safe.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
        )
        Spacer(Modifier.height(12.dp))
        Text(detail.lineSequence().first(), style = MonoLabel, color = colors.textMuted)
        Spacer(Modifier.height(28.dp))
        EmberButton(text = "Retry", onClick = onContinue)
        Spacer(Modifier.height(12.dp))
        GhostButton(text = "Dismiss", onClick = onContinue)
    }
}

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        // Checkout runs in the browser; coming back is when an upgrade lands.
        val container = (application as StackdApplication).container
        lifecycleScope.launch { app.stackd.core.premium.Celebration.check(container) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        debugCelebrate(intent)
    }

    /** Debug builds only: `adb shell am start ... --es celebrate pro|elite` previews the celebration. */
    private fun debugCelebrate(intent: android.content.Intent?) {
        if (!BuildConfig.DEBUG) return
        intent?.getStringExtra("celebrate")?.takeIf { it == "pro" || it == "elite" }?.let {
            app.stackd.core.premium.Celebration.pending.value = it
        }
        // `--ez recap_pdf true` renders the recap PDF from sample data.
        if (intent?.getBooleanExtra("recap_pdf", false) == true) {
            app.stackd.feature.room.RecapPdf.share(
                this,
                app.stackd.data.ai.SessionRecap(
                    title = "A steady ninety minutes",
                    summary = "You held the stack for the full block with a single wobble near the end. " +
                        "Your focus score climbed past last week's average.",
                    reflections = listOf("The first 30 minutes were the cleanest.", "The one breach came right after the halfway mark."),
                    nextStep = "Try a 100-minute block tomorrow morning.",
                    score = 87,
                    xp = 240,
                ),
                app.stackd.data.ai.SessionRecapInput("r", 87, 240, 5400, 1, "gold", "ABC123"),
                "Raghavendra G",
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        debugCelebrate(intent)

        val container = (application as StackdApplication).container

        setContent {
            StackdTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var crash by remember { mutableStateOf(CrashRecorder.consume(applicationContext)) }
                    if (crash != null) {
                        SessionInterrupted(detail = crash!!, onContinue = { crash = null })
                        return@Surface
                    }
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
                            // RefreshFailure = the access token expired while
                            // offline; the stored session is still valid and the
                            // SDK refreshes it once the network returns. Treating
                            // it as signed-out dumped users on the Auth screen on
                            // every offline launch after an hour.
                            val signedIn = status is SessionStatus.Authenticated ||
                                status is SessionStatus.RefreshFailure
                            val initialSignedIn = remember { signedIn }

                            // Sign-out anywhere -> fresh nav graph at Auth (web
                            // onAuthStateChange). Only the signed-in -> signed-out
                            // edge resets: signing IN from Auth must not, or the
                            // confirm-identity step would be skipped.
                            var epoch by remember { mutableIntStateOf(0) }
                            var wasSignedIn by remember { mutableStateOf(signedIn) }
                            LaunchedEffect(signedIn) {
                                if (wasSignedIn && !signedIn) {
                                    container.cache.clear()
                                    epoch++
                                }
                                wasSignedIn = signedIn
                            }
                            val start = if (initialSignedIn && epoch == 0) Dest.Dashboard.route
                            else Dest.Auth.route
                            val navController = key(epoch) { rememberNavController() }
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
                            SprintInvites(
                                client = container.client,
                                userId = uid,
                                host = snackbarHost,
                                onJoin = { code -> navController.navigate(Dest.Room.of(code)) },
                            )

                            Box(Modifier.fillMaxSize()) {
                                key(epoch) {
                                    StackdNavHost(
                                        navController = navController,
                                        startDestination = start,
                                    )
                                }
                                val entry by navController.currentBackStackEntryAsState()

                                // Status-bar scrim: edge-to-edge screens scroll under
                                // the clock/icons; without this both are unreadable.
                                // Fades out below the bar so there's no hard seam.
                                Box(
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .windowInsetsTopHeight(WindowInsets.statusBars)
                                        .background(
                                            Brush.verticalGradient(
                                                0f to Obsidian,
                                                0.75f to Obsidian.copy(alpha = 0.92f),
                                                1f to Obsidian.copy(alpha = 0f),
                                            ),
                                        ),
                                )

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

                                // Post-upgrade celebration over everything; held
                                // while a room is on screen.
                                app.stackd.core.premium.CelebrationHost(
                                    suppressed = entry?.destination?.route == Dest.Room.route,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
