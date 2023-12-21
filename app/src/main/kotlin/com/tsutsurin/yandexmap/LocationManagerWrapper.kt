package com.tsutsurin.yandexmap

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.yandex.mapkit.geometry.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class LocationManagerWrapper(context: Context) {
    private val locationManager = ContextCompat.getSystemService(
        context,
        LocationManager::class.java
    )

    @SuppressLint("MissingPermission")
    suspend fun getCurrentUserPosition(): Point {
        return suspendCancellableCoroutine { cont ->
            if (locationManager == null) {
                cont.resume(Point(0.0, 0.0))
            } else {
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    LocationManager.GPS_PROVIDER,
                    CancellationSignal(),
                    Dispatchers.IO.asExecutor()
                ) { location ->
                    location?.let {
                        cont.resume(Point(it.latitude, it.longitude))
                    } ?: cont.resume(Point(0.0, 0.0))
                }
            }
        }
    }
}