package app.stackd.core

import android.content.Context
import app.stackd.core.settings.SettingsStore
import app.stackd.core.supabase.SupabaseModule
import app.stackd.core.workmanager.FinalizeQueue
import app.stackd.data.auth.AuthRepository
import app.stackd.data.profile.ProfileRepository
import app.stackd.data.room.RoomRepository
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

    val client: SupabaseClient get() = SupabaseModule.client

    val settings: SettingsStore = SettingsStore(appContext)

    val finalizeQueue: FinalizeQueue = FinalizeQueue(appContext)

    val auth: AuthRepository by lazy { AuthRepository(settings) }

    val profiles: ProfileRepository by lazy { ProfileRepository(client) }

    val rooms: RoomRepository by lazy { RoomRepository(client) }
}
