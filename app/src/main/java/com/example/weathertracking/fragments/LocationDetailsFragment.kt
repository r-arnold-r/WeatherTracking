package com.example.weathertracking.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.weathertracking.R
import com.example.weathertracking.adapter.DayAdapter
import com.example.weathertracking.adapter.HourAdapter
import com.example.weathertracking.api.models.weatherconditions.Day
import com.example.weathertracking.api.models.weatherconditions.WeatherCondition
import com.example.weathertracking.application.WeatherTrackingApplication
import com.example.weathertracking.databinding.FragmentDetailsBinding
import com.example.weathertracking.extensionfunctions.ExtensionFunctions.showSnackBar
import com.example.weathertracking.fragments.LocationDetailsFragment.LocationDetailsFragmentConstants.CURRENT_LOCATION
import com.example.weathertracking.fragments.LocationDetailsFragment.LocationDetailsFragmentConstants.SELECTED_LOCATION
import com.example.weathertracking.fragments.LocationSearchFragment.LocationSearchFragmentConstants.BUNDLE_IS_FAVOURITE
import com.example.weathertracking.fragments.LocationSearchFragment.LocationSearchFragmentConstants.BUNDLE_LOCATION
import com.example.weathertracking.fragments.LocationSearchFragment.LocationSearchFragmentConstants.BUNDLE_UNITGROUP
import com.example.weathertracking.interfaces.*
import com.example.weathertracking.repository.Repository
import com.example.weathertracking.utils.Constants.METRIC_UNIT_GROUP
import com.example.weathertracking.utils.Constants.TODAY
import com.example.weathertracking.utils.Constants.TOMORROW
import com.example.weathertracking.utils.Utility
import com.example.weathertracking.utils.Utility.collapse
import com.example.weathertracking.utils.Utility.expand
import com.example.weathertracking.viewmodels.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class LocationDetailsFragment(private val type: String = CURRENT_LOCATION) : BaseFragment(),
    IHideTopNavigationLayout, IHideSearchView, IFragmentUpdateData, ICurrentLocationWeatherDataObserver,
    HourAdapter.ItemClickListener, DayAdapter.ItemClickListener {

    object LocationDetailsFragmentConstants {
        const val CURRENT_LOCATION = "CurrentLocation"
        const val SELECTED_LOCATION = "SelectedLocation"
    }
    private val TAG = "LocationDetailsFragment"

    init {
        if (type == CURRENT_LOCATION) {
            super.hideTopNavigationLayout = false
        }
        if (type == SELECTED_LOCATION) {
            super.hideTopNavigationLayout = true
        }
    }
    private lateinit var binding: FragmentDetailsBinding

    private lateinit var hourAdapter: HourAdapter
    private lateinit var dayAdapter: DayAdapter

    private lateinit var dayRecyclerView: RecyclerView
    private lateinit var hourRecyclerView: RecyclerView
    private lateinit var nextSixteenDaysAndCurrentWeatherViewModel: NextSixteenDaysAndCurrentWeatherViewModel
    private lateinit var lastSixteenDaysWeatherViewModel: LastSixteenDaysWeatherViewModel

    private lateinit var locationName: String
    private var unitGroup: String = METRIC_UNIT_GROUP
    private var isFavourite: Boolean = false
    private lateinit var todayWeatherDetails : WeatherCondition
    private var detailsIsUp : Boolean = true


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDetailsBinding.inflate(layoutInflater, container, false)

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeNextSixteenDaysAndCurrentWeatherViewModelWithFactory()
        initializeLastSixteenDaysWeatherViewModelWithFactory()

        binding.lastSixteenDaysBtn.setOnClickListener {
            onLastSixteenDaysBtnClicked()
        }
        binding.nextSixteenDaysBtn.setOnClickListener {
            onNextSixteenDaysBtnClicked()
        }
        binding.tomorrowBtn.setOnClickListener {
            tomorrowBtnClicked()
        }
        binding.detailsBtn.setOnClickListener {
            detailsBtnClicked()
        }

        observeOneDayWeatherViewModel()
        observeLastSixteenDaysWeatherViewModel()
        observeNextSixteenDaysAndCurrentWeatherViewModel()

        if(savedInstanceState == null)
        {
            initializeToolbarMenu()
        }

        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()

        // To disallow observer from using its value after fragment reload
        oneDayWeatherViewModel.oneDayWeatherCondition.value = null
        lastSixteenDaysWeatherViewModel.lastSixteenWeatherCondition.value = null
        nextSixteenDaysAndCurrentWeatherViewModel.nextSixteenDaysAndCurrentWeatherCondition.value = null
    }

    override fun onDayItemClick(position: Int) {
        Log.d(TAG, "Day: " + dayAdapter.getItemData(position).toString())
    }

    /** Update UI with clicked adapter item weather data **/
    override fun onHourItemClick(position: Int) {

        setDetailsToUI(hourAdapter.getItemData(position), unitGroup, hourAdapter.sunriseEpoch,hourAdapter.sunsetEpoch)
        Log.d(TAG, "Hour: " + hourAdapter.getItemData(position).toString())
    }

    /** Update data every X (30) minutes **/
    override fun updateData() {
        if(this::locationName.isInitialized)
        {
            if (binding.tomorrowBtn.text.equals(getString(R.string.show_tomorrow))) {
                getOneDayWeather(TODAY, unitGroup, isFavourite)
            } else {
                getOneDayWeather(TOMORROW, unitGroup, isFavourite)
            }
        }
    }

    /** Update data when internet got activated **/
    override fun internetStatusChanged(isActivated: Boolean) {
        if(isActivated)
        {
            updateData()
        }
    }

    private fun initializeToolbarMenu()
    {
        accessActivityUI.getToolbar().menu[0].setIcon(R.drawable.ic_heart_white)
        accessActivityUI.getToolbar().menu[0].isEnabled = false
        accessActivityUI.getToolbar().menu[1].isEnabled = false

        accessActivityUI.getToolbar().setOnMenuItemClickListener{
            when (it.itemId) {
                R.id.menu_toolbar_favorite -> showSnackBar("Not available!")
                R.id.menu_toolbar_reload -> {
                    accessActivityUI.showLoadingDialog()
                    // search for current location
                    // sends error message if couldn't get response
                    accessActivityLocation.searchForCurrentLocation()
                }
                else -> {
                    showSnackBar("Not available!")
                }
            }
            true
        }
    }

    /** Controlling dropdown to show more details about the weather **/
    private fun detailsBtnClicked()
    {
        if (detailsIsUp) {
            expand(binding.detailsCl, 1000, 550)
            val drawable = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_collapse)
            binding.detailsBtn.setImageDrawable(drawable)
        } else {
            collapse(binding.detailsCl, 1000, 0)
            val drawable = ContextCompat.getDrawable(requireActivity(), R.drawable.ic_expand)
            binding.detailsBtn.setImageDrawable(drawable)
        }
        detailsIsUp = !detailsIsUp
    }

    /** Show simple weather data from today to 16 days before **/
    private fun onLastSixteenDaysBtnClicked() {
        accessActivityUI.showLoadingDialog()

        if(WeatherTrackingApplication.isOnline)
        {
            // internet connection is working

            if (locationName.isNotEmpty()) {
                // start API call
                getLastSixteenDaysWeather()
            }
        }
        else
        {
            // internet connection is not working

            lifecycleScope.launch(Dispatchers.IO){

                // get weather data about the location from the database
                val weatherCondition = WeatherTrackingApplication.weatherConditionDao.getWeatherCondition(locationName)
                val days = weatherCondition?.lastDays?.toMutableList()
                withContext(Dispatchers.Main)
                {
                    if(days != null)
                    {
                        // weather data found for the selected location
                        // fill recyclerview with data from the database
                        weatherCondition.unitGroup?.let { dayRecycleViewAndAdapterHandler(days, it, 0) }
                        binding.nextSixteenDaysBtn.isEnabled = true
                    }
                    else
                    {
                        // weather data not found for the selected location
                        showSnackBar("You are in offline mode!")
                    }
                    binding.lastSixteenDaysBtn.isEnabled = true
                    accessActivityUI.hideLoadingDialog()
                }
            }
        }

        binding.lastSixteenDaysBtn.isEnabled = false
    }

    private fun onNextSixteenDaysBtnClicked() {
        accessActivityUI.showLoadingDialog()
        if(WeatherTrackingApplication.isOnline)
        {
            // internet connection is working
            if (locationName.isNotEmpty()) {
                // start API call
                getNextSixteenAndCurrentDayWeather()
            }
        }
        else
        {
            // internet connection is not working

            lifecycleScope.launch(Dispatchers.IO){
                val weatherCondition = WeatherTrackingApplication.weatherConditionDao.getWeatherCondition(locationName)
                val days = weatherCondition?.nextDays?.toMutableList()
                withContext(Dispatchers.Main)
                {
                    if(days != null)
                    {
                        // weather data found for the selected location
                        // fill recyclerview with data from the database
                        weatherCondition.unitGroup?.let { dayRecycleViewAndAdapterHandler(days, it, 0) }
                    }
                    else
                    {
                        // weather data not found for the selected location
                        showSnackBar("You are in offline mode!")
                    }
                    binding.nextSixteenDaysBtn.isEnabled = true
                    accessActivityUI.hideLoadingDialog()
                }

            }
        }

        binding.nextSixteenDaysBtn.isEnabled = false
    }

    private fun tomorrowBtnClicked() {
        accessActivityUI.showLoadingDialog()
        if (locationName.isNotEmpty()) {
            // when tomorrow button is equals tomorrow it shows todays weather
            // switch to tomorrow weather
            if (binding.tomorrowBtn.text.equals(getString(R.string.show_tomorrow))) {
                // update text
                binding.tomorrowBtn.text = getString(R.string.show_today)

                if(WeatherTrackingApplication.isOnline) {
                    // internet connection is working
                    // start API call
                    updateData()
                }
                else {
                    // internet connection is not working
                    lifecycleScope.launch(Dispatchers.IO){
                        // get weather data about the location from the database
                        val weatherCondition = WeatherTrackingApplication.weatherConditionDao.getWeatherCondition(locationName)
                        val weatherConditionTomorrow = weatherCondition?.tomorrow
                        withContext(Dispatchers.Main)
                        {
                            if(weatherConditionTomorrow != null)
                             {
                                 // weather data found for the selected day
                                updateUI(weatherConditionTomorrow)
                            }
                            else
                            {
                                // weather data not found for the selected day
                                showSnackBar("You are in offline mode!")
                            }
                            binding.tomorrowBtn.isEnabled = true
                            accessActivityUI.hideLoadingDialog()
                        }
                    }
                }
                // when tomorrow button is equals today it shows tomorrows weather
                // switch to today weather
            } else if (binding.tomorrowBtn.text.equals(getString(R.string.show_today))) {
                binding.tomorrowBtn.text = getString(R.string.show_tomorrow)
                if(WeatherTrackingApplication.isOnline) {
                    // internet connection is working
                    // start API call
                    updateData()
                }
                else {
                    lifecycleScope.launch(Dispatchers.IO) {
                        // get weather data about the location from the database
                        val weatherConditionToday =
                            WeatherTrackingApplication.weatherConditionDao.getWeatherCondition(
                                locationName
                            )
                        withContext(Dispatchers.Main)
                        {
                            // weather data found for the selected day
                            if (weatherConditionToday != null) {
                                updateUI(weatherConditionToday)
                            } else {
                                // weather data not found for the selected day
                                showSnackBar("You are in offline mode!")
                            }
                            binding.tomorrowBtn.isEnabled = true
                            accessActivityUI.hideLoadingDialog()
                        }
                    }
                }

            }
        }

        binding.tomorrowBtn.isEnabled = false
    }


    /** set locations from bundle **/
    private fun setLocationFromBundle() {
        val bundle = arguments
        if (bundle != null) {
            val selectedLocation = bundle.getString(BUNDLE_LOCATION)
            if (selectedLocation?.isNotEmpty() == true) {
                locationName = selectedLocation
            }
            unitGroup = bundle.getString(BUNDLE_UNITGROUP).toString()
            isFavourite = bundle.getBoolean(BUNDLE_IS_FAVOURITE)
        }
    }


    /**View model factory section**/
    private fun initializeNextSixteenDaysAndCurrentWeatherViewModelWithFactory() {
        // creates NextSixteenDaysAndCurrentWeatherViewModel with factory
        val nextSixteenDaysAndCurrentWeatherViewModelFactory =
            NextSixteenAndCurrentWeatherViewModelFactory(Repository())
        nextSixteenDaysAndCurrentWeatherViewModel = ViewModelProvider(
            this,
            nextSixteenDaysAndCurrentWeatherViewModelFactory
        )[NextSixteenDaysAndCurrentWeatherViewModel::class.java]
    }

    private fun initializeLastSixteenDaysWeatherViewModelWithFactory() {
        // creates LastSixteenDaysWeatherViewModel with factory
        val lastSixteenDaysWeatherViewModelFactory =
            LastSixteenDaysWeatherViewModelFactory(Repository())
        lastSixteenDaysWeatherViewModel = ViewModelProvider(
            this,
            lastSixteenDaysWeatherViewModelFactory
        )[LastSixteenDaysWeatherViewModel::class.java]
    }

    /**set details to UI**/
    private fun setDetailsToUI(weatherData : IWeatherData, unitGroup: String, sunriseEpoch : Long, sunsetEpoch : Long) {
        val degree = if (unitGroup == "metric") "C" else "F"
        val speed = if (unitGroup == "metric") "km/h" else "mph"
        val distance = if (unitGroup == "metric") "km" else "miles"

        try{
            if(weatherData.preciptype != null)
            {
                for(precip in weatherData.preciptype!!)
                {
                    when(precip){
                        "rain" -> binding.precipTypeRainIv.visibility = VISIBLE
                        "snow" -> binding.precipTypeSnowIv.visibility = VISIBLE
                        "freezingrain" -> binding.precipTypeFreezingRainIv.visibility = VISIBLE
                        "ice" -> binding.precipTypeIceIv.visibility = VISIBLE
                    }
                }
            }

        val img = weatherData.datetimeEpoch?.let {
            Utility.getImageIcon(weatherData.icon,
                sunriseEpoch, sunsetEpoch, it)
        }
        binding.weatherIv.setImageDrawable(img)

        if(weatherData.precipprob == null)
            weatherData.precipprob = 0.0
        if(weatherData.windgust == null)
            weatherData.windgust = 0.0

        binding.timeTv.text = weatherData.datetime
        binding.tempLikeTv.text = getString(
            R.string.temp,
            weatherData.temp.toString(),
            degree
        )
        binding.feelsLikeTv.text = getString(
            R.string.feels_like,
            weatherData.feelslike.toString(),
            degree
        )
        binding.precipProbTv.text =
            getString(R.string.precipprob, weatherData.precipprob.toString())
        binding.windTv.text =
            getString(R.string.wind, weatherData.windspeed.toString(), speed)
        binding.windGustTv.text =
            getString(R.string.wind_gust, weatherData.windgust.toString(), speed)
        binding.humidityTv.text =
            getString(R.string.humidity, weatherData.humidity.toString())
        binding.dewPointTv.text =
            getString(R.string.dew_point, weatherData.dew.toString(), degree)
        binding.cloudCoverTv.text =
            getString(R.string.cloud_cover, weatherData.cloudcover.toString())
        binding.visiblityTv.text =
            getString(R.string.visibility, weatherData.visibility.toString(), distance)
        }catch (e : Exception)
        {
            Log.e(TAG, "setDetailsToUI failed!")
        }
    }

    /**initializes hour recycleView and adapter**/
    @SuppressLint("NotifyDataSetChanged")
    private fun hourRecycleViewAndAdapterHandler(
        day: Day,
        indexToJumpTo: Int
    ) {
        //creating and setting up adapter with recyclerView
        hourRecyclerView = binding.hoursRv

        //creating and setting up adapter with recyclerView
        try {
            val sunsetEpoch = day.sunsetEpoch!!
            val sunriseEpoch = day.sunriseEpoch!!
            hourAdapter =
                day.hours?.let {
                    HourAdapter(
                        this,
                        it.toMutableList(),
                        unitGroup,
                        sunriseEpoch,
                        sunsetEpoch
                    )
                }!!
        }catch (e : Exception)
        {
            Log.e(TAG, "sunsetEpoch or sunriseEpoch was null!")
        }

        val layoutManager: RecyclerView.LayoutManager =
            LinearLayoutManager(activity, RecyclerView.HORIZONTAL, false)

        layoutManager.scrollToPosition(indexToJumpTo)
        hourRecyclerView.layoutManager = layoutManager
        hourRecyclerView.itemAnimator = DefaultItemAnimator()
        hourRecyclerView.adapter = hourAdapter

        if (hourRecyclerView.itemDecorationCount < 1) {
            hourRecyclerView.addItemDecoration(
                DividerItemDecoration(
                    requireActivity(),
                    DividerItemDecoration.HORIZONTAL
                )
            )
        }

        hourAdapter.notifyDataSetChanged()
    }


    /**initializes day recycleView and adapter**/
    @SuppressLint("NotifyDataSetChanged")
    private fun dayRecycleViewAndAdapterHandler(
        days: MutableList<Day>,
        unitGroup: String,
        indexToJumpTo: Int,
        reverseLayout: Boolean = false
    ) {
        //creating and setting up adapter with recyclerView
        dayRecyclerView = binding.daysRv

        //creating and setting up adapter with recyclerView
        dayAdapter = DayAdapter(this, days, unitGroup) //setting the data and listener for adapter

        val layoutManager: RecyclerView.LayoutManager =
            LinearLayoutManager(activity, RecyclerView.VERTICAL, reverseLayout)

        layoutManager.scrollToPosition(indexToJumpTo)
        dayRecyclerView.layoutManager = layoutManager
        dayRecyclerView.itemAnimator = DefaultItemAnimator()
        dayRecyclerView.adapter = dayAdapter

        if (dayRecyclerView.itemDecorationCount < 1) {
            dayRecyclerView.addItemDecoration(
                DividerItemDecoration(
                    requireActivity(),
                    DividerItemDecoration.VERTICAL
                )
            )
        }

        dayAdapter.notifyDataSetChanged()
    }


    private fun loadData() {
        accessActivityUI.getToolbar().title = "Currently not available!"
        // need to get weather data for current location
        if (type == CURRENT_LOCATION) {
            if(WeatherTrackingApplication.isOnline && WeatherTrackingApplication.isGPSEnabled)
            {
                // internet connection is working and GPS is enabled
                accessActivityLocation.searchForCurrentLocation()
            }
            else
            {
                // internet connection is not working and/or GPS is not enabled
                lifecycleScope.launch(Dispatchers.IO)
                {
                    // get current location weather data from database
                    val weatherCondition = WeatherTrackingApplication.weatherConditionDao.getCurrentWeatherCondition()
                    withContext(Dispatchers.Main){
                        if (weatherCondition != null) {
                            // weather data was found
                            // update ui elements
                            locationName = weatherCondition.address
                            updateUI(weatherCondition)
                            todayWeatherDetails = weatherCondition
                            setupToolbarMenuHandler(todayWeatherDetails)
                            binding.nextSixteenDaysBtn.isEnabled = true
                            binding.lastSixteenDaysBtn.isEnabled = true
                        }
                        accessActivityUI.hideLoadingDialog()
                    }
                }
            }

        }
        // need to get weather data for a selected location
        if (type == SELECTED_LOCATION) {
            setLocationFromBundle()
            if(WeatherTrackingApplication.isOnline)
            {
                // internet connection is working
                updateData()
            }
            else
            {
                // internet connection is not working
                lifecycleScope.launch(Dispatchers.IO)
                {
                    // get selected location weather data from database
                    val weatherCondition = WeatherTrackingApplication.weatherConditionDao.getWeatherCondition(locationName)
                    withContext(Dispatchers.Main){
                        if (weatherCondition != null) {
                            // weather data was found
                            // update UI
                            todayWeatherDetails = weatherCondition
                            updateUI(weatherCondition)
                            setupToolbarMenuHandler(weatherCondition)
                            binding.nextSixteenDaysBtn.isEnabled = true
                            binding.lastSixteenDaysBtn.isEnabled = true
                        }
                        accessActivityUI.hideLoadingDialog()
                    }
                }

            }

        }
    }

    /** API call to get weather data for the next 16 days weather data **/
    private fun getNextSixteenAndCurrentDayWeather() {
        lifecycleScope.launch(Dispatchers.IO) {
            // get next 16 weather data for given location from the API
            nextSixteenDaysAndCurrentWeatherViewModel.getLocationWeather(locationName, unitGroup, isFavourite)
        }
    }

    /** Observe for successful next 16 days weather data response **/
    private fun observeNextSixteenDaysAndCurrentWeatherViewModel() {
        nextSixteenDaysAndCurrentWeatherViewModel.nextSixteenDaysAndCurrentWeatherCondition.observe(viewLifecycleOwner) { weatherConditions ->
            // NOTE : BEING CALLED ONCE MORE AFTER ROTATION/DARK MODE CHANGE IF VIEW MODEL LIVEDATA IS NOT NULL
            if(weatherConditions != null) {

                lifecycleScope.launch(Dispatchers.IO){
                    // update database with the next 16 days weather data for given address
                    WeatherTrackingApplication.weatherConditionDao.updateNextDays(weatherConditions.days, weatherConditions.address)
                }

                // fill recycler view with the next 16 days weather data
                dayRecycleViewAndAdapterHandler(weatherConditions.days.toMutableList(), unitGroup, 0)
                binding.nextSixteenDaysBtn.isEnabled = true
                accessActivityUI.hideLoadingDialog()
            }
        }

        nextSixteenDaysAndCurrentWeatherViewModel.nextSixteenDaysAndCurrentWeatherConditionError.observe(viewLifecycleOwner) { e ->
            accessActivityUI.hideLoadingDialog()
            errorProvider.errorHandler(e)
        }
    }


    /** API call to get weather data for the last 16 days weather data **/
    private fun getLastSixteenDaysWeather() {
        lifecycleScope.launch(Dispatchers.IO) {
            // get last 16 weather data for given location from the API
            lastSixteenDaysWeatherViewModel.getLocationWeather(locationName, unitGroup, isFavourite)
        }
    }

    /** Observe for successful last 16 days weather data response **/
    private fun observeLastSixteenDaysWeatherViewModel() {
        lastSixteenDaysWeatherViewModel.lastSixteenWeatherCondition.observe(viewLifecycleOwner) { weatherConditions ->
            // NOTE : BEING CALLED ONCE MORE AFTER ROTATION/DARK MODE CHANGE IF VIEW MODEL LIVEDATA IS NOT NULL
            if(weatherConditions != null)
            {
                // update database with the last 16 days weather data for given address
                lifecycleScope.launch(Dispatchers.IO){
                    WeatherTrackingApplication.weatherConditionDao.updateLastDays(weatherConditions.days, weatherConditions.address)
                }

                // fill recycler view with the last 16 days weather data
                dayRecycleViewAndAdapterHandler(weatherConditions.days.toMutableList(), unitGroup, 0, reverseLayout = true)
                binding.lastSixteenDaysBtn.isEnabled = true
                accessActivityUI.hideLoadingDialog()
            }
        }

        lastSixteenDaysWeatherViewModel.lastSixteenWeatherConditionError.observe(viewLifecycleOwner) { e ->
            accessActivityUI.hideLoadingDialog()
            errorProvider.errorHandler(e)
        }
    }


    /** Listens for current location weather updates from the activity **/
    override fun currentWeatherCondition(weatherCondition: WeatherCondition) {
        if(type == CURRENT_LOCATION)
        {
            locationName = weatherCondition.address
            updateUI(weatherCondition)

            // update database with the next current location weather data
            lifecycleScope.launch(Dispatchers.IO) {
                WeatherTrackingApplication.weatherConditionDao.insertOrUpdateFavourite(weatherCondition)
            }

            setupToolbarMenuHandler(weatherCondition)
            // remember current location weather data
            todayWeatherDetails = weatherCondition

            binding.lastSixteenDaysBtn.isEnabled = true
            binding.nextSixteenDaysBtn.isEnabled = true
        }
        accessActivityUI.hideLoadingDialog()
    }

    /** API call to get weather data for one day weather data **/
    private fun getOneDayWeather(
        date: String,
        unitGroup: String,
        isFavourite: Boolean = false,
    ) {
        // get one day weather data for current location from the API
        lifecycleScope.launch(Dispatchers.IO) {
            oneDayWeatherViewModel.getLocationWeather(
                locationName,
                date,
                unitGroup,
                isFavourite,
            )
        }
    }

    /** Observe for successful current weather data response **/
    private fun observeOneDayWeatherViewModel() {
        oneDayWeatherViewModel.oneDayWeatherCondition.observe(viewLifecycleOwner) { weatherCondition ->
            // NOTE : BEING CALLED ONCE MORE AFTER ROTATION/DARK MODE CHANGE IF VIEW MODEL LIVEDATA IS NOT NULL
            if(weatherCondition != null)
            {
                // update UI with
                updateUI(weatherCondition)

                if (binding.tomorrowBtn.text.equals(getString(R.string.show_tomorrow))) {
                    lifecycleScope.launch(Dispatchers.IO)
                    {
                        // insert or update weather data for today
                        WeatherTrackingApplication.weatherConditionDao.insertOrUpdateFavourite(weatherCondition)
                    }
                    // remember current location weather data
                    todayWeatherDetails = weatherCondition
                    setupToolbarMenuHandler(weatherCondition)
                }
                else{

                    lifecycleScope.launch(Dispatchers.IO)
                    {
                        // update weather data for tomorrow
                        WeatherTrackingApplication.weatherConditionDao.updateTomorrow(weatherCondition, weatherCondition.address)
                    }
                    // don't want to add tomorrow to the favourites, only today
                    setupToolbarMenuHandler(todayWeatherDetails)
                }
                accessActivityUI.hideLoadingDialog()
            }
        }

        oneDayWeatherViewModel.oneDayWeatherConditionError.observe(viewLifecycleOwner) { e ->
            accessActivityUI.hideLoadingDialog()
            errorProvider.errorHandler(e)
        }
    }

    /** Update UI with one day weather data **/
    private fun updateUI(weatherCondition: WeatherCondition) {
        val location = weatherCondition.address
        val temp: String
        val conditions: String
        var currHour: String
        if (weatherCondition.currentConditions != null) {
            //TODAY
            temp = weatherCondition.currentConditions?.temp.toString()
            conditions = weatherCondition.currentConditions?.conditions.toString()
            currHour = weatherCondition.currentConditions?.datetime?.take(2).toString()
            weatherCondition.unitGroup?.let { unitGroup ->
                weatherCondition.currentConditions!!.sunriseEpoch?.let { sunriseEpoch ->
                    weatherCondition.currentConditions!!.sunsetEpoch?.let { sunsetEpoch ->
                        setDetailsToUI(weatherCondition.currentConditions!!, unitGroup, sunriseEpoch, sunsetEpoch)
                    }
                }
            }
        } else {
            //TOMORROW
            temp = weatherCondition.days[0].temp.toString()
            conditions = weatherCondition.days[0].conditions.toString()
            currHour = "00"
            weatherCondition.unitGroup?.let { unitGroup -> weatherCondition.days[0].sunriseEpoch?.let { sunriseEpoch ->
                weatherCondition.days[0].sunsetEpoch?.let { sunsetEpoch ->
                    setDetailsToUI(weatherCondition.days[0], unitGroup, sunriseEpoch, sunsetEpoch)
                }
            } }
        }
        if (currHour[0] == '0') {
            currHour = currHour[1].toString()
        }
        val hourIndex = currHour.toInt()

        handleButtonVisibilityAfterNewOneDayWeather()

        val symbol = if (weatherCondition.unitGroup == "metric") "C" else "F"
        accessActivityUI.getToolbar().title = "$location $temp°$symbol, $conditions"
        binding.sunriseTv.text = getString(R.string.sunrise, weatherCondition.days[0].sunrise)
        binding.sunsetTv.text = getString(R.string.sunset, weatherCondition.days[0].sunset)
        if (weatherCondition.isFavourite)
            accessActivityUI.getToolbar().menu[0].setIcon(R.drawable.ic_heart_red)
        accessActivityUI.getToolbar().menu[0].isEnabled = true
        accessActivityUI.getToolbar().menu[1].isEnabled = true

        // fill recyclerview with hourly weather data
        hourRecycleViewAndAdapterHandler(weatherCondition.days[0], hourIndex)
    }

    /** Handles toolbar menu items behaviour **/
    private fun setupToolbarMenuHandler(weatherCondition: WeatherCondition) {
        accessActivityUI.getToolbar().setOnMenuItemClickListener {
            when (it.itemId) {
                // when favourite menu item was clicked
                R.id.menu_toolbar_favorite ->

                    if (weatherCondition.isFavourite) {
                        // location weather data is already favourite
                        // delete from database, make it non-favourite
                        lifecycleScope.launch(Dispatchers.IO){
                            WeatherTrackingApplication.weatherConditionDao.delete(weatherCondition)
                            weatherCondition.isFavourite = false
                            isFavourite = false
                        }
                        // replace icon
                        it.setIcon(R.drawable.ic_heart_white)
                        Log.i(
                            TAG,
                            "Today weather was deleted from favourite list!"
                        )
                        showSnackBar("Location: " + weatherCondition.address + " was successfully removed from the favourites!")            // WAS locationName
                    } else {
                        // location weather data is not favourite
                        // make it favourite
                        weatherCondition.isFavourite = true
                        isFavourite = true
                        // add location weather data to the database
                        lifecycleScope.launch(Dispatchers.IO) {
                            WeatherTrackingApplication.weatherConditionDao.insertOrUpdateFavourite(
                                weatherCondition
                            )
                        }
                        Log.i(
                            TAG,
                            "Today weather ${weatherCondition.address} was added to favourite list!"
                        )
                        // replace icon
                        it.setIcon(R.drawable.ic_heart_red)
                        showSnackBar("Location: " + weatherCondition.address + " was successfully added to the favourites!")            // WAS locationName
                    }

                R.id.menu_toolbar_reload ->
                    if (locationName.isNotEmpty()) {
                        // force reload
                        accessActivityUI.showLoadingDialog()
                        updateData()
                        showSnackBar("Location: ${weatherCondition.address} was successfully reloaded!")
                        Log.i(TAG, "${weatherCondition.address} weather was reloaded!")
                    }
                else -> {
                    Log.e(TAG, "Undefined item")
                }
            }
            true
        }
    }

    private fun handleButtonVisibilityAfterNewOneDayWeather() {
        binding.lastSixteenDaysBtn.visibility = VISIBLE
        binding.nextSixteenDaysBtn.visibility = VISIBLE
        binding.tomorrowBtn.visibility = VISIBLE
        binding.tomorrowBtn.isEnabled = true
    }
}