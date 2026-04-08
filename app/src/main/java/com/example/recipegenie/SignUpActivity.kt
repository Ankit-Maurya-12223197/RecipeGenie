// SignUpActivity.kt
package com.example.recipegenie

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    companion object {
        private const val RC_SIGN_IN = 9001
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    // Views
    private lateinit var tilName: TextInputLayout
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var etName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var cbTerms: CheckBox
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var btnGoogleSignUp: MaterialButton
    private lateinit var progressSignup: CircularProgressIndicator
    private lateinit var progressPasswordStrength: LinearProgressIndicator
    private lateinit var tvPasswordStrength: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        setupGoogleSignIn()
        bindViews()
        setupTermsText()
        setupPasswordStrengthWatcher()
        setupClickListeners()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun bindViews() {
        tilName = findViewById(R.id.til_name)
        tilEmail = findViewById(R.id.til_email)
        tilPassword = findViewById(R.id.til_password)
        tilConfirmPassword = findViewById(R.id.til_confirm_password)
        etName = findViewById(R.id.et_name)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        cbTerms = findViewById(R.id.cb_terms)
        btnCreateAccount = findViewById(R.id.btn_create_account)
        btnGoogleSignUp = findViewById(R.id.btn_google_sign_up)
        progressSignup = findViewById(R.id.progress_signup)
        progressPasswordStrength = findViewById(R.id.progress_password_strength)
        tvPasswordStrength = findViewById(R.id.tv_password_strength)
    }

    private fun setupTermsText() {
        // Render HTML-formatted terms text on the checkbox's sibling TextView
        val tvTerms = findViewById<TextView>(R.id.tv_terms_text)
        tvTerms?.text = Html.fromHtml(
            "I agree to the <font color='#F97316'><b>Terms of Service</b></font> " +
                    "and <font color='#F97316'><b>Privacy Policy</b></font>",
            Html.FROM_HTML_MODE_LEGACY
        )
        tvTerms?.setOnClickListener {
            // Optionally open terms URL
        }
    }

    // ── Password Strength ─────────────────────────────────────────────────────

    private fun setupPasswordStrengthWatcher() {
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updatePasswordStrength(s?.toString() ?: "")
            }
        })
    }

    private fun updatePasswordStrength(password: String) {
        if (password.isEmpty()) {
            progressPasswordStrength.progress = 0
            tvPasswordStrength.text = ""
            return
        }

        var score = 0
        if (password.length >= 8) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { "!@#\$%^&*()_+-=[]{}|;':\",./<>?".contains(it) }) score++

        val (progress, label, colorRes) = when (score) {
            1 -> Triple(25, "Weak", R.color.red_text)
            2 -> Triple(50, "Fair", R.color.yellow_text)
            3 -> Triple(75, "Good", R.color.orange_primary)
            4 -> Triple(100, "Strong", R.color.green_text)
            else -> Triple(10, "Too short", R.color.red_text)
        }

        progressPasswordStrength.progress = progress
        progressPasswordStrength.setIndicatorColor(getColor(colorRes))
        tvPasswordStrength.text = label
        tvPasswordStrength.setTextColor(getColor(colorRes))
    }

    // ── Click Listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        // Back
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        // Create account with email
        btnCreateAccount.setOnClickListener { attemptSignUp() }

        // Google sign up
        btnGoogleSignUp.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }

        // Navigate back to log
        findViewById<TextView>(R.id.tv_sign_in).setOnClickListener {
            // Go back to LoginActivity — don't stack a new one
            finish()
        }
    }

    // ── Sign Up Logic ─────────────────────────────────────────────────────────

    private fun attemptSignUp() {
        val name = etName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        if (!validateInputs(name, email, password, confirmPassword)) return

        showLoading(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                // Set display name on Firebase Auth profile
                val profileUpdate = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                result.user?.updateProfile(profileUpdate)

                // Create user document in Firestore
                createUserDocument(result.user?.uid ?: "", name, email)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                val message = when {
                    e.message?.contains("email address is already in use") == true ->
                        "This email is already registered. Try signing in."
                    e.message?.contains("weak password") == true ->
                        "Password is too weak. Use at least 6 characters."
                    else -> e.localizedMessage ?: "Sign up failed. Please try again."
                }
                tilEmail.error = if (e.message?.contains("email") == true) message else null
                tilPassword.error = if (e.message?.contains("password") == true) message else null
                if (tilEmail.error == null && tilPassword.error == null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun validateInputs(
        name: String,
        email: String,
        password: String,
        confirmPassword: String
    ): Boolean {
        var isValid = true

        // Name
        if (name.isEmpty() || name.length < 2) {
            tilName.error = "Enter your full name"
            isValid = false
        } else {
            tilName.error = null
        }

        // Email
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Enter a valid email address"
            isValid = false
        } else {
            tilEmail.error = null
        }

        // Password
        if (password.isEmpty() || password.length < 6) {
            tilPassword.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            tilPassword.error = null
        }

        // Confirm password
        if (confirmPassword != password) {
            tilConfirmPassword.error = "Passwords do not match"
            isValid = false
        } else {
            tilConfirmPassword.error = null
        }

        // Terms
        if (!cbTerms.isChecked) {
            Toast.makeText(this, "Please accept the Terms of Service", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun createUserDocument(uid: String, name: String, email: String) {
        val userDoc = hashMapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "dietType" to "",
            "cuisines" to emptyList<String>(),
            "allergies" to emptyList<String>(),
            "recipesCooked" to 0,
            "dayStreak" to 0,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(uid)
            .set(userDoc)
            .addOnSuccessListener {
                showLoading(false)
                navigateToHome()
            }
            .addOnFailureListener {
                // Doc creation failed but auth succeeded — still navigate
                showLoading(false)
                navigateToHome()
            }
    }

    // ── Google Sign-Up ────────────────────────────────────────────────────────

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                showLoading(false)
                Toast.makeText(this, "Google sign-up failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        showLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user ?: return@addOnSuccessListener
                // Check if this is a new user
                val isNewUser = result.additionalUserInfo?.isNewUser == true
                if (isNewUser) {
                    createUserDocument(
                        uid = user.uid,
                        name = user.displayName ?: "",
                        email = user.email ?: ""
                    )
                } else {
                    // Existing user — go to home directly
                    showLoading(false)
                    navigateToHome()
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, e.localizedMessage ?: "Google auth failed", Toast.LENGTH_LONG).show()
            }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private fun navigateToHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun showLoading(show: Boolean) {
        progressSignup.visibility = if (show) View.VISIBLE else View.GONE
        btnCreateAccount.isEnabled = !show
        btnGoogleSignUp.isEnabled = !show
        btnCreateAccount.text = if (show) "" else getString(R.string.create_account)
    }
}
