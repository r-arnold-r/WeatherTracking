package com.example.weathertracking.interfaces

import androidx.fragment.app.Fragment

interface IReplaceFragment {
    fun replaceFragment(fragment: Fragment, tag: String = "")
}