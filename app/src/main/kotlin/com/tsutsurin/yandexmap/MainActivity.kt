package com.tsutsurin.yandexmap

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.animation.ValueAnimator
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.location.LocationManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor


class MainActivity : AppCompatActivity() {

    companion object {
        private const val ZOOM = 16f
        private const val ANIMATION_DURATION: Long = 200
        private const val requestPermissionLocation = 1
    }

    private var mapView: MapView? = null
    private var userLocationLayer: UserLocationLayer? = null

    private val viewModel: MainActivityViewModel by viewModels {
        MainActivityViewModel.provideFactory(
            GeocoderWrapper(this.applicationContext),
            LocationManagerWrapper(this.applicationContext)
        )
    }

    private var bottomSheetBehavior: BottomSheetBehavior<*>? = null

    private var textView: TextView? = null

    private val icArrow by lazy {
        ImageProvider.fromResource(this, R.drawable.ic_arrow)
    }

    private val cameraListener = CameraListener { _, cameraPosition, _, isFinished ->
        viewModel.onCameraPositionChanged(cameraPosition, isFinished)
    }

    private val objectListener = object : UserLocationObjectListener {
        override fun onObjectAdded(userLocationView: UserLocationView) {
            userLocationLayer?.apply {
                mapView?.let {
                    setAnchor(
                        PointF((it.width * 0.5).toFloat(), (it.height * 0.5).toFloat()),
                        PointF((it.width * 0.5).toFloat(), (it.height * 0.83).toFloat())
                    )
                }
            }

            userLocationView.arrow.setIcon(icArrow)

            userLocationView.pin.setIcon(icArrow)
        }

        override fun onObjectRemoved(p0: UserLocationView) {

        }

        override fun onObjectUpdated(p0: UserLocationView, p1: ObjectEvent) {

        }
    }

    private val upValueAnimator by lazy {
        val drawable = ContextCompat.getDrawable(
            this@MainActivity,
            R.drawable.ic_pin_no_active
        )

        animatePin(0, 100, drawable)
    }

    private val downValueAnimator by lazy {
        val drawable = ContextCompat.getDrawable(
            this@MainActivity,
            R.drawable.ic_pin_active
        )

        animatePin(100, 0, drawable)
    }

    private fun animatePin(
        startBottomMargin: Int,
        targetBottomMargin: Int,
        pinDrawable: Drawable?
    ) = ValueAnimator.ofInt(
        startBottomMargin,
        targetBottomMargin
    ).apply {
        duration = ANIMATION_DURATION
        addUpdateListener { valueAnimator ->
            val view = findViewById<ImageView>(R.id.pin)
            view.setImageDrawable(pinDrawable)
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = valueAnimator.animatedValue as Int
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        MapKitFactory.initialize(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermission()
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
                    initView()
                }
            }
        }
    }

    private fun checkPermission() {
        val permissionLocation = checkSelfPermission(ACCESS_FINE_LOCATION)
        if (permissionLocation != PERMISSION_GRANTED) {
            requestPermissions(arrayOf(ACCESS_FINE_LOCATION), requestPermissionLocation)
        } else {
            initView()
        }
    }

    private fun initView() {
        val mapKit = MapKitFactory.getInstance()
        mapKit.resetLocationManagerToDefault()

        mapView = findViewById<MapView>(R.id.mapview)?.apply {
            mapWindow.map.apply {
                isRotateGesturesEnabled = false
                addCameraListener(cameraListener)
            }
            userLocationLayer = mapKit.createUserLocationLayer(mapWindow).apply {
                isVisible = true
                isHeadingEnabled = true
                setObjectListener(objectListener)
            }
        }

        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.standardBottomSheet))
        textView = findViewById(R.id.tvCurrentAddress)

        findViewById<FloatingActionButton>(R.id.floatingButton).setOnClickListener {
            cameraUserPosition()
        }

        launchFlow()
    }

    private fun launchFlow() {
        launchWhenStarted { viewModel.pinPosition.collect(::collectPinPosition) }
        launchWhenStarted { viewModel.address.collect(::collectAddress) }
        launchWhenStarted {
            viewModel.currentUserPosition.collect {
                val point = it ?: Point(0.0, 0.0)
                mapView?.mapWindow?.map?.apply {
                    move(CameraPosition(point, ZOOM, 0.0f, 0.0f))
                }
            }
        }
    }

    private fun collectPinPosition(pinPosition: PinPosition) {
        upValueAnimator.cancel()
        downValueAnimator.cancel()

        userLocationLayer?.resetAnchor()

        when (pinPosition) {
            PinPosition.UP -> {
                upValueAnimator.start()
                showBottomSheet(true)
            }

            PinPosition.DOWN -> {
                downValueAnimator.start()
            }
        }
    }

    private fun collectAddress(address: String) {
        textView?.text = address
        textView?.isVisible = address.isNotEmpty()
        showBottomSheet(address.isEmpty())
    }

    private fun cameraUserPosition() {
        userLocationLayer?.let { userLocationLayer ->
            val cameraPosition = userLocationLayer.cameraPosition()
            if (cameraPosition != null && mapView != null) {
                mapView?.mapWindow?.map?.move(
                    CameraPosition(cameraPosition.target, ZOOM, 0f, 0f),
                    Animation(Animation.Type.SMOOTH, 1f)
                ) {
                    viewModel.onCameraPositionChanged(cameraPosition, it)
                }
            }
        }
    }

    private fun showBottomSheet(needHide: Boolean) {
        bottomSheetBehavior?.state = if (needHide) {
            BottomSheetBehavior.STATE_HIDDEN
        } else {
            BottomSheetBehavior.STATE_COLLAPSED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        upValueAnimator.cancel()
        upValueAnimator.removeAllUpdateListeners()
        downValueAnimator.cancel()
        downValueAnimator.removeAllUpdateListeners()
        mapView?.mapWindow?.map?.removeCameraListener(cameraListener)
        userLocationLayer?.setObjectListener(null)
    }
}