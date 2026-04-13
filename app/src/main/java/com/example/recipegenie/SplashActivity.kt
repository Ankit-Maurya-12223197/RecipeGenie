// SplashActivity.kt
package com.example.recipegenie

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseUser
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val handler = Handler(Looper.getMainLooper())
    private val navigateRunnable = Runnable {
        val destination = if (canAutoLogin(auth.currentUser)) {
            MainActivity::class.java
        } else {
            LoginActivity::class.java
        }
        startActivity(Intent(this, destination))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        handler.postDelayed(navigateRunnable, 3000)
    }

    private fun canAutoLogin(user: FirebaseUser?): Boolean {
        if (user == null) return false
        val hasPasswordProvider = user.providerData.any { it.providerId == "password" }
        return !hasPasswordProvider || user.isEmailVerified
    }


    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(navigateRunnable)
    }
}
