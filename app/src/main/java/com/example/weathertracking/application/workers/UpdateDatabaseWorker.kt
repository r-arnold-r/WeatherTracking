package com.example.weathertracking.application.workers

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.weathertracking.api.models.weatherconditions.WeatherCondition
import com.example.weathertracking.application.WeatherTrackingApplication
import com.example.weathertracking.repository.Repository
import com.example.weathertracking.utils.Constants
import com.example.weathertracking.utils.Constants.LAST_SIXTEEN_DAYS
import com.example.weathertracking.utils.Constants.NEXT_SIXTEEN_DAYS_AND_CURRENT
import com.example.weathertracking.utils.Constants.TODAY
import com.example.weathertracking.utils.Constants.TOMORROW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class UpdateDatabaseWorker(appContext: Context, workerParams: WorkerParameters): Worker(appContext, workerParams) {

    private val TAG = "UpdateDatabaseWorker"

    override fun doWork(): Result {
        CoroutineScope(Dispatchers.IO).launch {
            val repository = Repository()
            val includeDetailed = Constants.DETAILED_WEATHER_INCLUDES
            val elementsDetailed = Constants.DETAILED_WEATHER_ELEMENTS
            val includeGeneral = Constants.GENERAL_WEATHER_INCLUDES
            val elementsGeneral = Constants.GENERAL_WEATHER_ELEMENTS

            try {
                // get all
                val favouriteWeatherConditions =
                    WeatherTrackingApplication.weatherConditionDao.getAllFavouriteWeatherConditions()
                if (favouriteWeatherConditions != null) {
                    for (favouriteWeatherCondition in favouriteWeatherConditions) {
                        // update today
                        val weatherResponseToday = updateToday(favouriteWeatherCondition, repository, includeDetailed, elementsDetailed)
                        // update tomorrow
                        updateTomorrow(favouriteWeatherCondition, weatherResponseToday, repository, includeDetailed, elementsDetailed)
                        // update next days
                        updateNextDays(favouriteWeatherCondition, weatherResponseToday, repository, includeGeneral, elementsGeneral)
                        // update last days
                        updateLastDays(favouriteWeatherCondition, weatherResponseToday, repository, includeGeneral, elementsGeneral)
                    }

                }

            } catch (e: Exception) {
                Log.d(TAG, "doWork failed with: $e")
            }
        }


        // Indicate whether the work finished successfully with the Result
        return Result.success()
    }

    private suspend fun updateToday(favouriteWeatherCondition : WeatherCondition,
                                    repository: Repository, includeDetailed : String,
                                    elementsDetailed : String) : WeatherCondition?
    {
        val weatherResponseToday =
            favouriteWeatherCondition.unitGroup?.let { unitGroup ->
                repository.getWeather(
                    favouriteWeatherCondition.address,
                    TODAY,
                    WeatherTrackingApplication.KEY,
                    includeDetailed,
                    elementsDetailed,
                    unitGroup
                )
            }
        if (weatherResponseToday != null) {
            weatherResponseToday.unitGroup = favouriteWeatherCondition.unitGroup
            weatherResponseToday.isFavourite = favouriteWeatherCondition.isFavourite
            weatherResponseToday.isCurrentLocation = favouriteWeatherCondition.isCurrentLocation
            WeatherTrackingApplication.weatherConditionDao.insertOrUpdateFavourite(weatherResponseToday)
            Log.d(TAG, "doWork: weatherResponseToday was assigned")

            return weatherResponseToday
        }
        else{
            Log.d(TAG, "doWork: weatherResponseToday was null")
        }
        return null
    }

    private suspend fun updateTomorrow(favouriteWeatherCondition : WeatherCondition,
                                       weatherResponseToday : WeatherCondition?,
                                       repository: Repository, includeDetailed : String,
                                       elementsDetailed : String)
    {
        val weatherResponseTomorrow =
            favouriteWeatherCondition.unitGroup?.let { unitGroup ->
                repository.getWeather(
                    favouriteWeatherCondition.address,
                    TOMORROW,
                    WeatherTrackingApplication.KEY,
                    includeDetailed,
                    elementsDetailed,
                    unitGroup
                )
            }

        if (weatherResponseTomorrow != null) {
            weatherResponseTomorrow.unitGroup = favouriteWeatherCondition.unitGroup
            weatherResponseTomorrow.isFavourite =
                favouriteWeatherCondition.isFavourite
            if (weatherResponseToday != null) {
                WeatherTrackingApplication.weatherConditionDao.updateTomorrow(
                    weatherResponseTomorrow,
                    weatherResponseToday.address
                )
                Log.d(TAG, "doWork: weatherResponseTomorrow was assigned")
            }
        } else {
            Log.d(TAG, "doWork: weatherResponseTomorrow was null")
        }
    }

    private suspend fun updateNextDays(favouriteWeatherCondition : WeatherCondition,
                                       weatherResponseToday : WeatherCondition?,
                                       repository: Repository, includeGeneral : String,
                                       elementsGeneral : String)
    {
        if (weatherResponseToday != null) {
            if (weatherResponseToday.nextDays != null) {
                // get next days
                val weatherResponseNextDays =
                    favouriteWeatherCondition.unitGroup?.let { unitGroup ->
                        repository.getWeather(
                            favouriteWeatherCondition.address,
                            NEXT_SIXTEEN_DAYS_AND_CURRENT,
                            WeatherTrackingApplication.KEY,
                            includeGeneral,
                            elementsGeneral,
                            unitGroup
                        )
                    }

                // update next days
                if (weatherResponseNextDays != null) {
                    weatherResponseNextDays.unitGroup =
                        favouriteWeatherCondition.unitGroup
                    weatherResponseNextDays.isFavourite =
                        favouriteWeatherCondition.isFavourite
                    WeatherTrackingApplication.weatherConditionDao.updateNextDays(
                        weatherResponseNextDays.days,
                        favouriteWeatherCondition.address
                    )
                    Log.d(TAG, "doWork: weatherResponseNextDays was assigned")
                } else {
                    Log.d(TAG, "doWork: weatherResponseNextDays was null")
                }
            } else {
                Log.d(TAG, "doWork: weatherResponseToday.nextDays was null")
            }
        } else {
            Log.d(
                TAG,
                "doWork: weatherResponseToday was null can't continue with nextDays"
            )
        }
    }

    private suspend fun updateLastDays(favouriteWeatherCondition : WeatherCondition,
                                       weatherResponseToday : WeatherCondition?,
                                       repository: Repository, includeGeneral : String,
                                       elementsGeneral : String)
    {
        // try to update last days
        if (weatherResponseToday != null) {
            if (weatherResponseToday.lastDays != null) {
                // get last days
                val weatherResponseLastDays =
                    favouriteWeatherCondition.unitGroup?.let { unitGroup ->
                        repository.getWeather(
                            favouriteWeatherCondition.address,
                            LAST_SIXTEEN_DAYS,
                            WeatherTrackingApplication.KEY,
                            includeGeneral,
                            elementsGeneral,
                            unitGroup
                        )
                    }

                // update last days
                if (weatherResponseLastDays != null) {
                    weatherResponseLastDays.unitGroup =
                        favouriteWeatherCondition.unitGroup
                    weatherResponseLastDays.isFavourite =
                        favouriteWeatherCondition.isFavourite
                    WeatherTrackingApplication.weatherConditionDao.updateLastDays(
                        weatherResponseLastDays.days,
                        favouriteWeatherCondition.address
                    )
                    Log.d(TAG, "doWork: weatherResponseLastDays was assigned")
                } else {
                    Log.d(TAG, "doWork: weatherResponseLastDays was null")
                }
            } else {
                Log.d(TAG, "doWork: weatherResponseToday.lastDays was null")
            }
        } else {
            Log.d(
                TAG,
                "doWork: weatherResponseToday was null can't continue with lastDays"
            )
        }
    }
}