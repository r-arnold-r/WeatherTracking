package com.example.weathertracking.application

import android.app.Application
import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.example.weathertracking.application.workers.UpdateDatabaseWorker
import com.example.weathertracking.database.WeatherConditionDao
import com.example.weathertracking.database.WeatherConditionDatabase
import com.example.weathertracking.interfaces.IUpdateDataListener
import com.example.weathertracking.manager.SharedPreferencesManager
import com.example.weathertracking.utils.Constants.THIRTY_MINUTES
import com.example.weathertracking.utils.Timer
import java.util.concurrent.TimeUnit

class WeatherTrackingApplication : Application() {

    private lateinit var timer: Timer


    private lateinit var weatherConditionDatabase: WeatherConditionDatabase

    companion object {
        const val KEY = "25KRHKL8Z43Q4K8PUJGNQNSAY"
        private const val TAG = "WeatherTrackingApp"

        lateinit var appContext: Context
        lateinit var geocoder: Geocoder
        lateinit var sharedPreferencesManager: SharedPreferencesManager
        lateinit var weatherConditionDao: WeatherConditionDao
        private lateinit var updateDataListeners: MutableList<IUpdateDataListener>
        var isOnline = false
        var isGPSEnabled = false

        fun addToUpdateListeners(context: Context)
        {
            try {
                updateDataListeners.add(context as IUpdateDataListener)
            } catch (castException: ClassCastException) {
                Log.e(TAG, castException.message.toString())
            }
        }

        fun removeFromUpdateListeners(context: Context)
        {
            for(i in updateDataListeners.indices)
            {
                if(updateDataListeners[i] == context)
                {
                    updateDataListeners.removeAt(i)
                    break
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        geocoder = Geocoder(this)
        appContext = this
        weatherConditionDatabase = WeatherConditionDatabase.getInstance(this)
        weatherConditionDao = weatherConditionDatabase.weatherConditionDao()
        updateDataListeners = mutableListOf()
        sharedPreferencesManager = SharedPreferencesManager(applicationContext)

        // send update request every 30 minutes
        timer = Timer ({ updateData() }, THIRTY_MINUTES)
        timer.startTimer()

        // update database every 12 hours
        startUpdateDatabaseWorker(12L, TimeUnit.HOURS)
    }

    /** Update database data after every :repeatInterval :timeUnit **/
    private fun startUpdateDatabaseWorker(repeatInterval : Long, timeUnit: TimeUnit)
    {
        val periodicWork = PeriodicWorkRequest.Builder(UpdateDatabaseWorker::class.java, repeatInterval, timeUnit)
            .build()
        val completed = WorkManager.getInstance(this).enqueue(periodicWork)
        Log.d(TAG, "periodicWork:  ${completed.result}")
    }

    override fun onTerminate() {
        super.onTerminate()
        timer.cancelTimer()
    }

    /** Notify listeners to update their data every X (30) minutes **/
    private fun updateData()
    {
        for(updateListener in updateDataListeners)
        {
            updateListener.updateData()
        }
    }


}