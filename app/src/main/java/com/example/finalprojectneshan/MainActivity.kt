package com.example.finalprojectneshan

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.OnSuccessListener
import org.neshan.common.model.LatLng
import org.neshan.common.utils.PolylineEncoding
import org.neshan.mapsdk.MapView
import org.neshan.mapsdk.model.Marker
import org.neshan.mapsdk.model.Polyline
import org.neshan.servicessdk.direction.NeshanDirection
import org.neshan.servicessdk.direction.model.NeshanDirectionResult
import org.neshan.servicessdk.direction.model.Route
import org.neshan.servicessdk.search.model.Item
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.DateFormat
import java.util.Date

class MainActivity: AppCompatActivity(),PassDataToActivity{
    private val TAG: String = MainActivity::class.java.name

    // used to track request permissions
    private val REQUEST_CODE = 123

    // location updates interval - 1 sec
    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 1000

    // map UI element
    private lateinit var map: MapView

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
    private var marker: Marker? = null
    lateinit var textInput: EditText
////////
// define two toggle button and connecting together for two type of routing
private lateinit var overviewToggleButton: ToggleButton
    private lateinit var stepByStepToggleButton: ToggleButton

    // we save decoded Response of routing encoded string because we don't want request every time we clicked toggle buttons
    private var routeOverviewPolylinePoints: ArrayList<LatLng>? = null
    private var decodedStepByStepPath: ArrayList<LatLng>? = null

    // value for difference mapSetZoom
    private var overview = false

    // Marker that will be added on map
    private lateinit var mark: Marker

    // List of created markers
    private val markers: ArrayList<Marker> = ArrayList()

    // marker animation style
    private var animSt: AnimationStyle? = null

    // drawn path of route
    private var onMapPolyline: Polyline? = null


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        val secondFragment = SecondFragment(map.cameraTargetPosition, this)
        val thirdFragment = ThirdFragment(map.cameraTargetPosition, this)
        val editText1 = findViewById<EditText>(R.id.EditText1)
        val editText2 = findViewById<EditText>(R.id.EditText2)
        val button = findViewById<EditText>(R.id.Button)
        val secondFragmentLayout = R.id.flFragment
        val thirdFragmentLayout = R.id.flFragment

        editText1.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(secondFragmentLayout, secondFragment)
                addToBackStack(null)
                commit()
            }
        }
        editText2.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(thirdFragmentLayout, thirdFragment)
                addToBackStack(null)
                commit()

            }
        }
        button.setOnClickListener {
//            val origin = editText1.text.toString()
//            val destination = editText2.text.toString()
//            val bundle = Bundle()
//            bundle.putString("key_origin", origin)
//            bundle.putString("key_destination", destination)
//            val fragment =DelayFragment()//next fragment
//            fragment.arguments = bundle
            supportFragmentManager.beginTransaction().apply {
                replace(R.id.flFragment, DelayFragment())
                addToBackStack(null)
                commit()

            }
        }
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
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(
                    this@MainActivity,
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
        // Initializing views ()
        // Initializing mapView element
        initMap()
        // Initializing views
        initViews()

        map.setOnMapLongClickListener {
            if (markers.size < 2) {
                markers.add(addMarker(it))
                if (markers.size == 2) {
                    runOnUiThread {
                        overviewToggleButton.isChecked = true
                        neshanRoutingApi()
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "مسیریابی بین دو نقطه انجام میشود!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }


    }


    private fun initMap() {
        // Setting map focal position to a fixed position and setting camera zoom
        map.moveCamera(LatLng(35.767234, 51.330743), 0f)
        map.setZoom(14f, 0f)
    }

    private fun initViews() {
        map = findViewById(R.id.mapview)

    }

    // call this function with clicking on toggle buttons and draw routing line depend on type of routing requested
    fun findRoute(view: View?) {
        if (markers.size < 2) {
            Toast.makeText(
                this,
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

    private fun initLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        settingsClient = LocationServices.getSettingsClient(this)

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

        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_IN_MILLISECONDS
        ).build()
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        locationSettingsRequest = builder.build()
    }

    private fun startReceivingLocationUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this@MainActivity,
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
    private fun startLocationUpdates() {
        settingsClient
            .checkLocationSettings(locationSettingsRequest!!)
            .addOnSuccessListener(this, OnSuccessListener {
                Log.i(
                    TAG,
                    "All location settings are satisfied."
                )
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d("UserLocationUpdater", " required permissions are not granted ")
                    return@OnSuccessListener
                }
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback!!,
                    Looper.myLooper()
                )
            })
            .addOnFailureListener(this) { e ->
                val statusCode = (e as ApiException).statusCode
                when (statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                        Log.i(
                            TAG,
                            "Location settings are not satisfied. Attempting to upgrade location settings"
                        )
                        // Show the dialog by calling startResolutionForResult(), and check the
                        // result in onActivityResult().
                        val rae = e as ResolvableApiException
                        rae.startResolutionForResult(this@MainActivity, REQUEST_CODE)
                    } catch (sie: IntentSender.SendIntentException) {
                        Log.i(
                            TAG,
                            "PendingIntent unable to execute request."
                        )
                    }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        val errorMessage =
                            "Location settings are inadequate, and cannot be fixed here. Fix in Settings."
                        Log.e(
                            TAG,
                            errorMessage
                        )
                        Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
    }

    fun stopLocationUpdates() {
        // Removing location updates
        fusedLocationClient
            .removeLocationUpdates(locationCallback!!)
            .addOnCompleteListener(
                this
            ) {
                Toast.makeText(applicationContext, "Location updates stopped!", Toast.LENGTH_SHORT)
                    .show()
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

///
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
        mark= Marker(loc, markSt)

        // Adding marker to markerLayer, or showing marker on map!
        map.addMarker(mark)
        return mark
    }
    fun focusOnUserLocation(view: View?) {
        if (userLocation != null) {
            map.moveCamera(
                LatLng(userLocation!!.latitude, userLocation!!.longitude), 0.25f
            )
            map.setZoom(15f, 0.25f)
        } else {
            startReceivingLocationUpdates()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE -> {
                when (resultCode) {
                    RESULT_OK -> {
                        Log.e(
                            TAG,
                            "User agreed to make required location settings changes."
                        )
                        mRequestingLocationUpdates = true
                        startLocationUpdates()
                    }

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
                        Toast.makeText(this@MainActivity, "مسیری یافت نشد", Toast.LENGTH_LONG)
                            .show()
                    }
                }

                override fun onFailure(call: Call<NeshanDirectionResult?>, t: Throwable) {
                    Toast.makeText(this@MainActivity, "خطا در دریافت اطلاعات: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })
    }
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





    override fun passData(item: Item?) {
        val passData=findViewById<EditText>(R.id.EditText1)
        passData.setText(item?.address)

    }
    override fun passSecondData(data: Item?){
        val passSecondData=findViewById<EditText>(R.id.EditText2)
        passSecondData.setText(data?.address)

    }




}

