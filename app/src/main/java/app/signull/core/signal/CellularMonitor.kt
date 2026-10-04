package app.signull.core.signal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.signull.core.util.Format
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Streams the mobile-data signal of the SIM that carries data (the one your hotspot uses).
 *
 * Android pushes signal strength changes through a callback. When location permission is granted we
 * also poll the modem for fresh serving-cell measurements every [POLL_MS], which gives faster and
 * finer updates than the callback alone on many devices.
 */
class CellularMonitor(private val context: Context) {

    private val telephony: TelephonyManager? = context.getSystemService(TelephonyManager::class.java)

    // Every radio callback and state change runs on this one thread.
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "signull-radio").apply { isDaemon = true }
    }

    val isSupported: Boolean =
        telephony != null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    fun snapshots(): Flow<CellularSnapshot> = callbackFlow {
        val base = telephony
        if (base == null || !isSupported) {
            trySend(CellularSnapshot.NoService)
            awaitClose { }
            return@callbackFlow
        }
        val dataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        val tm = if (SubscriptionManager.isValidSubscriptionId(dataSubId)) {
            base.createForSubscriptionId(dataSubId)
        } else {
            base
        }
        val state = RadioState()

        fun publish() {
            trySend(
                CellParser.parse(
                    strength = state.strength,
                    strengthAt = state.strengthAt,
                    cells = state.cells,
                    cellsAt = state.cellsAt,
                    operator = runCatching { tm.networkOperatorName }.getOrNull(),
                ),
            )
        }

        val onStrength: (SignalStrength) -> Unit = { strength ->
            state.strength = strength
            state.strengthAt = SystemClock.elapsedRealtime()
            publish()
        }

        executor.execute {
            state.strength = runCatching { tm.signalStrength }.getOrNull()
            state.strengthAt = SystemClock.elapsedRealtime()
            publish()
        }

        val registration: StrengthRegistration = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            CallbackRegistration(tm, executor, onStrength)
        } else {
            ListenerRegistration(tm, executor, onStrength)
        }

        val poller = launch {
            while (isActive) {
                if (hasFineLocation()) {
                    executor.execute { requestCellInfo(tm, executor, state, ::publish) }
                }
                delay(POLL_MS)
            }
        }

        awaitClose {
            poller.cancel()
            runCatching { registration.unregister() }
        }
    }

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun requestCellInfo(
        tm: TelephonyManager,
        executor: Executor,
        state: RadioState,
        publish: () -> Unit,
    ) {
        try {
            tm.requestCellInfoUpdate(
                executor,
                object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cellInfo: MutableList<CellInfo>) {
                        state.cells = cellInfo.toList()
                        state.cellsAt = SystemClock.elapsedRealtime()
                        publish()
                    }

                    override fun onError(errorCode: Int, detail: Throwable?) = Unit
                },
            )
        } catch (_: SecurityException) {
        } catch (_: IllegalStateException) {
        }
    }

    private class RadioState {
        var strength: SignalStrength? = null
        var strengthAt: Long = 0L
        var cells: List<CellInfo> = emptyList()
        var cellsAt: Long = 0L
    }

    private interface StrengthRegistration {
        fun unregister()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private class CallbackRegistration(
        private val tm: TelephonyManager,
        executor: Executor,
        onStrength: (SignalStrength) -> Unit,
    ) : StrengthRegistration {
        private val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onStrength(signalStrength)
        }

        init {
            tm.registerTelephonyCallback(executor, callback)
        }

        override fun unregister() = tm.unregisterTelephonyCallback(callback)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private class ListenerRegistration(
        private val tm: TelephonyManager,
        executor: Executor,
        onStrength: (SignalStrength) -> Unit,
    ) : StrengthRegistration {
        private val listener = object : PhoneStateListener(executor) {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) = onStrength(signalStrength)
        }

        init {
            tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        }

        override fun unregister() = tm.listen(listener, PhoneStateListener.LISTEN_NONE)
    }

    private companion object {
        const val POLL_MS = 1_500L
    }
}

/** Turns Android's telephony objects into a [CellularSnapshot]. */
internal object CellParser {

    fun parse(
        strength: SignalStrength?,
        strengthAt: Long,
        cells: List<CellInfo>,
        cellsAt: Long,
        operator: String?,
    ): CellularSnapshot {
        val fromStrength = strength?.cellSignalStrengths.orEmpty()
        val registered = cells.filter { it.isRegistered }
        val fromCells = registered.mapNotNull { signalOf(it) }
        // Prefer whichever source reported most recently, and fill gaps from the other.
        val merged = if (cellsAt > strengthAt) merge(fromCells, fromStrength) else merge(fromStrength, fromCells)

        val lte = merged.firstNotNullOfOrNull { s -> (s as? CellSignalStrengthLte)?.takeIf { it.rsrp.valid(-140, -43) } }
        val nr = merged.firstNotNullOfOrNull { s -> (s as? CellSignalStrengthNr)?.takeIf { it.ssRsrp.valid(-156, -31) } }
        val wcdma = merged.firstNotNullOfOrNull { s -> (s as? CellSignalStrengthWcdma)?.takeIf { it.dbm.valid(-120, -24) } }
        val tdscdma = merged.firstNotNullOfOrNull { s -> (s as? CellSignalStrengthTdscdma)?.takeIf { it.rscp.valid(-120, -24) } }
        val gsm = merged.firstNotNullOfOrNull { s -> (s as? CellSignalStrengthGsm)?.takeIf { it.dbm.valid(-113, -51) } }
        val cdma = merged.firstNotNullOfOrNull { s -> (s as? CellSignalStrengthCdma)?.takeIf { it.dbm.valid(-120, -1) } }

        val serving = registered.firstNotNullOfOrNull { servingCell(it) }
        val name = operator?.takeIf { it.isNotBlank() }

        return when {
            lte != null -> lteSnapshot(lte, nr, serving, name)
            nr != null -> nrSnapshot(nr, serving, name)
            wcdma != null -> wcdmaSnapshot(wcdma, serving, name)
            tdscdma != null -> CellularSnapshot(
                tech = RadioTech.TDSCDMA,
                kind = SignalKind.TDSCDMA_RSCP,
                dbm = tdscdma.rscp,
                level = tdscdma.level,
                operatorName = name,
                cell = serving,
                details = listOf(Metric("RSCP", Format.signed(tdscdma.rscp), "dBm"), Metric("Level", "${tdscdma.level}/4")),
            )
            gsm != null -> gsmSnapshot(gsm, serving, name)
            cdma != null -> CellularSnapshot(
                tech = RadioTech.CDMA,
                kind = SignalKind.CDMA_RSSI,
                dbm = cdma.dbm,
                level = cdma.level,
                operatorName = name,
                details = listOf(Metric("RSSI", Format.signed(cdma.dbm), "dBm"), Metric("Level", "${cdma.level}/4")),
            )
            else -> CellularSnapshot.NoService.copy(operatorName = name)
        }
    }

    private fun lteSnapshot(lte: CellSignalStrengthLte, nr: CellSignalStrengthNr?, cell: ServingCell?, operator: String?): CellularSnapshot {
        val rsrq = lte.rsrq.validOrNull(-34, 3)
        val sinr = lte.rssnr.validOrNull(-20, 30)
        val rssi = lte.rssi.validOrNull(-113, -51)
        val towerM = lte.timingAdvance.validOrNull(0, 1282)?.let { (it * 78.12f).toInt() }
        val nrDbm = nr?.ssRsrp
        val details = buildList {
            add(Metric("RSRP", Format.signed(lte.rsrp), "dBm"))
            rsrq?.let { add(Metric("RSRQ", Format.signed(it), "dB")) }
            sinr?.let { add(Metric("SINR", Format.signed(it), "dB")) }
            rssi?.let { add(Metric("RSSI", Format.signed(it), "dBm")) }
            lte.cqi.validOrNull(0, 15)?.let { add(Metric("CQI", "$it", "/15")) }
            nrDbm?.let { add(Metric("5G SS‑RSRP", Format.signed(it), "dBm")) }
            nr?.ssSinr?.validOrNull(-23, 40)?.let { add(Metric("5G SINR", Format.signed(it), "dB")) }
            add(Metric("Bars", "${lte.level}", "/4"))
            cell?.band?.let { add(bandMetric(it)) }
            cell?.pci?.let { add(Metric("PCI", "$it")) }
            cell?.channel?.let { add(Metric("EARFCN", "$it")) }
            towerM?.let { add(Metric("Tower", Format.meters(it.toFloat()))) }
        }
        return CellularSnapshot(
            tech = if (nr != null) RadioTech.NR_NSA else RadioTech.LTE,
            kind = SignalKind.LTE_RSRP,
            dbm = lte.rsrp,
            rsrq = rsrq,
            sinr = sinr?.toFloat(),
            rssi = rssi,
            nrDbm = nrDbm,
            level = lte.level,
            operatorName = operator,
            cell = cell,
            towerDistanceM = towerM,
            details = details,
        )
    }

    private fun nrSnapshot(nr: CellSignalStrengthNr, cell: ServingCell?, operator: String?): CellularSnapshot {
        val rsrq = nr.ssRsrq.validOrNull(-43, 20)
        val sinr = nr.ssSinr.validOrNull(-23, 40)
        val details = buildList {
            add(Metric("SS‑RSRP", Format.signed(nr.ssRsrp), "dBm"))
            rsrq?.let { add(Metric("SS‑RSRQ", Format.signed(it), "dB")) }
            sinr?.let { add(Metric("SS‑SINR", Format.signed(it), "dB")) }
            nr.csiRsrp.validOrNull(-156, -31)?.let { add(Metric("CSI‑RSRP", Format.signed(it), "dBm")) }
            add(Metric("Bars", "${nr.level}", "/4"))
            cell?.band?.let { add(bandMetric(it)) }
            cell?.pci?.let { add(Metric("PCI", "$it")) }
            cell?.channel?.let { add(Metric("NR‑ARFCN", "$it")) }
        }
        return CellularSnapshot(
            tech = RadioTech.NR_SA,
            kind = SignalKind.NR_RSRP,
            dbm = nr.ssRsrp,
            rsrq = rsrq,
            sinr = sinr?.toFloat(),
            level = nr.level,
            operatorName = operator,
            cell = cell,
            details = details,
        )
    }

    private fun wcdmaSnapshot(wcdma: CellSignalStrengthWcdma, cell: ServingCell?, operator: String?): CellularSnapshot {
        val ecNo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) wcdma.ecNo.validOrNull(-24, 1) else null
        val details = buildList {
            add(Metric("RSCP", Format.signed(wcdma.dbm), "dBm"))
            ecNo?.let { add(Metric("Ec/No", Format.signed(it), "dB")) }
            add(Metric("Bars", "${wcdma.level}", "/4"))
            cell?.pci?.let { add(Metric("PSC", "$it")) }
            cell?.channel?.let { add(Metric("UARFCN", "$it")) }
        }
        return CellularSnapshot(
            tech = RadioTech.WCDMA,
            kind = SignalKind.WCDMA_RSCP,
            dbm = wcdma.dbm,
            level = wcdma.level,
            operatorName = operator,
            cell = cell,
            details = details,
        )
    }

    private fun gsmSnapshot(gsm: CellSignalStrengthGsm, cell: ServingCell?, operator: String?): CellularSnapshot {
        val towerM = gsm.timingAdvance.validOrNull(0, 219)?.let { (it * 553.5f).toInt() }
        val details = buildList {
            add(Metric("RSSI", Format.signed(gsm.dbm), "dBm"))
            gsm.bitErrorRate.validOrNull(0, 7)?.let { add(Metric("BER", "$it", "/7")) }
            add(Metric("Bars", "${gsm.level}", "/4"))
            cell?.channel?.let { add(Metric("ARFCN", "$it")) }
            towerM?.let { add(Metric("Tower", Format.meters(it.toFloat()))) }
        }
        return CellularSnapshot(
            tech = RadioTech.GSM,
            kind = SignalKind.GSM_RSSI,
            dbm = gsm.dbm,
            level = gsm.level,
            operatorName = operator,
            cell = cell,
            towerDistanceM = towerM,
            details = details,
        )
    }

    /** "B28 · 700 MHz" becomes value "B28" with unit "700 MHz". */
    private fun bandMetric(label: String): Metric {
        val parts = label.split(" · ", limit = 2)
        return Metric("Band", parts[0], parts.getOrNull(1).orEmpty())
    }

    private fun merge(primary: List<CellSignalStrength>, secondary: List<CellSignalStrength>): List<CellSignalStrength> =
        primary + secondary.filter { s -> primary.none { it.javaClass == s.javaClass } }

    @Suppress("DEPRECATION")
    private fun signalOf(info: CellInfo): CellSignalStrength? = when (info) {
        is CellInfoLte -> info.cellSignalStrength
        is CellInfoNr -> info.cellSignalStrength
        is CellInfoWcdma -> info.cellSignalStrength
        is CellInfoTdscdma -> info.cellSignalStrength
        is CellInfoGsm -> info.cellSignalStrength
        is CellInfoCdma -> info.cellSignalStrength
        else -> null
    }

    private fun servingCell(info: CellInfo): ServingCell? = when (info) {
        is CellInfoLte -> info.cellIdentity.let { id ->
            val band = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.firstOrNull() else null
            val earfcn = id.earfcn.validOrNull(0, 262_143)
            ServingCell(
                pci = id.pci.validOrNull(0, 503),
                band = Bands.lteLabel(band, earfcn),
                channel = earfcn,
                cellId = id.ci.validOrNull(0, 268_435_455)?.toLong(),
                areaCode = id.tac.validOrNull(0, 65_535),
                mcc = id.mccString,
                mnc = id.mncString,
            )
        }
        is CellInfoNr -> (info.cellIdentity as? CellIdentityNr)?.let { id ->
            val band = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.firstOrNull() else null
            val arfcn = id.nrarfcn.validOrNull(0, 3_279_165)
            ServingCell(
                pci = id.pci.validOrNull(0, 1007),
                band = Bands.nrLabel(band, arfcn),
                channel = arfcn,
                cellId = id.nci.takeIf { it != Long.MAX_VALUE && it >= 0 },
                areaCode = id.tac.validOrNull(0, 16_777_215),
                mcc = id.mccString,
                mnc = id.mncString,
            )
        }
        is CellInfoWcdma -> info.cellIdentity.let { id ->
            ServingCell(
                pci = id.psc.validOrNull(0, 511),
                channel = id.uarfcn.validOrNull(0, 16_383),
                cellId = id.cid.validOrNull(0, 268_435_455)?.toLong(),
                areaCode = id.lac.validOrNull(0, 65_535),
                mcc = id.mccString,
                mnc = id.mncString,
            )
        }
        is CellInfoGsm -> info.cellIdentity.let { id ->
            ServingCell(
                pci = id.bsic.validOrNull(0, 63),
                channel = id.arfcn.validOrNull(0, 65_535),
                cellId = id.cid.validOrNull(0, 65_535)?.toLong(),
                areaCode = id.lac.validOrNull(0, 65_535),
                mcc = id.mccString,
                mnc = id.mncString,
            )
        }
        else -> null
    }

    private fun Int.valid(min: Int, max: Int): Boolean = this != CellInfo.UNAVAILABLE && this in min..max

    private fun Int.validOrNull(min: Int, max: Int): Int? = takeIf { it.valid(min, max) }
}
