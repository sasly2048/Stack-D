package app.stackd.core.workmanager

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/**
 * Drains the pending session results once the device is back online.
 *
 * WorkManager owns the retry policy and the network constraint, which is why
 * this replaces the web app's "retry on next mount" approach — a queued result
 * lands when connectivity returns rather than whenever the user next happens to
 * open the app.
 */
class FinalizeQueueWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val owner = inputData.getString(KEY_OWNER) ?: return Result.success()
        val queue = FinalizeQueue(applicationContext)

        val pending = queue.readFor(owner)
        if (pending.isEmpty()) return Result.success()

        val submit = submitter ?: return Result.retry()

        val survivors = pending.filterNot { payload ->
            runCatching { submit(payload) }.getOrDefault(false)
        }
        queue.replaceFor(owner, survivors)

        // Anything still queued failed for a reason WorkManager should back off
        // on rather than spin against.
        return if (survivors.isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        private const val KEY_OWNER = "owner"
        const val WORK_NAME = "finalize-queue-flush"

        /**
         * Submits one payload, returning true once the server has it.
         *
         * Assigned at startup rather than injected: WorkManager constructs
         * workers reflectively, and a whole factory would be ceremony for a
         * single function. Replaced wholesale in tests.
         */
        @Volatile
        var submitter: (suspend (FinalizePayload) -> Boolean)? = null

        fun flush(context: Context, owner: String) {
            val request = OneTimeWorkRequestBuilder<FinalizeQueueWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(
                    androidx.work.Data.Builder().putString(KEY_OWNER, owner).build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                // A newer flush supersedes a pending one; they'd read the same
                // queue anyway.
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
