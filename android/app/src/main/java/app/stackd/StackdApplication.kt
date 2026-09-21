package app.stackd

import android.app.Application
import app.stackd.core.settings.SettingsStore

class StackdApplication : Application() {

    /**
     * Manual dependency container. The graph is small enough that Hilt's
     * annotation processing would cost more in build time than it saves;
     * revisit if this grows unwieldy.
     */
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
    }
}
