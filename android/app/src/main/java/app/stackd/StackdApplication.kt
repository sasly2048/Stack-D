package app.stackd

import android.app.Application
import app.stackd.core.AppContainer
import app.stackd.core.workmanager.FinalizeQueueWorker
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StackdApplication : Application() {

    /**
     * Manual dependency container. The graph is small enough that Hilt's
     * annotation processing would cost more in build time than it saves;
     * revisit if this grows unwieldy. [AppContainer] owns the settings store,
     * repositories, and finalize queue — one instance for the process.
     */
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        wireFinalizeSubmitter()
        reportTimezoneOnSignIn()
    }

    /**
     * Mirrors the web's `reportTimezone` in use-auth: whenever a session is
     * live, tell the server this device's IANA zone (deduped per user+zone) so
     * streak / daily-reward day boundaries roll at local midnight, not UTC.
     */
    private fun reportTimezoneOnSignIn() {
        appScope.launch {
            container.auth.sessionStatus.collect { status ->
                if (status !is SessionStatus.Authenticated) return@collect
                val userId = container.auth.currentUserId ?: return@collect
                val tz = java.util.TimeZone.getDefault().id ?: return@collect
                if (container.settings.reportedTimezone(userId) == tz) return@collect
                runCatching { container.profiles.setMyTimezone(tz) }
                    .onSuccess { container.settings.markTimezoneReported(userId, tz) }
                // Failure: retried on the next auth event; boundaries stay UTC.
            }
        }
    }

    /**
     * Teaches the finalize queue how to actually submit a parked result. The
     * worker is constructed reflectively by WorkManager, so it can't hold a
     * repository — instead it reads this process-wide submitter, set once here.
     *
     * The owner guard is a fail-safe: a result is queued under the account that
     * earned it, and the worker may run much later, after a sign-out and a
     * different sign-in. Submitting then would write one person's session under
     * another's identity, so a payload whose owner isn't the current user is
     * left in the queue (returns false) rather than misattributed. It drains on
     * the next flush under the right account.
     */
    private fun wireFinalizeSubmitter() {
        FinalizeQueueWorker.submitter = submit@{ payload ->
            if (container.auth.currentUserId != payload.owner) return@submit false
            container.rooms.finalizeSession(
                roomId = payload.roomId,
                score = payload.score,
                xp = payload.xp,
                durationSeconds = payload.durationSeconds,
                breachesCount = payload.breachesCount,
                tier = payload.tier,
                scoringVersion = payload.scoringVersion,
                abandonmentSeconds = payload.abandonmentSeconds,
            ) != null
        }
    }
}
