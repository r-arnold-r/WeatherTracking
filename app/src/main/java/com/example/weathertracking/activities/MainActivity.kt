package com.example.weathertracking.activities

import android.content.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.TransitionDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.weathertracking.R
import com.example.weathertracking.activities.MainActivity.MainActivityConstants.NAVIGATION_BUTTON_ANIMATION_DURATION
import com.example.weathertracking.application.WeatherTrackingApplication
import com.example.weathertracking.databinding.ActivityMainBinding
import com.example.weathertracking.extensionfunctions.ExtensionFunctions.createAlertDialog
import com.example.weathertracking.extensionfunctions.ExtensionFunctions.getAddress
import com.example.weathertracking.extensionfunctions.ExtensionFunctions.showSnackBar
import com.example.weathertracking.fragments.LocationDetailsFragment
import com.example.weathertracking.fragments.LocationSearchFragment
import com.example.weathertracking.fragments.LocationsFragment
import com.example.weathertracking.interfaces.*
import com.example.weathertracking.repository.Repository
import com.example.weathertracking.utils.*
import com.example.weathertracking.utils.Constants.METRIC_UNIT_GROUP
import com.example.weathertracking.utils.Utility.setBackground
import com.example.weathertracking.viewmodels.CurrentDayWeatherViewModel
import com.example.weathertracking.viewmodels.CurrentDayWeatherViewModelFactory
import com.example.weathertracking.viewmodels.MainActivityViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : BaseGPSActivity(), IReplaceFragment, IAccessActivityUI, IUpdateDataListener, IGpsStatusChange {

    object MainActivityConstants {
        const val NAVIGATION_BUTTON_ANIMATION_DURATION = 300
    }
    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding

    private lateinit var locationsButtonTransition: TransitionDrawable
    private lateinit var currentLocationButtonTransition: TransitionDrawable
    private lateinit var currentDayWeatherViewModel: CurrentDayWeatherViewModel
    private lateinit var loadingDialog : AlertDialog
    private lateinit var networkConnection : NetworkConnection
    private lateinit var gpsReceiver : GpsReceiver
    private val mainActivityViewModel by viewModels<MainActivityViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //SharedPreferencesManager(this).clear()
        initializeLoadingDialog()
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        binding.locationsTbtn.setOnClickListener {
            locationsButtonClicked()
        }

        binding.currentLocationTbtn.setOnClickListener {
            currentLocationButtonClicked()
        }

        binding.locationSearchFab.setOnClickListener {
            locationSearchButtonCLicked()
        }

        WeatherTrackingApplication.addToUpdateListeners(this)
        initializeButtonTransitions()
        initializeCurrentDayWeatherViewModelWithFactory()
        observeCurrentDayWeatherViewModel()
        initializeNetworkObserver()
        if(savedInstanceState == null) {
            mainActivityViewModel.background = AppCompatResources.getDrawable(this, R.drawable.default_bg)
            locationsButtonClicked()
            searchForCurrentLocation()
        }
        binding.mainContainerIv.background = mainActivityViewModel.background

        registerGpsReceiver()
        setContentView(view)
    }

    override fun onStart() {
        super.onStart()
        setSupportActionBar(binding.toolbar)
    }

    /** Registers Broadcast Receiver to listen for Location status changes **/
    private fun registerGpsReceiver() {
        val mIntentFilter = IntentFilter()
        mIntentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        gpsReceiver = GpsReceiver(this)
        registerReceiver(gpsReceiver, mIntentFilter)
    }

    override fun onStop() {
        super.onStop()
        loadingDialog.dismiss()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainActivityViewModel.wasUpdated = false

        WeatherTrackingApplication.removeFromUpdateListeners(this)

        try{
            unregisterReceiver(gpsReceiver)
        }
        catch (e : Exception){
            Log.i(TAG, "Gps receiver is already unregistered!")
        }

        // To disallow observer from using its value after activity reload
        currentDayWeatherViewModel.currentDayWeatherCondition.value = null
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.menu_toolbar_favorite -> showSnackBar("Not available!")
            R.id.menu_toolbar_reload -> showSnackBar("Not available!")
            else -> {
                showSnackBar("Not available!")
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /** Return to LocationsFragment from other fragments when back is pressed,
     * finish() activity if back is pressed in LocationsFragment and permission was given
     * from the user **/
    override fun onBackPressed() {
        if (supportFragmentManager.findFragmentByTag("LOCATIONSFRAGMENT") !is LocationsFragment) {
            locationsButtonClicked()
        } else {
            createAlertDialog("Press OK to close the application")
        }
    }

    /** Can replace fragment from fragments that implement IReplaceFragment **/
    override fun replaceFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainerFcw.id, fragment, tag).commit()
    }

    /** Update data every X (30) minutes, notify fragments **/
    override fun updateData() {
        updateCurrentData()
        for (fragment in supportFragmentManager.fragments) {
            if (fragment is IFragmentUpdateData) {
                fragment.updateData()
            }
        }
    }

    /** Called everytime when gps status has changed **/
    override fun gpsStatusChanged(isActivated: Boolean) {
        if(isActivated && !mainActivityViewModel.wasUpdated)
        {
            mainActivityViewModel.wasUpdated = true
            searchForCurrentLocation()
        }
        Log.d(TAG, "gpsStatusChanged: $isActivated")
    }

    /** Updating data with new location **/
    override fun currentLocationChanged(location: Location) {
        binding.currentLocationTbtn.isEnabled = true
        try{
            mainActivityViewModel.currentAddress =
                WeatherTrackingApplication.geocoder.getAddress(location.latitude, location.longitude)
        }
        catch(e : Exception){
            ErrorProvider(this, this.findViewById(android.R.id.content)).errorHandler(e)
        }
        finally {
            hideLoadingDialog()
        }

        updateCurrentData()
    }

    override fun currentLocationErrorChanged(locationError: String) {
        hideLoadingDialog()
    }

    override fun hideLocationSearchButton() {
        binding.locationSearchFab.hide()
    }

    override fun showLocationSearchButton() {
        binding.locationSearchFab.show()
    }

    override fun hideTopNavigationLayout() {
        binding.topNavigationLl.visibility = GONE
    }

    override fun showTopNavigationLayout() {
        binding.topNavigationLl.visibility = VISIBLE
    }

    override fun hideSearchView() {
        binding.currentLocationSv.visibility = GONE
    }

    override fun showSearchView() {
        binding.currentLocationSv.visibility = VISIBLE
    }

    override fun hideToolbar() {
        binding.toolbar.visibility = GONE
    }

    override fun showToolbar() {
        binding.toolbar.visibility = VISIBLE
    }

    override fun getToolbar(): androidx.appcompat.widget.Toolbar {
        return binding.toolbar
    }

    override fun getSearchView(): SearchView {
        return binding.currentLocationSv
    }

    override fun showLoadingDialog() {
        loadingDialog.show()
    }

    override fun hideLoadingDialog() {
        loadingDialog.hide()
    }

    /** Listen for network connection status changes **/
    private fun initializeNetworkObserver()
    {
        networkConnection = NetworkConnection(applicationContext)
        networkConnection.observe(this@MainActivity) { isConnected ->
            if (isConnected) {
                binding.connectionModeTv.visibility = GONE
                if(WeatherTrackingApplication.isGPSEnabled)
                {
                    // update current location
                    searchForCurrentLocation()
                    mainActivityViewModel.wasUpdated = true
                }
            } else {
                binding.connectionModeTv.visibility = VISIBLE
            }
            // update online state of the Application
            WeatherTrackingApplication.isOnline = isConnected

            // notify fragments of the internet status change
            lifecycleScope.launch(Dispatchers.Default){
                for (fragment in supportFragmentManager.fragments) {
                    if (fragment is IInternetStatusChange) {
                        fragment.internetStatusChanged(isConnected)
                    }
                }
            }
        }
    }

    private fun initializeCurrentDayWeatherViewModelWithFactory() {
        // creates CurrentWeatherViewModel with factory
        val currentDayWeatherViewModelFactory = CurrentDayWeatherViewModelFactory(Repository())
        currentDayWeatherViewModel = ViewModelProvider(
            this,
            currentDayWeatherViewModelFactory
        )[CurrentDayWeatherViewModel::class.java]
    }

    /** Used to notify users for longer operations, that can't be cancelled **/
    private fun initializeLoadingDialog()
    {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        builder.setView(R.layout.layout_loading_dialog)
        loadingDialog = builder.create()
        loadingDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        mainActivityViewModel.loadingDialog = loadingDialog
    }

    /** Used to highlight with an underline which location toggle button is selected **/
    private fun initializeButtonTransitions() {
        locationsButtonTransition = binding.locationsTbtn.background as TransitionDrawable
        currentLocationButtonTransition =
            binding.currentLocationTbtn.background as TransitionDrawable
    }


    private fun locationsButtonClicked() {
        binding.locationsTbtn.isChecked = true
        binding.currentLocationTbtn.isChecked = false

        locationsButtonTransition.startTransition(NAVIGATION_BUTTON_ANIMATION_DURATION)
        currentLocationButtonTransition.resetTransition()

        binding.currentLocationTbtn.isEnabled = true
        binding.locationsTbtn.isEnabled = false

        replaceFragment(LocationsFragment(), "LOCATIONSFRAGMENT")
    }

    private fun currentLocationButtonClicked() {
        loadingDialog.show()

        binding.locationsTbtn.isChecked = false
        binding.currentLocationTbtn.isChecked = true
        locationsButtonTransition.resetTransition()
        currentLocationButtonTransition.startTransition(NAVIGATION_BUTTON_ANIMATION_DURATION)

        binding.currentLocationTbtn.isEnabled = false
        binding.locationsTbtn.isEnabled = true

        replaceFragment(LocationDetailsFragment())
    }

    private fun locationSearchButtonCLicked() {
        replaceFragment(LocationSearchFragment())
    }

    private fun updateCurrentData()
    {
        lifecycleScope.launch(Dispatchers.IO)
        {
            // in case the weather data is not saved in the database, set default parameters
            var unitGroup = METRIC_UNIT_GROUP
            var isFavourite = false

            // get weather data from the database with current address
            val weatherCondition = WeatherTrackingApplication.weatherConditionDao.getWeatherCondition(mainActivityViewModel.currentAddress)
            if (weatherCondition != null) {
                // weather data was found, reassign parameters
                unitGroup = weatherCondition.unitGroup.toString()
                isFavourite = weatherCondition.isFavourite
            }

            // send API call to update weather data at current address
            getCurrentDayWeather(mainActivityViewModel.currentAddress, Constants.TODAY, unitGroup, isFavourite)
        }
    }

    /** API call to get weather data for current day weather data**/
    private fun getCurrentDayWeather(
        currentPosition : String,
        date: String,
        unitGroup: String,
        isFavourite: Boolean = false,
    ) {
        // get current weather data for current location from the API
        lifecycleScope.launch(Dispatchers.IO) {
            currentDayWeatherViewModel.getLocationWeather(
                currentPosition,
                date,
                unitGroup,
                isFavourite,
            )
        }
    }

    /** Observe for successful current weather data response **/
    private fun observeCurrentDayWeatherViewModel() {
        currentDayWeatherViewModel.currentDayWeatherCondition.observe(this) { weatherCondition ->
            lifecycleScope.launch(Dispatchers.IO){
                // NOTE : BEING CALLED ONCE MORE AFTER ROTATION/DARK MODE CHANGE IF VIEW MODEL LIVEDATA IS NOT NULL
                if(weatherCondition != null) {
                    // Update database with new weather condition data
                    WeatherTrackingApplication.weatherConditionDao.insertOrUpdate(weatherCondition)

                    val icon: String = weatherCondition.currentConditions?.icon.toString()

                    // Select background to display aligned with current weather
                    val newBackground = Utility.getBackground(weatherCondition)

                    // Send new weather data for current weather data listeners
                    withContext(Dispatchers.Main) {
                        for (fragment in supportFragmentManager.fragments) {
                            if (fragment is ICurrentLocationWeatherDataObserver) {
                                fragment.currentWeatherCondition(weatherCondition)
                            }
                        }

                        // set previous contentDescription to deny the background change to the currently displayed
                        mainActivityViewModel.imageViewDescription = binding.mainContainerIv.contentDescription.toString()

                        if(setBackground(icon, binding.mainContainerIv, newBackground))
                        {
                            mainActivityViewModel.background = newBackground
                        }

                        // save currently displayed background description
                        binding.mainContainerIv.contentDescription = icon


                        Log.d(TAG, "Changed background with location: $weatherCondition")

                        hideLoadingDialog()
                        mainActivityViewModel.wasUpdated = false
                    }
                }
            }
        }
        currentDayWeatherViewModel.currentDayWeatherConditionError.observe(this) { e ->
            ErrorProvider(this, this.findViewById(android.R.id.content)).errorHandler(e)
            mainActivityViewModel.wasUpdated = false
        }
    }
}