package com.example.weathertracking.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.weathertracking.repository.Repository

class NextSixteenAndCurrentWeatherViewModelFactory(private val repository: Repository) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return NextSixteenDaysAndCurrentWeatherViewModel(repository) as T
    }
}