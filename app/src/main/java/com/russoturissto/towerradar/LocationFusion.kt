package com.russoturissto.towerradar

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

/**
 * Optional GNSS + Wi-Fi + cell fusion supplied by Google Play services.
 * MainActivity also registers Android GPS/NETWORK as a no-GMS fallback.
 * No background tracking.
 */
class LocationFusion(private val activity: Activity, private val onFix: (Location) -> Unit) {
    private var active = false
    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (active) for (fix in result.locations) onFix(fix)
        }
    }
    private val client by lazy { LocationServices.getFusedLocationProviderClient(activity) }

    @SuppressLint("MissingPermission")
    fun start() {
        if (active || !allowed()) return
        try {
            active = true
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                .setMinUpdateIntervalMillis(1000L)
                .setMinUpdateDistanceMeters(0f)
                .setMaxUpdateAgeMillis(15000L)
                .setWaitForAccurateLocation(true).build()
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnFailureListener { active = false }
            refresh()
        } catch (_: Exception) { active = false }
    }

    @SuppressLint("MissingPermission")
    fun refresh() {
        if (!allowed()) return
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                .addOnSuccessListener { fix -> if (fix != null) onFix(fix) }
                .addOnFailureListener { /* Android GPS fallback stays active */ }
        } catch (_: Exception) {}
    }

    fun stop() {
        if (!active) return
        active = false
        try { client.removeLocationUpdates(callback) } catch (_: Exception) {}
    }

    private fun allowed(): Boolean =
        activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
