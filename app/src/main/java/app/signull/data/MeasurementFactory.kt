package app.signull.data

import app.signull.core.angle.AngleResult
import app.signull.core.sensors.GeoFix
import app.signull.core.signal.CellularSnapshot
import app.signull.core.signal.RadioTech
import app.signull.core.signal.WifiSnapshot
import app.signull.data.db.MeasurementEntity

/** Builds a database row from live readings averaged over a short capture window. */
object MeasurementFactory {

    fun create(
        cellular: CellularSnapshot?,
        cellularAverage: Int?,
        cellularSupported: Boolean,
        wifi: WifiSnapshot?,
        wifiAverage: Int?,
        angle: AngleResult?,
        fix: GeoFix?,
        pressureHpa: Float?,
        spotId: Long = 0,
        interference: Int = 0,
        magneticUt: Float? = null,
    ): MeasurementEntity {
        // A phone with a modem but no service is a confirmed dead zone worth recording.
        val cellTech = when {
            cellular == null -> null
            cellular.hasSignal -> cellular.tech.name
            cellularSupported -> RadioTech.NONE.name
            else -> null
        }
        val wifiConnected = wifi?.connected == true
        return MeasurementEntity(
            spotId = spotId,
            cellKind = cellular?.kind?.name,
            cellTech = cellTech,
            cellDbm = cellularAverage ?: cellular?.dbm,
            cellRsrq = cellular?.rsrq,
            cellSinr = cellular?.sinr,
            cellNrDbm = cellular?.nrDbm,
            cellOperator = cellular?.operatorName,
            cellBand = cellular?.cell?.band,
            cellPci = cellular?.cell?.pci,
            wifiRssi = if (wifiConnected) wifiAverage ?: wifi.rssi else null,
            wifiSsid = if (wifiConnected) wifi.ssid else null,
            wifiBssid = if (wifiConnected) wifi.bssid else null,
            wifiFreqMhz = if (wifiConnected) wifi.frequencyMhz else null,
            wifiLinkMbps = if (wifiConnected) wifi.linkSpeedMbps else null,
            bestSource = angle?.source?.name,
            bestPose = angle?.pose?.name,
            bestHeadingDeg = angle?.headingDeg,
            bestDbm = angle?.bestDbm,
            worstDbm = angle?.worstDbm,
            latitude = fix?.latitude,
            longitude = fix?.longitude,
            accuracyM = fix?.accuracyM,
            altitudeM = fix?.altitudeM,
            pressureHpa = pressureHpa,
            interference = interference,
            magneticUt = magneticUt,
        )
    }
}
