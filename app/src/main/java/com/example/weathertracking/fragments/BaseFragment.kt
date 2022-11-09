package com.example.weathertracking.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.weathertracking.application.WeatherTrackingApplication
import com.example.weathertracking.interfaces.*
import com.example.weathertracking.repository.Repository
import com.example.weathertracking.utils.ErrorProvider
import com.example.weathertracking.viewmodels.OneDayWeatherViewModel
import com.example.weathertracking.viewmodels.OneDayWeatherViewModelFactory

open class BaseFragment : Fragment(), IInternetStatusChange {

    private val TAG = "BaseFragment"
    protected lateinit var oneDayWeatherViewModel: OneDayWeatherViewModel
    protected var hideTopNavigationLayout = true

    protected lateinit var replaceFragmentListener: IReplaceFragment
    protected lateinit var accessActivityUI: IAccessActivityUI
    protected lateinit var accessActivityLocation: IAccessActivityLocation

    protected lateinit var errorProvider : ErrorProvider

    override fun onAttach(context: Context) {
        super.onAttach(context)

        try {
            replaceFragmentListener = context as IReplaceFragment
            accessActivityUI = context as IAccessActivityUI
            accessActivityLocation = context as IAccessActivityLocation
        } catch (castException: ClassCastException) {
            ErrorProvider(replaceFragmentListener, this.requireActivity().findViewById(android.R.id.content)).errorHandler("Unexpected error occurred!")
            Log.e(TAG, castException.message.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        errorProvider = ErrorProvider(replaceFragmentListener, this.requireActivity().findViewById(android.R.id.content))
        initializeOneDayWeatherViewModelWithFactory()
    }


    @CallSuper
    override fun onStart() {
        super.onStart()

        if (this is IHideLocationSearchButton) {
            accessActivityUI.hideLocationSearchButton()
        }
        if (this is IHideTopNavigationLayout && hideTopNavigationLayout) {
            accessActivityUI.hideTopNavigationLayout()
        }
        if (this is IHideSearchView) {
            accessActivityUI.hideSearchView()
        }
        if (this is IHideToolbar) {
            accessActivityUI.hideToolbar()
        }

    }

    @CallSuper
    override fun onStop() {
        super.onStop()

        if (this is IHideLocationSearchButton) {
            accessActivityUI.showLocationSearchButton()
        }
        if (this is IHideTopNavigationLayout && hideTopNavigationLayout) {
            accessActivityUI.showTopNavigationLayout()
        }
        if (this is IHideSearchView) {
            accessActivityUI.showSearchView()
        }
        if (this is IHideToolbar) {
            accessActivityUI.showToolbar()
        }

    }

    private fun initializeOneDayWeatherViewModelWithFactory() {
        // creates CurrentWeatherViewModel with factory
        val oneDayWeatherViewModelFactory = OneDayWeatherViewModelFactory(Repository())
        oneDayWeatherViewModel = ViewModelProvider(
            this,
            oneDayWeatherViewModelFactory
        )[OneDayWeatherViewModel::class.java]
    }


    override fun onDetach() {
        super.onDetach()
        WeatherTrackingApplication.removeFromUpdateListeners(requireContext())
    }

    override fun internetStatusChanged(isActivated: Boolean) {
        Log.d(TAG, "Not yet implemented!")
    }
}