package com.tsutsurin.yandexmap

import android.location.Address
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivityViewModel(
    private val geocoder: GeocoderWrapper,
    private val locationManager: LocationManagerWrapper
) : ViewModel() {
    companion object {
        fun provideFactory(
            geocoder: GeocoderWrapper,
            locationManager: LocationManagerWrapper
        ) = viewModelFactory {
            initializer {
                MainActivityViewModel(geocoder, locationManager)
            }
        }
    }

    private val _pinPosition: MutableStateFlow<PinPosition> = MutableStateFlow(PinPosition.UP)
    val pinPosition = _pinPosition.asStateFlow()

    private val _address: MutableStateFlow<String> = MutableStateFlow("")
    val address = _address.asStateFlow()

    private val _currentUserPosition: MutableStateFlow<Point?> = MutableStateFlow(null)
    val currentUserPosition = _currentUserPosition.asStateFlow()

    private var job: Job? = null
    private val mutex = Mutex()

    init {
        getCurrentUserPoint()
    }

    fun onCameraPositionChanged(
        cameraPosition: CameraPosition,
        isFinished: Boolean
    ) {
        job?.cancel()
        job = viewModelScope.launch(SupervisorJob() + Dispatchers.Main) {
            if (isFinished) {
                changePinPosition(PinPosition.DOWN)
                produceAddress(cameraPosition)
            } else {
                changePinPosition(PinPosition.UP)
            }
        }
    }

    private suspend fun produceAddress(cameraPosition: CameraPosition) =
        withContext(Dispatchers.IO) {
            _address.value = cameraPosition.target.run {
                geocoder.getFromLocation(
                    latitude = latitude,
                    longitude = longitude
                ).firstOrNull()?.let(::getAddressTitle).orEmpty()
            }
        }

    private suspend fun changePinPosition(newValue: PinPosition) = mutex.withLock {
        val oldValue = pinPosition.value
        if (oldValue != newValue) _pinPosition.value = newValue
    }

    private fun getAddressTitle(address: Address): String =
        "${address.countryName}, ${address.subAdminArea}, " +
                "${address.thoroughfare}, ${address.featureName}"

    private fun getCurrentUserPoint() {
        viewModelScope.launch {
            _currentUserPosition.value = locationManager.getCurrentUserPosition()
        }
    }
}
