package com.example.weathertracking.viewmodels

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.weathertracking.api.models.weatherconditions.WeatherCondition
import com.example.weathertracking.application.WeatherTrackingApplication
import com.example.weathertracking.repository.Repository
import com.example.weathertracking.utils.Constants.GENERAL_WEATHER_ELEMENTS
import com.example.weathertracking.utils.Constants.GENERAL_WEATHER_INCLUDES
import com.example.weathertracking.utils.Constants.LAST_SIXTEEN_DAYS
import com.example.weathertracking.utils.Utility

class LastSixteenDaysWeatherViewModel(private val repository: Repository) : ViewModel() {

    private val TAG = "LastSixteenDaysWVM"
    val lastSixteenWeatherCondition: MutableLiveData<WeatherCondition> = MutableLiveData()
    var lastSixteenWeatherConditionError: MutableLiveData<Exception> = MutableLiveData()

    suspend fun getLocationWeather(
        location: String,
        unitGroup: String,
        isFavourite: Boolean = false,
    ) {

        val include = GENERAL_WEATHER_INCLUDES
        val elements = GENERAL_WEATHER_ELEMENTS
        val date = LAST_SIXTEEN_DAYS

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

            lastSixteenWeatherCondition.postValue(getWeatherResponse)
        } catch (e: Exception) {
            exceptionHandler(e)
        }

    }

    private fun exceptionHandler(e: Exception) {
        Log.d(TAG, "exception: $e")
        lastSixteenWeatherConditionError.postValue(e)

    }
}
