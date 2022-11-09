package com.example.weathertracking.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weathertracking.adapter.LocationAdapter
import com.example.weathertracking.api.models.weatherconditions.WeatherCondition
import com.example.weathertracking.application.WeatherTrackingApplication
import com.example.weathertracking.databinding.FragmentLocationsBinding
import com.example.weathertracking.fragments.LocationsFragment.LocationsFragmentConstants.SELECTED_LOCATION
import com.example.weathertracking.interfaces.IFragmentUpdateData
import com.example.weathertracking.interfaces.IHideToolbar
import com.example.weathertracking.interfaces.IUpdateDataListener
import com.example.weathertracking.manager.SharedPreferencesManager.Companion.LOCATIONS_FRAGMENT_UPDATE_TIMESTAMP
import com.example.weathertracking.utils.Constants.THIRTY_MINUTES
import com.example.weathertracking.utils.Constants.TODAY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationsFragment : BaseFragment(), IHideToolbar, IUpdateDataListener, IFragmentUpdateData, LocationAdapter.ItemClickListener {

    object LocationsFragmentConstants {
        const val SELECTED_LOCATION = "SelectedLocation"
    }
    private val TAG = "LocationsFragment"
    private lateinit var binding: FragmentLocationsBinding
    private lateinit var recyclerView: RecyclerView
    private lateinit var locationAdapter: LocationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        observeOneDayWeatherViewModel()
        if(savedInstanceState == null)
        {
            updateData()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLocationsBinding.inflate(layoutInflater, container, false)

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeRecyclerViewWithWeatherDataSavedInTheDatabase()
        searchViewHandler()
    }

    override fun onDetach() {
        super.onDetach()
        WeatherTrackingApplication.removeFromUpdateListeners(requireContext())
    }

    /** Show detailed information about the clicked adapter item in Location Details Fragment **/
    override fun onLocationAdapterItemClick(position: Int) {
        accessActivityUI.showLoadingDialog()

        Log.d(TAG, locationAdapter.weatherConditionFilterList[position].toString())

        val detailsFragment = LocationDetailsFragment(SELECTED_LOCATION)
        val bundle = Bundle()
        val location = locationAdapter.weatherConditionFilterList[position].address

        bundle.putString("location", location)
        bundle.putString("unitGroup", locationAdapter.weatherConditionFilterList[position].unitGroup )
        bundle.putBoolean("isFavourite", locationAdapter.weatherConditionFilterList[position].isFavourite)
        detailsFragment.arguments = bundle

        replaceFragmentListener.replaceFragment(detailsFragment)
    }

    private fun initializeRecyclerViewWithWeatherDataSavedInTheDatabase(){
        lifecycleScope.launch(Dispatchers.IO) {
            // get all favourite weather data from the database
            val weatherConditions =
                WeatherTrackingApplication.weatherConditionDao.getAllFavouriteWeatherConditions()
                    ?.toMutableList()
            withContext(Dispatchers.Main)
            {
                if(weatherConditions != null)
                {
                    recycleViewAndAdapterHandler(weatherConditions)
                }
            }
        }
    }

    /**initializes recycleView and adapter**/
    @SuppressLint("NotifyDataSetChanged")
    private fun recycleViewAndAdapterHandler(locations: MutableList<WeatherCondition>) {
        //creating and setting up adapter with recyclerView
        recyclerView = binding.recyclerViewLocations

        //creating and setting up adapter with recyclerView
        locationAdapter =
            LocationAdapter(this, locations) //setting the data and listener for adapter

        val layoutManager: RecyclerView.LayoutManager =
            LinearLayoutManager(requireActivity(), RecyclerView.VERTICAL, false)
        recyclerView.layoutManager = layoutManager
        recyclerView.itemAnimator = DefaultItemAnimator()
        recyclerView.adapter = locationAdapter

        recyclerView.addItemDecoration(
            DividerItemDecoration(
                requireActivity(),
                DividerItemDecoration.VERTICAL
            )
        )

        locationAdapter.notifyDataSetChanged()
    }


    /** Controls search view **/
    private fun searchViewHandler() {
        accessActivityUI.getSearchView()
            .setOnQueryTextListener(object : SearchView.OnQueryTextListener,
                androidx.appcompat.widget.SearchView.OnQueryTextListener {

                override fun onQueryTextChange(newText: String): Boolean {
                    // filters text
                    if (::locationAdapter.isInitialized) {
                        locationAdapter.filter.filter(newText)
                    }
                    return false
                }

                override fun onQueryTextSubmit(query: String): Boolean {
                    // do nothing
                    return false
                }
            })
    }

    override fun updateData() {
        lifecycleScope.launch(Dispatchers.IO){
            // get last updated time
            val timestamp = WeatherTrackingApplication.sharedPreferencesManager[LOCATIONS_FRAGMENT_UPDATE_TIMESTAMP]
            if(timestamp  != null)
            {
                // when it was already updated
                val diff = System.currentTimeMillis() - timestamp.toLong()
                if(diff > THIRTY_MINUTES)
                {
                    // when 30 minutes passed from the last update update favourite weather data
                    val weatherConditions =
                        WeatherTrackingApplication.weatherConditionDao.getAllFavouriteWeatherConditions()
                            ?.toMutableList()
                    if(weatherConditions != null)
                    {
                        updateFavouriteLocations(weatherConditions)
                    }

                    // reset updated time
                    WeatherTrackingApplication.sharedPreferencesManager[LOCATIONS_FRAGMENT_UPDATE_TIMESTAMP] =
                        System.currentTimeMillis().toString()
                }
            }
            else
            {
                // when it was never updated
                val weatherConditions =
                    WeatherTrackingApplication.weatherConditionDao.getAllFavouriteWeatherConditions()
                        ?.toMutableList()
                if(weatherConditions != null)
                {
                    updateFavouriteLocations(weatherConditions)
                }

                // reset updated time
                WeatherTrackingApplication.sharedPreferencesManager[LOCATIONS_FRAGMENT_UPDATE_TIMESTAMP] =
                    System.currentTimeMillis().toString()
            }
        }
    }

    private fun updateFavouriteLocations(favouriteLocations: MutableList<WeatherCondition>) {
        lifecycleScope.launch(Dispatchers.IO) {
            for (favouriteLocation in favouriteLocations) {
                oneDayWeatherViewModel.getLocationWeather(
                    favouriteLocation.address,
                    TODAY,
                    favouriteLocation.unitGroup.toString(),
                    favouriteLocation.isFavourite
                )
            }
        }
    }

    private fun observeOneDayWeatherViewModel() {
        oneDayWeatherViewModel.oneDayWeatherCondition.observe(this) { weatherCondition ->
            lifecycleScope.launch(Dispatchers.IO)
            {
                // update database value
                WeatherTrackingApplication.weatherConditionDao.update(weatherCondition.latitude, weatherCondition.longitude, weatherCondition.resolvedAddress,
                                                                        weatherCondition.address, weatherCondition.days, weatherCondition.currentConditions, weatherCondition.description,
                                                                        weatherCondition.unitGroup, weatherCondition.isFavourite, weatherCondition.isCurrentLocation) > 0
                Log.d(TAG, "Updated favourite location: $weatherCondition")
            }
            //updateRecycleView(weatherCondition, index)
        }

        oneDayWeatherViewModel.oneDayWeatherConditionError.observe(this) { e ->
            errorProvider.errorHandler(e)
        }
    }


    /*
private fun updateRecycleView(weatherCondition: WeatherCondition, index: Int)
{
    if(this::locationAdapter.isInitialized)
    {
        locationAdapter.weatherConditionFilterList[index] = weatherCondition
        locationAdapter.notifyItemChanged(index)
    }
    else
    {
        Log.d(TAG, "Location adapter was not initialized!")
    }
}
*/
}