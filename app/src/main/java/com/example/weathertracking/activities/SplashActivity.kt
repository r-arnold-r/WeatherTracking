package com.example.weathertracking.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.weathertracking.databinding.ActivitySplashBinding

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val TAG = "SplashActivity"
    private lateinit var binding: ActivitySplashBinding
    private lateinit var view: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = getBinding()
        view = getView()
        setContentView(view)

        binding.splashContainer.splashAnimateAndOpenMainActivity()
    }

    private fun getBinding() = ActivitySplashBinding.inflate(layoutInflater)
    private fun getView() = binding.root

    private fun androidx.constraintlayout.widget.ConstraintLayout.splashAnimateAndOpenMainActivity() {
        binding.splashContainer.alpha = 0F
        binding.splashContainer.animate().setDuration(1500).alpha(1F).withEndAction {
            Intent(context, MainActivity::class.java).startActivityWithFadeAndFinish()
        }
    }

    private fun Intent.startActivityWithFadeAndFinish() {
        startActivity(this)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
