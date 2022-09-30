package com.tsutsurin.yandexmap

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.animation.ValueAnimator
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import java.util.*


class MainActivity : AppCompatActivity(), UserLocationObjectListener, CameraListener {

    companion object {
        private const val ZOOM = 16f
        const val PIN_POSITION_UP = 0
        const val PIN_POSITION_DOWN = 1
        private const val requestPermissionLocation = 1
    }

    private var mapView: MapView? = null
    private var userLocationLayer: UserLocationLayer? = null

    private var routeStartLocation = Point(0.0, 0.0)

    private var geocoder: Geocoder? = null

    private var defaultPinPadding: Int = 0

    private var pinPosition = PIN_POSITION_DOWN

    private var bottomSheetBehavior: BottomSheetBehavior<*>? = null

    private lateinit var viewModel: MainActivityViewModel

    private var pinActive: Drawable? = null

    private var pinNoActive: Drawable? = null

    private var currentAddress = ""

    private var textView: TextView? = null

    private val upValueAnimator = ValueAnimator.ofInt(
        defaultPinPadding,
        defaultPinPadding + 100
    ).apply {
        duration = 200
        addUpdateListener { valueAnimator ->
            val view = findViewById<ImageView>(R.id.pin)
            view.setImageDrawable(pinNoActive)
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = valueAnimator.animatedValue.toString().toInt()
            }
        }
    }

    private val downValueAnimator = ValueAnimator.ofInt(
        defaultPinPadding + 100,
        defaultPinPadding
    ).apply {
        duration = 200
        addUpdateListener { valueAnimator ->
            val view = findViewById<ImageView>(R.id.pin)
            view.setImageDrawable(pinActive)
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = valueAnimator.animatedValue.toString().toInt()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MapKitFactory.initialize(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission()
        viewModel = ViewModelProvider(this)[MainActivityViewModel::class.java]
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView?.onStart()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onStop()
        MapKitFactory.getInstance().onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            requestPermissionLocation -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                    initMapView()
                }
            }
        }
    }

    override fun onObjectAdded(userLocationView: UserLocationView) {
        userLocationLayer?.apply {
            mapView?.let {
                setAnchor(
                    PointF((it.width * 0.5).toFloat(), (it.height * 0.5).toFloat()),
                    PointF((it.width * 0.5).toFloat(), (it.height * 0.83).toFloat())
                )
            }
        }

        userLocationView.arrow.setIcon(
            ImageProvider.fromResource(this, R.drawable.ic_arrow)
        )

        userLocationView.pin.setIcon(
            ImageProvider.fromResource(this, R.drawable.ic_arrow)
        )

        cameraUserPosition()
    }

    override fun onObjectRemoved(userLocationView: UserLocationView) {}

    override fun onObjectUpdated(userLocationView: UserLocationView, objectEvent: ObjectEvent) {}

    override fun onCameraPositionChanged(
        map: Map,
        cameraPosition: CameraPosition,
        cameraUpdateReason: CameraUpdateReason,
        isFinished: Boolean
    ) {
        viewModel.execute(
            cameraPosition,
            cameraUpdateReason,
            isFinished,
            pinPosition
        )
    }

    private fun checkPermission() {
        val permissionLocation = checkSelfPermission(ACCESS_FINE_LOCATION)
        if (permissionLocation != PERMISSION_GRANTED) {
            requestPermissions(arrayOf(ACCESS_FINE_LOCATION), requestPermissionLocation)
        } else {
            initMapView()
        }
    }

    private fun initMapView() {
        val mapKit = MapKitFactory.getInstance()
        pinActive = ContextCompat.getDrawable(
            this@MainActivity,
            R.drawable.ic_pin_active
        )
        pinNoActive = ContextCompat.getDrawable(
            this@MainActivity,
            R.drawable.ic_pin_no_active
        )
        mapView = findViewById(R.id.mapview)
        geocoder = Geocoder(this, Locale("ru", "RU"))
        bottomSheetBehavior =
            BottomSheetBehavior.from(findViewById(R.id.standardBottomSheet)).apply {
                state = BottomSheetBehavior.STATE_HIDDEN
            }
        textView = findViewById(R.id.textView)
        mapView?.let { mapView ->
            mapView.map?.apply {
                isRotateGesturesEnabled = false
                addCameraListener(this@MainActivity)
                move(CameraPosition(routeStartLocation, ZOOM, 0.0f, 0.0f))
            }

            mapKit.resetLocationManagerToDefault()
            userLocationLayer = mapView.mapWindow?.let { mapKit.createUserLocationLayer(it) }
            userLocationLayer?.apply {
                isVisible = true
                isHeadingEnabled = true
                setObjectListener(this@MainActivity)
            }
        }

        findViewById<FloatingActionButton>(R.id.floatingButton).setOnClickListener {
            cameraUserPosition()
        }

        lifecycleScope.launchWhenCreated {
            viewModel.uiState.collect { state ->
                userLocationLayer?.resetAnchor()
                when (state) {
                    PIN_POSITION_DOWN -> {
                        downValueAnimator.start()
                        pinPosition = PIN_POSITION_DOWN
                        geocoder?.let { viewModel.getAddress(it) }
                    }
                    PIN_POSITION_UP -> {
                        upValueAnimator.start()
                        pinPosition = PIN_POSITION_UP
                        bottomSheetBehavior?.apply {
                            this.state = BottomSheetBehavior.STATE_HIDDEN
                        }
                        textView?.isVisible = false
                    }
                }
            }
        }

        lifecycleScope.launchWhenCreated {
            viewModel.address.collect {
                it?.let { address->
                    if (pinPosition != PIN_POSITION_UP) {
                        textView?.text =
                            "${address.countryName}, ${address.subAdminArea}, ${address.thoroughfare}, ${address.featureName}"
                        textView?.isVisible = true
                        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        }
    }

    private fun cameraUserPosition() {
        userLocationLayer?.let { userLocationLayer ->
            if (userLocationLayer.cameraPosition() != null && mapView != null) {
                routeStartLocation = userLocationLayer.cameraPosition()!!.target
                mapView!!.map.move(
                    CameraPosition(routeStartLocation, ZOOM, 0f, 0f),
                    Animation(Animation.Type.SMOOTH, 1f),
                    null
                )
            }
        }
    }
}