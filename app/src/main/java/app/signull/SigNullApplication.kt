package app.signull

import android.app.Application
import app.signull.update.UpdateScheduler
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SigNullApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Keep the background update check in step with the setting.
        container.scope.launch {
            container.settings.settings
                .map { it.autoUpdate }
                .distinctUntilChanged()
                .collect { UpdateScheduler.apply(this@SigNullApplication, it) }
        }
    }
}
