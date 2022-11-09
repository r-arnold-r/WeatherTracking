package com.example.weathertracking.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.weathertracking.databinding.FragmentErrorBinding
import com.example.weathertracking.interfaces.IHideLocationSearchButton
import com.example.weathertracking.interfaces.IHideSearchView
import com.example.weathertracking.interfaces.IHideToolbar
import com.example.weathertracking.interfaces.IHideTopNavigationLayout

class ErrorFragment : BaseFragment(), IHideToolbar, IHideTopNavigationLayout, IHideSearchView, IHideLocationSearchButton{

    companion object{
        const val BUNDLE_ERROR_MESSAGE = "errorMessage"
    }

    private val TAG = "ErrorFragment"
    private lateinit var binding: FragmentErrorBinding
    private lateinit var errorMessage: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accessActivityUI.hideLoadingDialog()
        errorMessage = arguments?.getString(BUNDLE_ERROR_MESSAGE).toString()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentErrorBinding.inflate(layoutInflater, container, false)

        // Inflate the layout for this fragment
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.errorTv.text = errorMessage
        binding.returnBtn.setOnClickListener {
            replaceFragmentListener.replaceFragment(LocationsFragment())
        }
    }

}