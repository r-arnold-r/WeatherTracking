package com.example.weathertracking.interfaces

import android.location.Location

interface IAccessActivityLocation {
    fun currentLocationChanged(location: Location)
    fun currentLocationErrorChanged(locationError : String)
    fun searchForCurrentLocation()
}