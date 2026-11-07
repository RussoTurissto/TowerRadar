package com.russoturissto.towerradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.telephony.SubscriptionManager

/** A serving cell is an identified radio cell, not a verified mast location. */
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
    val level: Int
) {
    val identity: String get() = "$mcc/$mnc/$area/$cellId/$network"
    val validId: Boolean get() = mcc in 1..999 && mnc in 0..999 &&
        area >= 0 && cellId > 0L
    val validDbm: Boolean get() = dbm != null && dbm in -150..-20
}

/** Foreground-only Android radio observer, with SIM choice and Android throttling safeguards. */
class CellMonitor(private val activity: Activity, private val listener: (ServingCell?) -> Unit) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var active = false
    private var subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID
    private var manager: TelephonyManager? = null

    private val phoneListener = object : PhoneStateListener() {
        @Deprecated("Deprecated by Android")
        override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
            dispatch(cellInfo)
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            if (!active) return
            refresh()
            mainHandler.postDelayed(this, 9000L)
        }
    }

    fun hasPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun availableSubscriptions(): List<Pair<Int, String>> {
        if (!hasPermission()) return emptyList()
        return try {
            val subs = activity.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            subs.activeSubscriptionInfoList.orEmpty()
                .map { it.subscriptionId to (it.displayName?.toString() ?: ("SIM " + (it.simSlotIndex + 1))) }
        } catch (_: SecurityException) { emptyList() }
    }

    @SuppressLint("MissingPermission")
    fun start(preferredSubId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
        stop()
        if (!hasPermission() || !activity.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            listener(null)
            return
        }
        val base = activity.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val available = availableSubscriptions()
        val defaultSub = SubscriptionManager.getDefaultDataSubscriptionId()
        val chosen = when {
            available.any { it.first == preferredSubId } -> preferredSubId
            available.any { it.first == defaultSub } -> defaultSub
            available.isNotEmpty() -> available.first().first
            else -> SubscriptionManager.INVALID_SUBSCRIPTION_ID
        }
        subscriptionId = chosen
        manager = if (chosen >= 0) base.createForSubscriptionId(chosen) else base
        active = true
        try {
            @Suppress("DEPRECATION")
            manager?.listen(phoneListener, PhoneStateListener.LISTEN_CELL_INFO)
        } catch (_: Exception) { /* requestCellInfoUpdate remains available. */ }
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
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!active || !hasPermission()) return
        val tm = manager ?: return
        try {
            dispatch(tm.allCellInfo)
            if (Build.VERSION.SDK_INT >= 29) {
                tm.requestCellInfoUpdate(activity.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                    override fun onCellInfo(cells: MutableList<CellInfo>) {
                        if (active) dispatch(cells)
                    }
                    override fun onError(errorCode: Int, detail: Throwable?) {
                        // Android or modem may throttle; preserve the last valid reading in UI.
                    }
                })
            }
        } catch (_: Exception) { /* Do not erase a good reading for a transient modem error. */ }
    }

    private fun dispatch(cells: List<CellInfo>?) {
        if (!active) return
        val nr = if (Build.VERSION.SDK_INT >= 29)
            cells.orEmpty().firstOrNull { it.isRegistered && it is CellInfoNr } else null
        val selected = nr ?: cells.orEmpty().firstOrNull { it.isRegistered }
        val reading = selected?.let { parse(it, manager?.networkOperatorName.orEmpty(), subscriptionId) }
        if (Looper.myLooper() == Looper.getMainLooper()) listener(reading)
        else mainHandler.post { if (active) listener(reading) }
    }

    companion object {
        private fun dbm(value: Int): Int? = value.takeIf { it in -150..-20 }
        private fun rsrp(value: Int): Int? = value.takeIf { it in -150..-20 }
        private fun rsrq(value: Int): Int? = value.takeIf { it in -40..0 }

        fun parse(cell: CellInfo, operator: String, subId: Int): ServingCell? {
            if (!cell.isRegistered) return null
            return when (cell) {
                is CellInfoLte -> {
                    val id = cell.cellIdentity; val s = cell.cellSignalStrength
                    ServingCell(operator, subId, "LTE",
                        if (Build.VERSION.SDK_INT >= 28) id.mccString?.toIntOrNull() ?: -1 else id.mcc,
                        if (Build.VERSION.SDK_INT >= 28) id.mncString?.toIntOrNull() ?: -1 else id.mnc,
                        id.tac, id.ci.toLong(), dbm(s.dbm), rsrp(s.rsrp), rsrq(s.rsrq),
                        s.rssnr.takeIf { it in -200..300 }?.div(10f), s.level.coerceIn(0, 4))
                }
                is CellInfoGsm -> {
                    val id = cell.cellIdentity; val s = cell.cellSignalStrength
                    ServingCell(operator, subId, "GSM",
                        if (Build.VERSION.SDK_INT >= 28) id.mccString?.toIntOrNull() ?: -1 else id.mcc,
                        if (Build.VERSION.SDK_INT >= 28) id.mncString?.toIntOrNull() ?: -1 else id.mnc,
                        id.lac, id.cid.toLong(), dbm(s.dbm), null, null, null,
                        s.level.coerceIn(0, 4))
                }
                is CellInfoWcdma -> {
                    val id = cell.cellIdentity; val s = cell.cellSignalStrength
                    ServingCell(operator, subId, "UMTS",
                        if (Build.VERSION.SDK_INT >= 28) id.mccString?.toIntOrNull() ?: -1 else id.mcc,
                        if (Build.VERSION.SDK_INT >= 28) id.mncString?.toIntOrNull() ?: -1 else id.mnc,
                        id.lac, id.cid.toLong(), dbm(s.dbm), null, null, null,
                        s.level.coerceIn(0, 4))
                }
                else -> if (Build.VERSION.SDK_INT >= 29) parseNr(cell, operator, subId) else null
            }
        }

        @android.annotation.TargetApi(29)
        private fun parseNr(cell: CellInfo, operator: String, subId: Int): ServingCell? {
            val nr = cell as? CellInfoNr ?: return null
                    val id = nr.cellIdentity as? CellIdentityNr ?: return null
                    val s = nr.cellSignalStrength as? CellSignalStrengthNr ?: return null
                    ServingCell(operator, subId, "NR", id.mccString?.toIntOrNull() ?: -1,
                        id.mncString?.toIntOrNull() ?: -1, id.tac, id.nci, dbm(s.dbm),
                        rsrp(s.ssRsrp), rsrq(s.ssRsrq),
                        s.ssSinr.takeIf { it in -40..50 }?.toFloat(), s.level.coerceIn(0, 4))
        }
    }
}