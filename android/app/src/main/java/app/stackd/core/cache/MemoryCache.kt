package app.stackd.core.cache

import java.util.concurrent.ConcurrentHashMap

/**
 * A tiny process-lifetime cache that survives navigation.
 *
 * The problem it solves: every feature ViewModel is scoped to its nav
 * destination, so navigating away and back recreates it and re-runs `load()`
 * cold — spinner, network, populate, every single time. That is what makes the
 * app feel dated. There is no Room DB and no other cache tier.
 *
 * This holds the last successfully-loaded UI state per screen, keyed by a
 * string (typically "screen:userId"). A recreated ViewModel reads its last
 * state back instantly for a spinner-free re-entry, then revalidates in the
 * background and overwrites the entry — classic stale-while-revalidate.
 *
 * Scope is deliberately in-memory only: it survives navigation (the actual
 * complaint) but not process death. Cold start after the app is killed still
 * fetches fresh; persisting to DataStore is a later step if that also needs to
 * be instant. Values are whole immutable UI-state objects, so no defensive
 * copying is needed. Backed by a ConcurrentHashMap because ViewModels on
 * different dispatchers may read/write concurrently.
 *
 * ponytail: in-memory only, add DataStore persistence if cold-start-after-kill
 * needs to be instant too.
 */
class MemoryCache {

    private val store = ConcurrentHashMap<String, Any>()

    /** Last cached value for [key], or null if nothing has been stored yet. */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = store[key] as? T

    /** Stores [value] as the latest state for [key]. */
    fun <T : Any> put(key: String, value: T) {
        store[key] = value
    }

    /** Drops a single entry — e.g. on sign-out, to avoid leaking one user's data. */
    fun invalidate(key: String) {
        store.remove(key)
    }

    /** Clears everything — call on sign-out so the next user starts clean. */
    fun clear() {
        store.clear()
    }
}
