package de.proofseal.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.util.Locale

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun resolveCurrentLocationString(context: Context): String {
        if (!hasLocationPermission(context)) {
            return "GPS nicht aktiv (Berechtigung nicht erteilt)"
        }

        return try {
            val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                    ?: return "GPS nicht aktiv"

            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )

            var bestLocation: Location? = null
            for (provider in providers) {
                val enabled = try {
                    locationManager.isProviderEnabled(provider)
                } catch (_: Exception) {
                    false
                }
                if (!enabled && provider != LocationManager.PASSIVE_PROVIDER) continue

                val candidate = try {
                    locationManager.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                } ?: continue

                if (bestLocation == null || candidate.accuracy < bestLocation.accuracy) {
                    bestLocation = candidate
                }
            }

            if (bestLocation != null) {
                val latDir = if (bestLocation.latitude >= 0) "N" else "S"
                val lonDir = if (bestLocation.longitude >= 0) "E" else "W"
                val acc = bestLocation.accuracy.toInt().coerceAtLeast(1)
                String.format(
                    Locale.US,
                    "%.5f° %s, %.5f° %s (±%dm)",
                    kotlin.math.abs(bestLocation.latitude),
                    latDir,
                    kotlin.math.abs(bestLocation.longitude),
                    lonDir,
                    acc
                )
            } else {
                "GPS nicht aktiv (Kein aktueller Standort-Fix)"
            }
        } catch (e: Exception) {
            "GPS nicht aktiv"
        }
    }
}
