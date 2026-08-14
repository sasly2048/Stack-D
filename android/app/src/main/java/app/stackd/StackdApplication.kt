package app.stackd

import android.app.Application
import app.stackd.core.AppContainer

class StackdApplication : Application() {

    /**
     * Manual dependency container. The graph is small enough that Hilt's
     * annotation processing would cost more in build time than it saves;
     * revisit if this grows unwieldy. [AppContainer] owns the settings store,
     * repositories, and finalize queue — one instance for the process.
     */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
