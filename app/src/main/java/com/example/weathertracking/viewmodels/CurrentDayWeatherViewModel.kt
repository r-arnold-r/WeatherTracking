package com.example.weathertracking.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.weathertracking.api.models.weatherconditions.WeatherCondition
import com.example.weathertracking.application.WeatherTrackingApplication
import com.example.weathertracking.repository.Repository
import com.example.weathertracking.utils.Constants.DETAILED_WEATHER_ELEMENTS
import com.example.weathertracking.utils.Constants.DETAILED_WEATHER_INCLUDES
import com.example.weathertracking.utils.Constants.TODAY
import com.example.weathertracking.utils.Utility

class CurrentDayWeatherViewModel(private val repository: Repository) : ViewModel() {

    val TAG = "CurrentDayWeatherVM"

    val currentDayWeatherCondition: MutableLiveData<WeatherCondition> = MutableLiveData()
    var currentDayWeatherConditionError: MutableLiveData<Exception> = MutableLiveData()

    suspend fun getLocationWeather(
        location: String,
        date: String,
        unitGroup: String,
        isFavourite: Boolean = false,
    ) {

        val include = DETAILED_WEATHER_INCLUDES
        val elements = DETAILED_WEATHER_ELEMENTS

        if (date != TODAY) {
                throw java.lang.IllegalArgumentException("Date must be : today")
        }

        try {
            val getWeatherResponse = Utility.measureTimeMillis({ time ->
                Log.d(
                    TAG,
                    "Get weather took $time ms"
                )
            }) {
                repository.getWeather(
                    location,
                    date,
                    WeatherTrackingApplication.KEY,
                    include,
                    elements,
                    unitGroup
                )
            }

            getWeatherResponse.unitGroup = unitGroup
            getWeatherResponse.isFavourite = isFavourite
            getWeatherResponse.isCurrentLocation = true

            currentDayWeatherCondition.postValue(getWeatherResponse)

        } catch (e: Exception) {
            exceptionHandler(e)
        }

    }

    private fun exceptionHandler(e: Exception) {
        Log.d(TAG, "exception: $e")
        currentDayWeatherConditionError.postValue(e)
    }
}
