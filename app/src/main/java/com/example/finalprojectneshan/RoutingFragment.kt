package com.example.finalprojectneshan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Toast
import android.widget.ToggleButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.carto.graphics.Color
import com.carto.styles.AnimationStyle
import com.carto.styles.AnimationStyleBuilder
import com.carto.styles.AnimationType
import com.carto.styles.LineStyle
import com.carto.styles.LineStyleBuilder
import com.carto.styles.MarkerStyleBuilder
import com.carto.utils.BitmapUtils
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import org.neshan.common.model.LatLng
import org.neshan.common.utils.PolylineEncoding
import org.neshan.mapsdk.MapView
import org.neshan.mapsdk.model.Marker
import org.neshan.mapsdk.model.Polyline
import org.neshan.servicessdk.direction.NeshanDirection
import org.neshan.servicessdk.direction.model.NeshanDirectionResult
import org.neshan.servicessdk.direction.model.Route
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DateFormat
import java.util.*

class RoutingFragment : Fragment(R.layout.fragment_routing) {

    private lateinit var map: MapView

    // define two toggle button and connecting together for two type of routing
    private lateinit var overviewToggleButton: ToggleButton
    private lateinit var stepByStepToggleButton: ToggleButton

    // we save decoded Response of routing encoded string because we don't want request every time we clicked toggle buttons
    private var routeOverviewPolylinePoints: ArrayList<LatLng>? = null
    private var decodedStepByStepPath: ArrayList<LatLng>? = null

    // value for difference mapSetZoom
    private var overview = false

    // Marker that will be added on map
    private var marker: Marker? = null
    private lateinit var mark: Marker

    // List of created markers
    private val markers: ArrayList<Marker> = ArrayList()

    // marker animation style
    private var animSt: AnimationStyle? = null

    // drawn path of route
    private var onMapPolyline: Polyline? = null

    //////////////
// User's current location
    private var userLocation: Location? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var settingsClient: SettingsClient
    private lateinit var locationRequest: LocationRequest
    private var locationSettingsRequest: LocationSettingsRequest? = null
    private var locationCallback: LocationCallback? = null
    private var lastUpdateTime: String? = null

    // boolean flag to toggle the ui
    private var mRequestingLocationUpdates: Boolean? = null

    //    private var marker: Marker? = null
    lateinit var textInput: EditText

    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000

    // fastest updates interval - 1 sec
    // location updates will be received if another app is requesting the locations
    // than your app can handle
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000

    // used to track request permissions
    private val REQUEST_CODE = 123

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_routing, container, false)
    }


    override fun onStart() {
        super.onStart()
        // everything related to ui is initialized here
        initLayoutReferences()
        // Initializing user location
        initLocation()
        startReceivingLocationUpdates()

    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE) {
            if (ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mRequestingLocationUpdates = true
                startLocationUpdates()
            }
        }
    }

    // Initializing layout references (views, map and map events)
    private fun initLayoutReferences() {
        // Initializing views
        initViews()
        // Initializing mapView element
        initMap()

        // when long clicked on map, a marker is added in clicked location
        map.setOnMapLongClickListener {
            if (markers.size < 2) {
                markers.add(addMarker(it));
                if (markers.size == 2) {
                    activity?.runOnUiThread {
                        overviewToggleButton.isChecked = true
                        neshanRoutingApi();
                    }
                }
            } else {
                activity?.runOnUiThread {
                    Toast.makeText(
                        requireContext(),
                        "مسیریابی بین دو نقطه انجام میشود!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    }

    // We use findViewByID for every element in our layout file here
    private fun initViews() {
        map = view?.findViewById(R.id.mapview)!!

        // CheckChangeListener for Toggle buttons
        val changeChecker =
            CompoundButton.OnCheckedChangeListener { toggleButton, isChecked -> // if any toggle button checked:
                if (isChecked) {
                    // if overview toggle button checked other toggle button is uncheck
                    if (toggleButton === overviewToggleButton) {
                        stepByStepToggleButton.isChecked = false
                        overview = true
                    }
                    if (toggleButton === stepByStepToggleButton) {
                        overviewToggleButton.isChecked = false
                        overview = false
                    }
                }
                if (!isChecked && onMapPolyline != null) {
                    map.removePolyline(onMapPolyline)
                }
            }

        // each toggle button has a checkChangeListener for uncheck other toggle button
        overviewToggleButton = view?.findViewById(R.id.overviewToggleButton)!!
        overviewToggleButton.setOnCheckedChangeListener(changeChecker)

        stepByStepToggleButton = view?.findViewById(R.id.stepByStepToggleButton)!!
        stepByStepToggleButton.setOnCheckedChangeListener(changeChecker)
    }

    // Initializing map
    private fun initMap() {
        // Setting map focal position to a fixed position and setting camera zoom
        map.moveCamera(LatLng(35.767234, 51.330743), 0f)
        map.setZoom(14f, 0f)
    }

    // call this function with clicking on toggle buttons and draw routing line depend on type of routing requested
    fun findRoute(view: View?) {
        if (markers.size < 2) {
            Toast.makeText(
                requireContext(),
                "برای مسیریابی باید دو نقطه انتخاب شود",
                Toast.LENGTH_SHORT
            ).show()
            overviewToggleButton.isChecked = false
            stepByStepToggleButton.isChecked = false
        } else if (overviewToggleButton.isChecked) {
            try {
                map.removePolyline(onMapPolyline)
                onMapPolyline = Polyline(routeOverviewPolylinePoints, getLineStyle())
                //draw polyline between route points
                map.addPolyline(onMapPolyline)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (stepByStepToggleButton.isChecked) {
            try {
                map.removePolyline(onMapPolyline)
                onMapPolyline = Polyline(decodedStepByStepPath, getLineStyle())
                //draw polyline between route points
                map.addPolyline(onMapPolyline)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun addMarker(loc: LatLng): Marker {
        // Creating animation for marker. We should use an object of type AnimationStyleBuilder, set
        // all animation features on it and then call buildStyle() method that returns an object of type
        // AnimationStyle
        val animStBl = AnimationStyleBuilder()
        animStBl.fadeAnimationType = AnimationType.ANIMATION_TYPE_SMOOTHSTEP
        animStBl.sizeAnimationType = AnimationType.ANIMATION_TYPE_SPRING
        animStBl.phaseInDuration = 0.5f
        animStBl.phaseOutDuration = 0.5f
        animSt = animStBl.buildStyle()

        // Creating marker style. We should use an object of type MarkerStyleBuilder, set all features on it
        // and then call buildStyle method on it. This method returns an object of type MarkerStyle
        val markStCr = MarkerStyleBuilder()
        markStCr.size = 30f
        markStCr.bitmap = BitmapUtils.createBitmapFromAndroidBitmap(
            BitmapFactory.decodeResource(
                resources, R.drawable.ic_marker
            )
        )
        // AnimationStyle object - that was created before - is used here
        markStCr.animationStyle = animSt
        val markSt = markStCr.buildStyle()

        // Creating marker
        mark = Marker(loc, markSt)

        // Adding marker to markerLayer, or showing marker on map!
        map.addMarker(mark)
        return mark
    }

    // request routing method from Neshan Server
    private fun neshanRoutingApi() {
        NeshanDirection.Builder(
            "service.VNlPhrWb3wYRzEYmstQh3GrAXyhyaN55AqUSRR3V",
            markers[0].latLng,
            markers[1].latLng
        )
            .build().call(object : Callback<NeshanDirectionResult?> {
                override fun onResponse(
                    call: Call<NeshanDirectionResult?>,
                    response: Response<NeshanDirectionResult?>
                ) {

                    // two type of routing
                    if (response.body() != null && response.body()!!.routes != null && !response.body()!!.routes.isEmpty()
                    ) {
                        val route: Route = response.body()!!.routes[0]
                        routeOverviewPolylinePoints = java.util.ArrayList(
                            PolylineEncoding.decode(
                                route.overviewPolyline.encodedPolyline
                            )
                        )
                        decodedStepByStepPath = java.util.ArrayList()

                        // decoding each segment of steps and putting to an array
                        for (step in route.legs[0].directionSteps) {
                            decodedStepByStepPath!!.addAll(PolylineEncoding.decode(step.encodedPolyline))
                        }
                        onMapPolyline = Polyline(routeOverviewPolylinePoints, getLineStyle())
                        //draw polyline between route points
                        map.addPolyline(onMapPolyline)
                        // focusing camera on first point of drawn line
                        mapSetPosition(overview)
                    } else {
                        Toast.makeText(requireContext(), "مسیری یافت نشد", Toast.LENGTH_LONG)
                            .show()
                    }
                }

                override fun onFailure(call: Call<NeshanDirectionResult?>, t: Throwable) {}
            })
    }

    // In this method we create a LineStyleCreator, set its features and call buildStyle() method
    // on it and return the LineStyle object (the same routine as crating a marker style)
    private fun getLineStyle(): LineStyle {
        val lineStCr = LineStyleBuilder()
        lineStCr.color = Color(
            2.toShort(), 119.toShort(), 189.toShort(),
            190.toShort()
        )
        lineStCr.width = 10f
        lineStCr.stretchFactor = 0f
        return lineStCr.buildStyle()
    }

    // for overview routing we zoom out and review hole route and for stepByStep routing we just zoom to first marker position
    private fun mapSetPosition(overview: Boolean) {
        val centerFirstMarkerX = markers[0].latLng.latitude
        val centerFirstMarkerY = markers[0].latLng.longitude
        if (overview) {
            val centerFocalPositionX = (centerFirstMarkerX + markers[1].latLng.latitude) / 2
            val centerFocalPositionY = (centerFirstMarkerY + markers[1].latLng.longitude) / 2
            map.moveCamera(LatLng(centerFocalPositionX, centerFocalPositionY), 0.5f)
            map.setZoom(14f, 0.5f)
        } else {
            map.moveCamera(LatLng(centerFirstMarkerX, centerFirstMarkerY), 0.5f)
            map.setZoom(14f, 0.5f)
        }
    }

    private fun initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        settingsClient = LocationServices.getSettingsClient(requireActivity())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                // location is received
                userLocation = locationResult.lastLocation
                lastUpdateTime = DateFormat.getTimeInstance().format(Date())
                onLocationChange()
            }
        }
        mRequestingLocationUpdates = false
        locationRequest = LocationRequest()
        locationRequest.numUpdates = 10
        locationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        locationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        locationSettingsRequest = builder.build()
    }

    fun startReceivingLocationUpdates() {
        // Requesting ACCESS_FINE_LOCATION using Dexter library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mRequestingLocationUpdates = true
                startLocationUpdates()
            } else {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ), REQUEST_CODE
                )
            }

        } else {
            mRequestingLocationUpdates = true
            startLocationUpdates()
        }
    }

    /**
     * Starting location updates
     * Check whether location settings are satisfied and then
     * location updates will be requested
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        locationSettingsRequest?.let {
            settingsClient.checkLocationSettings(it).addOnSuccessListener(requireActivity()) {
                Log.i(TAG, "All location settings are satisfied.")
                locationCallback?.let { it1 ->
                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        it1,
                        Looper.myLooper()
                    )
                }
                onLocationChange()
            }
                .addOnFailureListener(requireActivity())
                { e ->
                    val statusCode = (e as ApiException).statusCode
                    when (statusCode) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                            Log.i(
                                TAG,
                                "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings "
                            )
                            if (mRequestingLocationUpdates == true) {
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    val rae = e as ResolvableApiException
                                    rae.startResolutionForResult(requireActivity(), REQUEST_CODE)
                                } catch (sie: SendIntentException) {
                                    Log.i(
                                        TAG,
                                        "PendingIntent unable to execute request."
                                    )
                                }
                            }
                        }

                        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                            val errorMessage = "Location settings are inadequate, and cannot be " +
                                    "fixed here. Fix in Settings."
                            Log.e(
                                TAG,
                                errorMessage
                            )
                            Toast.makeText(requireActivity(), errorMessage, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                    onLocationChange()
                }
        }
    }

    fun stopLocationUpdates() {
        // Removing location updates
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
                .addOnCompleteListener(
                    requireActivity()
                ) {
                    Toast.makeText(
                        requireContext(),
                        "Location updates stopped!",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
        }
    }

    private fun onLocationChange() {
        if (userLocation != null) {
            addUserMarker(LatLng(userLocation!!.latitude, userLocation!!.longitude))
            if (marker != null) {
                map.enableUserMarkerRotation(marker)
            }
            map.showAccuracyCircle(userLocation)
            map.moveCamera(LatLng(userLocation!!.latitude, userLocation!!.longitude), .5f)
        }
    }


    private fun addUserMarker(loc: LatLng) {
        //remove existing marker from map
        if (marker != null) {
            map.removeMarker(marker)
        }
        // Creating marker style. We should use an object of type MarkerStyleCreator, set all features on it
        // and then call buildStyle method on it. This method returns an object of type MarkerStyle
        val markStCr = MarkerStyleBuilder()
        markStCr.size = 70f
        markStCr.anchorPointX = 0f
        markStCr.anchorPointY = 0f
        markStCr.bitmap = BitmapUtils.createBitmapFromAndroidBitmap(
            BitmapFactory.decodeResource(
                resources, org.neshan.mapsdk.R.drawable.ic_user_loc_3
            )
        )
        val markSt = markStCr.buildStyle()

        // Creating user marker
        marker = Marker(loc, markSt)

        // Adding user marker to map!
        map.addMarker(marker)

        map.enableUserMarkerRotation(marker)
    }

    fun focusOnLocation(view: View?) {
        if (userLocation != null) {
            map.moveCamera(
                LatLng(userLocation!!.latitude, userLocation!!.longitude), 0.25f
            )
            map.setZoom(15f, 0.25f)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE -> when (resultCode) {
                RESULT_OK -> Log.e(
                    TAG,
                    "User agreed to make required location settings changes."
                )

                RESULT_CANCELED -> {
                    Log.e(
                        TAG,
                        "User choose not to make required location settings changes."
                    )
                    mRequestingLocationUpdates = false
                }
            }
        }


    }
}
