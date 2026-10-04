package app.signull

import android.content.Context
import app.signull.core.sensors.BarometerMonitor
import app.signull.core.sensors.DeviceOrientation
import app.signull.core.sensors.GeoFix
import app.signull.core.sensors.LocationTracker
import app.signull.core.sensors.MagneticMonitor
import app.signull.core.sensors.OrientationTracker
import app.signull.core.sensors.StepDetector
import app.signull.core.signal.CellularMonitor
import app.signull.core.signal.InterferenceHub
import app.signull.core.signal.SignalHub
import app.signull.core.signal.WifiMonitor
import app.signull.data.AngleHandoff
import app.signull.data.MapRepository
import app.signull.data.SettingsRepository
import app.signull.data.db.SigNullDatabase
import app.signull.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Hand-rolled dependency container. One instance lives in [SigNullApplication]. */
class AppContainer(context: Context) {

    private val app = context.applicationContext

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { SigNullDatabase.create(app) }

    val maps by lazy { MapRepository(database.mapDao()) }

    val settings = SettingsRepository(app)

    val signals = SignalHub(CellularMonitor(app), WifiMonitor(app), scope)

    val location = LocationTracker(app)

    val orientationTracker = OrientationTracker(app)

    val steps = StepDetector(app)

    val barometer = BarometerMonitor(app)

    val angleHandoff = AngleHandoff()

    val magnetic = MagneticMonitor(app)

    val interference = InterferenceHub(signals, magnetic, scope)

    val updater = Updater(app, scope, settings)

    val fix: StateFlow<GeoFix?> =
        location.fixes().stateIn(scope, SharingStarted.WhileSubscribed(4_000), null)

    val orientation: StateFlow<DeviceOrientation?> =
        orientationTracker.orientation().stateIn(scope, SharingStarted.WhileSubscribed(1_000), null)

    val pressure: StateFlow<Float?> =
        barometer.pressureHpa().stateIn(scope, SharingStarted.WhileSubscribed(4_000), null)
}
