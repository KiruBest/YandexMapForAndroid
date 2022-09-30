package com.tsutsurin.yandexmap

import android.location.Address
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tsutsurin.yandexmap.MainActivity.Companion.PIN_POSITION_DOWN
import com.tsutsurin.yandexmap.MainActivity.Companion.PIN_POSITION_UP
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivityViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PIN_POSITION_DOWN)
    val uiState = _uiState.asStateFlow()

    private val _address: MutableStateFlow<Address?> = MutableStateFlow(null)
    val address = _address.asStateFlow()

    private var lastCameraPosition: CameraPosition? = null

    fun execute(
        cameraPosition: CameraPosition,
        cameraUpdateReason: CameraUpdateReason,
        isFinished: Boolean,
        pinPosition: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isActive && !isFinished && pinPosition == PIN_POSITION_UP) {
                return@launch
            } else if (isActive) {
                cancel()
            }
            Log.i("TAG", cameraUpdateReason.name)
            if (isFinished) {
                lastCameraPosition = cameraPosition
                _uiState.emit(PIN_POSITION_DOWN)
            } else {
                if (pinPosition == PIN_POSITION_DOWN) {
                    _uiState.emit(PIN_POSITION_UP)
                }
            }
        }
    }

    fun getAddress(geocoder: Geocoder) {
        viewModelScope.launch(Dispatchers.IO) {
            lastCameraPosition?.let { lastCameraPosition ->
                val address = geocoder.getFromLocation(
                    lastCameraPosition.target.latitude,
                    lastCameraPosition.target.longitude,
                    1
                )

                if (address.isNotEmpty()) {
                    _address.emit(address[0])
                }
            }
        }
    }
}