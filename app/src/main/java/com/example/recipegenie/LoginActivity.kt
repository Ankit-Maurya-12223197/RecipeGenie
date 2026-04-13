// LoginActivity.kt
package com.example.recipegenie

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser

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
    private lateinit var btnBack: View
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvSignUp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        setupGoogleSignIn()
        bindViews()
        prefillEmailFromIntent()
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
        btnBack = findViewById(R.id.btn_back)
        tvForgotPassword = findViewById(R.id.tv_forgot_password)
        tvSignUp = findViewById(R.id.tv_sign_up)
    }

    private fun prefillEmailFromIntent() {
        val prefillEmail = intent.getStringExtra("prefill_email").orEmpty()
        if (prefillEmail.isNotBlank()) {
            etEmail.setText(prefillEmail)
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        tvForgotPassword.setOnClickListener { showForgotPasswordDialog() }
        btnSignIn.setOnClickListener { attemptEmailSignIn() }

        btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }

        tvSignUp.setOnClickListener {
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
            .addOnSuccessListener { result ->
                result.user?.reload()
                    ?.addOnSuccessListener {
                        handlePostLogin(result.user)
                    }
                    ?.addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(this, "Couldn't verify account status. Try again.", Toast.LENGTH_LONG).show()
                    }
            }
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
            .addOnSuccessListener { result ->
                handlePostLogin(result.user)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, e.localizedMessage ?: "Auth failed", Toast.LENGTH_LONG).show()
            }
    }

    private fun handlePostLogin(user: FirebaseUser?) {
        if (user == null) {
            showLoading(false)
            Toast.makeText(this, "Sign in failed", Toast.LENGTH_LONG).show()
            return
        }

        val hasPasswordProvider = user.providerData.any { it.providerId == "password" }
        if (hasPasswordProvider && !user.isEmailVerified) {
            auth.signOut()
            showLoading(false)
            Toast.makeText(
                this,
                "First confirm your email using the verification link, then sign in.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        checkPreferencesAndNavigate()
    }

    private fun showForgotPasswordDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.email_address)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(etEmail.text?.toString()?.trim().orEmpty())
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Reset password")
            .setMessage("Enter your email address to receive a password reset link.")
            .setView(input)
            .setPositiveButton("Send link") { _, _ ->
                val email = input.text.toString().trim()
                sendPasswordReset(email)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            tilEmail.error = "Enter your email first"
            Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Enter a valid email address"
            Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }

        tilEmail.error = null
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                etEmail.setText(email)
                Toast.makeText(
                    this,
                    "Password reset link sent to $email",
                    Toast.LENGTH_LONG
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    e.localizedMessage ?: "Couldn't send reset email",
                    Toast.LENGTH_LONG
                ).show()
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
