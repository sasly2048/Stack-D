package app.stackd.core

import android.content.Context
import app.stackd.core.settings.SettingsStore
import app.stackd.core.supabase.SupabaseModule
import app.stackd.core.workmanager.FinalizeQueue
import app.stackd.data.auth.AuthRepository
import app.stackd.data.premium.PremiumRepository
import app.stackd.data.profile.ProfileRepository
import app.stackd.data.room.RoomRepository
import app.stackd.data.progression.ProgressionRepository
import app.stackd.data.social.FriendsRepository
import app.stackd.data.social.LeaderboardRepository
import app.stackd.data.vault.VaultRepository
import io.github.jan.supabase.SupabaseClient

/**
 * Manual dependency container — no Hilt.
 *
 * The graph is a handful of stateless repositories over one Supabase client;
 * annotation processing would cost more build time than it saves. Repositories
 * are concrete classes, one per web `*.functions.ts` file, so porting stays a
 * mechanical read-and-translate rather than a redesign.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** For WorkManager enqueues, which need an application Context. */
    val appContextForWork: Context get() = appContext

    val client: SupabaseClient get() = SupabaseModule.client

    val settings: SettingsStore = SettingsStore(appContext)

    val finalizeQueue: FinalizeQueue = FinalizeQueue(appContext)

    val auth: AuthRepository by lazy { AuthRepository(settings) }

    val profiles: ProfileRepository by lazy { ProfileRepository(client) }

    val rooms: RoomRepository by lazy { RoomRepository(client) }

    val premium: PremiumRepository by lazy { PremiumRepository(client) }

    val leaderboard: LeaderboardRepository by lazy { LeaderboardRepository(client) }

    val vault: VaultRepository by lazy { VaultRepository(client) }

    val friends: FriendsRepository by lazy { FriendsRepository(client) }

    val progression: ProgressionRepository by lazy { ProgressionRepository(client) }
}
