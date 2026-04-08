// LoginActivity.kt
package com.example.recipegenie

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSignIn: MaterialButton
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var progressLogin: CircularProgressIndicator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        setupGoogleSignIn()
        bindViews()
        setupClickListeners()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun bindViews() {
        tilEmail = findViewById(R.id.til_email)
        tilPassword = findViewById(R.id.til_password)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        btnSignIn = findViewById(R.id.btn_sign_in)
        btnGoogleSignIn = findViewById(R.id.btn_google_sign_in)
        progressLogin = findViewById(R.id.progress_login)
    }

    private fun setupClickListeners() {
        // Back
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Forgot password
        findViewById<TextView>(R.id.tv_forgot_password).setOnClickListener {
            val email = etEmail.text.toString()
            if (email.isEmpty()) {
                tilEmail.error = "Enter your email first"
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Toast.makeText(this, "Reset email sent!", Toast.LENGTH_SHORT).show()
                }
        }

        // Sign in with email
        btnSignIn.setOnClickListener { attemptEmailSignIn() }

        // Google sign in
        btnGoogleSignIn.setOnClickListener {
            val  signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        // Navigate to sign up
        findViewById<TextView>(R.id.tv_sign_up).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun attemptEmailSignIn() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        // Validate inputs
        var hasError = false
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Enter a valid email address"
            hasError = true
        } else {
            tilEmail.error = null
        }
        if (password.isEmpty() || password.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            hasError = true
        } else {
            tilPassword.error = null
        }
        if (hasError) return

        showLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { checkPreferencesAndNavigate() }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, e.localizedMessage ?: "Sign in failed", Toast.LENGTH_LONG).show()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        showLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { checkPreferencesAndNavigate() }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, e.localizedMessage ?: "Auth failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkPreferencesAndNavigate() {
        showLoading(false)
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    private fun showLoading(show: Boolean) {
        progressLogin.visibility = if (show) View.VISIBLE else View.GONE
        btnSignIn.isEnabled = !show
        btnGoogleSignIn.isEnabled = !show
    }
}
