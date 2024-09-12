package com.example.finalprojectneshan

import android.Manifest
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
import org.neshan.mapsdk.MapView
import org.neshan.mapsdk.model.Marker
import org.neshan.servicessdk.search.model.Item
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity(),PassDataToActivity {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initViews()
        val secondFragment = SecondFragment(map.cameraTargetPosition,this)
        val thirdFragment=ThirdFragment(map.cameraTargetPosition,this)
        val editText1 = findViewById<EditText>(R.id.EditText1)
        val editText2 = findViewById<EditText>(R.id.EditText2)
        val secondFragmentLayout = R.id.flFragment
        val thirdFragmentLayout=R.id.flFragment

        editText1.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(secondFragmentLayout, secondFragment)
                addToBackStack(null)
                commit()
            }
        }
        editText2.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(thirdFragmentLayout,thirdFragment)
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
    }

    private fun initMap() {
        // Setting map focal position to a fixed position and setting camera zoom
        map.moveCamera(LatLng(35.767234, 51.330743), 0f)
        map.setZoom(14f, 0f)
    }

    private fun initViews() {
        map = findViewById(R.id.mapview)
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

    override fun passData(item: Item?) {
        val passData=findViewById<EditText>(R.id.EditText1)
        passData.setText(item?.address)
    }
    override fun passSecondData(data: Item?){
        val passSecondData=findViewById<EditText>(R.id.EditText2)
        passSecondData.setText(data?.address)
    }
}
