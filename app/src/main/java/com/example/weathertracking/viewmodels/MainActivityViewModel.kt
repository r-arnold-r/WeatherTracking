package com.example.weathertracking.viewmodels
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModel

class MainActivityViewModel : ViewModel() {
    lateinit var loadingDialog : AlertDialog

    // to store which background was previously loaded
    var imageViewDescription : String = ""

    var background : Drawable? = null
    var currentAddress : String = ""

    // to block using gps status while awaiting for new location
    var wasUpdated = false
}