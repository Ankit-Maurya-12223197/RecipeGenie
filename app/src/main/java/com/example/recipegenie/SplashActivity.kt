// SplashActivity.kt
package com.example.recipegenie

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        auth = FirebaseAuth.getInstance()

        setupAnimations()
        setupClickListeners()

        // Auto-navigate if user is already logged in
        Handler(Looper.getMainLooper()).postDelayed({
            if (auth.currentUser != null) {
                navigateToHome()
            }
        }, 1500)
    }

    private fun setupAnimations() {
        val logoCard = findViewById<CardView>(R.id.cv_logo)
        val btnGetStarted = findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_get_started)
        val tvSignIn = findViewById<TextView>(R.id.tv_sign_in_link)

        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up)

        logoCard.startAnimation(fadeIn)
        btnGetStarted.startAnimation(slideUp)
        tvSignIn.startAnimation(slideUp)
    }

    private fun setupClickListeners() {
        // "Get Started" → Sign Up (new users)
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_get_started)
            .setOnClickListener {
                startActivity(Intent(this, SignUpActivity::class.java))
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }

        // "Already have an account? Sign in" → Login (returning users)
        findViewById<TextView>(R.id.tv_sign_in_link).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
    }

    private fun navigateToHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
