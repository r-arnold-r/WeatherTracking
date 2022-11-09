package com.example.weathertracking.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.example.weathertracking.application.WeatherTrackingApplication.Companion.isGPSEnabled
import com.example.weathertracking.extensionfunctions.ExtensionFunctions.showSnackBar
import com.example.weathertracking.interfaces.IAccessActivityLocation
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException


open class BaseGPSActivity : AppCompatActivity(), IAccessActivityLocation {

    private val TAG = "BaseGPSActivity"
    private lateinit var currentLocation: MutableLiveData<Location>
    private lateinit var currentLocationError: MutableLiveData<String>

    /** Request permission to get location data **/
    private var permissionRequester = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        when {
            isGranted -> onPermissionGranted()
            else -> {
                currentLocationError.value = "Permission denied"
                this.showSnackBar("Current location is disabled!")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentLocation = MutableLiveData()
        currentLocationError = MutableLiveData()
        observeCurrentLocation()
        observeCurrentError()
    }


    /** Observe successful current location **/
    private fun observeCurrentLocation()
    {
            currentLocation.observe(this) {
                isGPSEnabled = try{
                    currentLocationChanged(it)
                    true
                } catch (e : Exception) {
                    Log.e(TAG, e.toString())
                    false
                }
            }
    }

    /** Observe failed current location **/
    private fun observeCurrentError()
    {
            currentLocationError.observe(this) {
                isGPSEnabled = try {
                    currentLocationErrorChanged(it)
                    false
                } catch (e : Exception) {
                    Log.e(TAG, e.toString())
                    false
                }
            }

    }

    /** Sending current location for activities / fragments that implement IAccessActivityLocation,
     * Derived class has to implement**/
    override fun currentLocationChanged(location: Location) {}

    /** Sending current location error for fragments that implement IAccessActivityLocation,
     * Derived class has to implement**/
    override fun currentLocationErrorChanged(locationError: String) {}

    /** Searches for current location
     * Can be called from all fragments and activities that implement IAccessActivityLocation**/
    override fun searchForCurrentLocation() {
        lifecycleScope.launch(Dispatchers.Default){
            requirePermission()
        }
    }

    /** Requires permission is not already given and starts searching for current location **/
    private fun requirePermission() {

        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        permissionRequester.launch(permission)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    suspend fun FusedLocationProviderClient.awaitCurrentLocation(priority: Int): Location? {
        return suspendCancellableCoroutine {
            val cts = CancellationTokenSource()
            getCurrentLocation(priority, cts.token)
                .addOnSuccessListener { location ->
                    it.resume(location, null)
                }.addOnFailureListener { e ->
                    it.resumeWithException(e)
                }

            it.invokeOnCancellation {
                cts.cancel()
            }
        }
    }

    private fun onPermissionGranted() {
        val lm = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (LocationManagerCompat.isLocationEnabled(lm)) {
            val priority = Priority.PRIORITY_HIGH_ACCURACY
            lifecycleScope.launch {
                currentLocation.postValue(LocationServices
                    .getFusedLocationProviderClient(this@BaseGPSActivity)
                    .awaitCurrentLocation(priority))
            }
        } else {
            currentLocationError.value = "Location not enabled"
            this.showSnackBar("Please enable your location settings, to view your current location weather details!")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionRequester.unregister()
    }
}