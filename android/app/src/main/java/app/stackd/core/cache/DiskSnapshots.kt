package app.stackd.core.cache

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Last-known screen data on disk, so a cold start paints real content at once
 * instead of "Loading…" while the first requests cross a slow mobile link.
 * The second tier under [MemoryCache]: memory survives navigation, this
 * survives process death. Screens still revalidate right after painting.
 *
 * App-private storage (backups are off in the manifest); wiped with the
 * memory cache on sign-out so one account's data never shows for another.
 */
class DiskSnapshots(private val dir: File) {
    constructor(context: Context) : this(File(context.filesDir, "snapshots"))
    private val json = Json { ignoreUnknownKeys = true }

    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")

    /** Null on miss or on any read/parse failure (e.g. an older app version's shape). */
    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(serializer, file(key).readText()) }.getOrNull()
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) = withContext(Dispatchers.IO) {
        runCatching {
            dir.mkdirs()
            // Write-then-rename so a kill mid-write never leaves a torn file.
            val tmp = File(dir, file(key).name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            java.nio.file.Files.move(
                tmp.toPath(), file(key).toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
