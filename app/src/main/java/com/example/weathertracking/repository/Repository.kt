package com.example.weathertracking.repository

import com.example.weathertracking.api.RetrofitInstance
import com.example.weathertracking.api.models.weatherconditions.WeatherCondition

class Repository {

    suspend fun getWeather(
        location: String,
        date: String,
        key: String,
        include: String = "",
        elements: String = "",
        unitGroup: String = "metric"
    ): WeatherCondition {
        return RetrofitInstance.api.getCurrentWeather(
            location,
            date,
            key,
            include,
            elements,
            unitGroup
        )
    }
}