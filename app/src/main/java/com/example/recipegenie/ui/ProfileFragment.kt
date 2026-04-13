package com.example.recipegenie.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.recipegenie.LoginActivity
import com.example.recipegenie.R
import com.example.recipegenie.data.FeedbackRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    companion object {
        private const val PREFS_NAME = "recipe_genie_prefs"
        private const val KEY_COOKED_DATE = "cooked_recipes_date"
        private const val KEY_COOKED_COUNT = "cooked_recipes_count"
        private const val KEY_STREAK_LAST_DATE = "cooked_streak_last_date"
        private const val KEY_STREAK_COUNT = "cooked_streak_count"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var avatarView: ShapeableImageView
    private lateinit var tvProfileName: TextView
    private lateinit var tvProfileEmail: TextView
    private lateinit var tvStatCooked: TextView
    private lateinit var tvStatStreak: TextView
    private lateinit var tvStatSaved: TextView
    private lateinit var rowEditProfile: View
    private lateinit var rowHistory: View
    private lateinit var rowFeedback: View
    private lateinit var rowSignOut: View
    private lateinit var rowDarkMode: View
    private lateinit var switchDarkMode: SwitchMaterial

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        bindViews(view)

        loadUserProfile()
        loadUserStats()
        setupThemeToggle()
        setupClickListeners()
    }

    private fun bindViews(view: View) {
        avatarView = view.findViewById(R.id.iv_profile_avatar)
        tvProfileName = view.findViewById(R.id.tv_profile_name)
        tvProfileEmail = view.findViewById(R.id.tv_profile_email)
        tvStatCooked = view.findViewById(R.id.tv_stat_cooked)
        tvStatStreak = view.findViewById(R.id.tv_stat_streak)
        tvStatSaved = view.findViewById(R.id.tv_stat_saved)
        rowEditProfile = view.findViewById(R.id.row_edit_profile)
        rowHistory = view.findViewById(R.id.row_history)
        rowFeedback = view.findViewById(R.id.row_feedback)
        rowSignOut = view.findViewById(R.id.row_sign_out)
        rowDarkMode = view.findViewById(R.id.row_dark_mode)
        switchDarkMode = view.findViewById(R.id.switch_dark_mode)
    }

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        tvProfileName.text = user.displayName ?: "Chef"
        tvProfileEmail.text = user.email ?: ""

        if (user.photoUrl != null) {
            Glide.with(this).load(user.photoUrl).circleCrop().into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_person)
        }
    }

    private fun loadUserStats() {
        val uid = auth.currentUser?.uid ?: return
        tvStatCooked.text = getTodayCookedCount().toString()
        tvStatStreak.text = "${getCurrentStreak()}🔥"

        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener
        }
        db.collection("users").document(uid)
            .collection("saved_recipes").get()
            .addOnSuccessListener { snap ->
                tvStatSaved.text = snap.size().toString()
            }
    }

    private fun getTodayCookedCount(): Int {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedDate = prefs.getString(KEY_COOKED_DATE, null)
        if (savedDate != today) {
            return 0
        }
        return prefs.getInt(KEY_COOKED_COUNT, 0)
    }

    private fun getCurrentStreak(): Int {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val lastCookedDate = prefs.getString(KEY_STREAK_LAST_DATE, null)
        val savedStreak = prefs.getInt(KEY_STREAK_COUNT, 0)

        return when (daysBetween(lastCookedDate, today)) {
            0, 1 -> savedStreak
            else -> 0
        }
    }

    private fun daysBetween(fromDate: String?, toDate: String): Int {
        if (fromDate.isNullOrBlank()) return Int.MAX_VALUE
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            val from = format.parse(fromDate) ?: return Int.MAX_VALUE
            val to = format.parse(toDate) ?: return Int.MAX_VALUE
            val diffMillis = startOfDay(to).time - startOfDay(from).time
            (diffMillis / (24 * 60 * 60 * 1000L)).toInt()
        } catch (_: ParseException) {
            Int.MAX_VALUE
        }
    }

    private fun startOfDay(date: Date): Date {
        return Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    private fun setupClickListeners() {
        rowEditProfile.setOnClickListener {
            showEditNameDialog()
        }

        rowHistory.setOnClickListener {
            // Navigate to history screen
        }

        rowFeedback.setOnClickListener {
            showFeedbackDialog()
        }

        rowSignOut.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign out?")
                .setMessage("You'll need to sign in again to access your saved recipes.")
                .setPositiveButton("Sign out") { _, _ ->
                    auth.signOut()
                    startActivity(Intent(requireContext(), LoginActivity::class.java))
                    requireActivity().finishAffinity()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun setupThemeToggle() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val savedValue = prefs.getBoolean(
            KEY_DARK_MODE_ENABLED,
            AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES
        )

        switchDarkMode.isChecked = savedValue
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        rowDarkMode.setOnClickListener {
            switchDarkMode.toggle()
        }
    }

    private fun showEditNameDialog() {
        val user = auth.currentUser ?: return
        val input = EditText(requireContext()).apply {
            setText(user.displayName.orEmpty())
            hint = "Enter your name"
            setSelection(text.length)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit profile")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isBlank()) {
                    Toast.makeText(requireContext(), "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                updateProfileName(newName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateProfileName(newName: String) {
        val user = auth.currentUser ?: return
        val profileUpdate = UserProfileChangeRequest.Builder()
            .setDisplayName(newName)
            .build()

        user.updateProfile(profileUpdate)
            .addOnSuccessListener {
                db.collection("users")
                    .document(user.uid)
                    .set(mapOf("name" to newName), SetOptions.merge())
                    .addOnCompleteListener {
                        if (!isAdded) return@addOnCompleteListener
                        tvProfileName.text = newName
                        Toast.makeText(requireContext(), "Name updated", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(
                    requireContext(),
                    "Couldn't update name",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showFeedbackDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Share your feedback"
            minLines = 4
            textSize = 15f
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Your feedback")
            .setView(input)
            .setPositiveButton("Send") { _, _ ->
                val feedback = input.text.toString().trim()
                if (feedback.isBlank()) {
                    Toast.makeText(requireContext(), "Please enter your feedback", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                sendFeedback(feedback)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendFeedback(feedback: String) {
        val user = auth.currentUser
        val userName = user?.displayName.orEmpty()
        val userEmail = user?.email.orEmpty()

        Toast.makeText(requireContext(), "Sending feedback...", Toast.LENGTH_SHORT).show()

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                FeedbackRepository.sendFeedback(userName, userEmail, feedback)
            }

            if (!isAdded) return@launch

            result.onSuccess {
                Toast.makeText(
                    requireContext(),
                    "Feedback sent successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    requireContext(),
                    error.localizedMessage ?: "Couldn't send feedback",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
