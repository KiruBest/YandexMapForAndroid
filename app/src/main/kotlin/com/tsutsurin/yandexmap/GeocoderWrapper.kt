package com.tsutsurin.yandexmap

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Geocoder.GeocodeListener
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class GeocoderWrapper(context: Context) {
    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun getFromLocation(
        latitude: Double,
        longitude: Double,
        maxResults: Int = 1
    ): List<Address> {
        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitude, longitude, maxResults, object : GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        cont.resume(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        Log.e(this::class.java.name, errorMessage.orEmpty())
                        cont.resume(emptyList())
                    }
                })
            } else {
                try {
                    val addresses = geocoder.getFromLocation(latitude, longitude, maxResults)
                    cont.resume(addresses ?: emptyList())
                } catch (e: IOException) {
                    cont.resume(emptyList())
                }
            }
        }
    }
}