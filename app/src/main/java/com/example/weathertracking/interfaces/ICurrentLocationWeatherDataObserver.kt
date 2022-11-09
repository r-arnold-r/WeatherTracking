package com.example.weathertracking.interfaces

import com.example.weathertracking.api.models.weatherconditions.WeatherCondition

interface ICurrentLocationWeatherDataObserver {
    fun currentWeatherCondition(weatherCondition: WeatherCondition)
}