package com.russoturissto.towerradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.telephony.*

/** Logical cell identifiers do not confirm the physical mast location. */
data class ServingCell(
    val operator: String,
    val subId: Int,
    val network: String,
    val mcc: Int,
    val mnc: Int,
    val area: Int,
    val cellId: Long,
    val dbm: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Float?,
    val level: Int,
    val pci: Int? = null,
    val channel: Int? = null,
    val bands: List<Int> = emptyList(),
    val timingAdvance: Int? = null,
    val bandwidthKhz: Int? = null,
    val registered: Boolean = true
) {
    val identity: String get() = listOf(mcc, mnc, area, cellId, network).joinToString("/")
    val validId: Boolean get() = mcc in 1..999 && mnc in 0..999 &&
        area >= 0 && cellId > 0L
    val validDbm: Boolean get() = dbm != null && dbm in -150..-20
}

/** Location-independent modem signal (not a verified cell ID). */
data class RadioSignal(
    val operator: String,
    val network: String,
    val dbm: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Float?,
    val level: Int,
    val measuredAt: Long = SystemClock.elapsedRealtime()
)

/** Foreground radio observer. Signal works without GPS; CellInfo remains permission gated. */
class CellMonitor(
    private val activity: Activity,
    private val listener: (ServingCell?) -> Unit,
    private val signalListener: (RadioSignal) -> Unit = {},
    private val neighborsListener: (List<ServingCell>) -> Unit = {}
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var active = false
    private var subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var manager: TelephonyManager? = null
    private var subscribedToCells = false
    private var lastCellHadData = false

    private val phoneListener = object : PhoneStateListener() {
        @Deprecated("Deprecated by Android")
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) { dispatch(cellInfo) }
        @Deprecated("Deprecated by Android")
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            signalStrength?.let(::dispatchSignal)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            refresh()
            mainHandler.postDelayed(this, 9000L)
        }
    }

    fun hasSignalPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED

    fun canReadCellDetails(): Boolean {
        if (!hasSignalPermission() ||
            activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) return false
        return try {
            val lm = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) { false }
    }

    /** Legacy name used by the UI: this means full cell identity access, not signal. */
    fun hasPermission(): Boolean = canReadCellDetails()

    @SuppressLint("MissingPermission")
    fun availableSubscriptions(): List<Pair<Int, String>> {
        if (!hasSignalPermission()) return emptyList()
        return try {
            val subs = activity.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subs.activeSubscriptionInfoList.orEmpty().map {
                it.subscriptionId to (it.displayName?.toString() ?: ("SIM " + (it.simSlotIndex + 1)))
            }
        } catch (_: Exception) { emptyList() }
    }

    @SuppressLint("MissingPermission")
    fun start(preferredSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        stop()
        if (!hasSignalPermission() ||
            !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            listener(null)
            neighborsListener(emptyList())
            return
        }
        val base = activity.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val available = availableSubscriptions()
        val defaultSub = SubscriptionManager.getDefaultDataSubscriptionId()
        subscriptionId = when {
            available.any { it.first == preferredSubId } -> preferredSubId
            available.any { it.first == defaultSub } -> defaultSub
            available.isNotEmpty() -> available.first().first
            else -> SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        manager = if (subscriptionId >= 0) base.createForSubscriptionId(subscriptionId) else base
        active = true
        refresh()
        mainHandler.postDelayed(poll, 9000L)
    }

    fun stop() {
        active = false
        mainHandler.removeCallbacks(poll)
        try {
            @Suppress("DEPRECATION")
            manager?.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
        } catch (_: Exception) {}
        manager = null
        subscribedToCells = false
        lastCellHadData = false
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!active || !hasSignalPermission()) return
        val tm = manager ?: return
        val readCells = canReadCellDetails()
        if (readCells != subscribedToCells) {
            subscribedToCells = readCells
            try {
                @Suppress("DEPRECATION")
                tm.listen(phoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    (if (readCells) PhoneStateListener.LISTEN_CELL_INFO else 0))
            } catch (_: Exception) { /* some modems block CellInfo */ }
            if (!readCells) {
                lastCellHadData = false
                listener(null)
                neighborsListener(emptyList())
            }
        } else {
            // Re-register at least once for signal even if location never enabled.
            try {
                @Suppress("DEPRECATION")
                tm.listen(phoneListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                    (if (readCells) PhoneStateListener.LISTEN_CELL_INFO else 0))
            } catch (_: Exception) {}
        }
        try {
            if (Build.VERSION.SDK_INT >= 28) tm.signalStrength?.let(::dispatchSignal)
        } catch (_: Exception) { /* preserve last modem reading */ }
        if (!readCells) return
        try {
            dispatch(tm.allCellInfo)
            if (Build.VERSION.SDK_INT >= 29) {
                tm.requestCellInfoUpdate(activity.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cells: MutableList<CellInfo>) {
                        if (active && canReadCellDetails()) dispatch(cells)
                    }
                    override fun onError(errorCode: Int, detail: Throwable?) {
                        // Android rate limits fresh readings; don't erase valid measurements.
                    }
                })
            }
        } catch (_: Exception) { /* no cell identity access; signal remains available */ }
    }

    private fun dispatchSignal(signal: SignalStrength) {
        if (!active) return
        val tm = manager ?: return
        val selected = if (Build.VERSION.SDK_INT >= 29) signal.cellSignalStrengths
            .filter { it.dbm in -150..-20 }
            .sortedBy { when (it) {
                is CellSignalStrengthNr -> 0
                is CellSignalStrengthLte -> 1
                is CellSignalStrengthWcdma -> 2
                is CellSignalStrengthGsm -> 3
                else -> 4
            } }.firstOrNull()
        else null
        val type = when (selected) {
            is CellSignalStrengthNr -> "5G NR"
            is CellSignalStrengthLte -> "4G LTE"
            is CellSignalStrengthWcdma -> "3G UMTS"
            is CellSignalStrengthGsm -> "2G GSM"
            else -> try {
                @Suppress("DEPRECATION")
                when (tm.dataNetworkType) {
                    TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                    TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                    TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSPA,
                    TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
                    TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                    else -> "Мобильная сеть"
                }
            } catch (_: Exception) { "Мобильная сеть" }
        }
        val rsrp = when (selected) {
            is CellSignalStrengthLte -> validDbm(selected.rsrp)
            is CellSignalStrengthNr -> validDbm(selected.ssRsrp)
            else -> null
        }
        val rsrq = when (selected) {
            is CellSignalStrengthLte -> selected.rsrq.takeIf { it in -40..0 }
            is CellSignalStrengthNr -> selected.ssRsrq.takeIf { it in -40..0 }
            else -> null
        }
        val sinr = when (selected) {
            is CellSignalStrengthLte -> selected.rssnr.takeIf { it in -200..300 }?.div(10f)
            is CellSignalStrengthNr -> selected.ssSinr.takeIf { it in -40..50 }?.toFloat()
            else -> null
        }
        val dbm = validDbm(selected?.dbm ?: Int.MAX_VALUE)
        val reading = RadioSignal(
            operator = try { tm.networkOperatorName.orEmpty() } catch (_: Exception) { "" },
            network = type, dbm = dbm, rsrp = rsrp, rsrq = rsrq, sinr = sinr,
            level = (selected?.level ?: signal.level).coerceIn(0, 4)
        )
        if (Looper.myLooper() == Looper.getMainLooper()) signalListener(reading)
        else mainHandler.post { if (active) signalListener(reading) }
    }

    private fun dispatch(cells: List<CellInfo>?) {
        if (!active || !canReadCellDetails()) return
        if (cells.isNullOrEmpty()) return // modem throttling must not erase a useful reading
        val operator = manager?.networkOperatorName.orEmpty()
        val available = cells.mapNotNull { parse(it, operator, subscriptionId) }
        val serving = available.filter { it.registered }
            .sortedBy { if (it.network == "NR") 0 else 1 }.firstOrNull()
        val others = available.filter { !it.registered }.distinctBy { it.identity }.take(35)
        if (serving != null) lastCellHadData = true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (serving != null) listener(serving)
            neighborsListener(others)
        } else mainHandler.post {
            if (active && canReadCellDetails()) {
                if (serving != null) listener(serving)
                neighborsListener(others)
            }
        }
    }

    companion object {
        private fun validDbm(value: Int): Int? = value.takeIf { it in -150..-20 }
        private fun rsrq(value: Int): Int? = value.takeIf { it in -40..0 }
        private fun value(v: Int): Int? = v.takeIf { it >= 0 && it < Int.MAX_VALUE }
        private fun bands(v: IntArray): List<Int> = v.filter { it > 0 && it < 200 }.distinct()

        fun parse(cell: CellInfo, operator: String, subId: Int): ServingCell? {
            val registered = cell.isRegistered
            return when (cell) {
                is CellInfoLte -> {
                    val id = cell.cellIdentity
                    val strength = cell.cellSignalStrength
                    ServingCell(operator, subId, "LTE",
                        if (Build.VERSION.SDK_INT >= 28) id.mccString?.toIntOrNull() ?: -1 else id.mcc,
                        if (Build.VERSION.SDK_INT >= 28) id.mncString?.toIntOrNull() ?: -1 else id.mnc,
                        id.tac, id.ci.toLong(), validDbm(strength.dbm),
                        validDbm(strength.rsrp), rsrq(strength.rsrq),
                        strength.rssnr.takeIf { it in -200..300 }?.div(10f),
                        strength.level.coerceIn(0, 4), pci = value(id.pci),
                        channel = value(id.earfcn),
                        bands = if (Build.VERSION.SDK_INT >= 30) bands(id.bands) else emptyList(),
                        timingAdvance = value(strength.timingAdvance),
                        bandwidthKhz = if (Build.VERSION.SDK_INT >= 28) value(id.bandwidth) else null,
                        registered = registered)
                }
                is CellInfoGsm -> {
                    val id = cell.cellIdentity; val strength = cell.cellSignalStrength
                    ServingCell(operator, subId, "GSM",
                        if (Build.VERSION.SDK_INT >= 28) id.mccString?.toIntOrNull() ?: -1 else id.mcc,
                        if (Build.VERSION.SDK_INT >= 28) id.mncString?.toIntOrNull() ?: -1 else id.mnc,
                        id.lac, id.cid.toLong(), validDbm(strength.dbm),
                        null, null, null, strength.level.coerceIn(0, 4),
                        pci = if (Build.VERSION.SDK_INT >= 24) value(id.bsic) else null,
                        channel = if (Build.VERSION.SDK_INT >= 24) value(id.arfcn) else null,
                        registered = registered)
                }
                is CellInfoWcdma -> {
                    val id = cell.cellIdentity; val strength = cell.cellSignalStrength
                    ServingCell(operator, subId, "UMTS",
                        if (Build.VERSION.SDK_INT >= 28) id.mccString?.toIntOrNull() ?: -1 else id.mcc,
                        if (Build.VERSION.SDK_INT >= 28) id.mncString?.toIntOrNull() ?: -1 else id.mnc,
                        id.lac, id.cid.toLong(), validDbm(strength.dbm),
                        null, null, null, strength.level.coerceIn(0, 4),
                        pci = if (Build.VERSION.SDK_INT >= 24) value(id.psc) else null,
                        channel = if (Build.VERSION.SDK_INT >= 24) value(id.uarfcn) else null,
                        registered = registered)
                }
                else -> if (Build.VERSION.SDK_INT >= 29) parseNr(cell, operator, subId) else null
            }
        }

        @android.annotation.TargetApi(29)
        private fun parseNr(cell: CellInfo, operator: String, subId: Int): ServingCell? {
            val nr = cell as? CellInfoNr ?: return null
            val id = nr.cellIdentity as? CellIdentityNr ?: return null
            val strength = nr.cellSignalStrength as? CellSignalStrengthNr ?: return null
            return ServingCell(operator, subId, "NR", id.mccString?.toIntOrNull() ?: -1,
                id.mncString?.toIntOrNull() ?: -1, id.tac, id.nci,
                validDbm(strength.dbm), validDbm(strength.ssRsrp), rsrq(strength.ssRsrq),
                strength.ssSinr.takeIf { it in -40..50 }?.toFloat(),
                strength.level.coerceIn(0, 4), pci = value(id.pci),
                channel = value(id.nrarfcn),
                bands = if (Build.VERSION.SDK_INT >= 30) bands(id.bands) else emptyList(),
                registered = nr.isRegistered)
        }
    }
}
