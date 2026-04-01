package com.example.recipegenie.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.recipegenie.LoginActivity
import com.example.recipegenie.PreferencesActivity
import com.example.recipegenie.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        loadUserProfile(view)
        loadUserStats(view)
        setupClickListeners(view)
    }

    private fun loadUserProfile(view: View) {
        val user = auth.currentUser ?: return
        view.findViewById<TextView>(R.id.tv_profile_name).text =
            user.displayName ?: "Chef"
        view.findViewById<TextView>(R.id.tv_profile_email).text = user.email ?: ""

        val avatarView = view.findViewById<ShapeableImageView>(R.id.iv_profile_avatar)
        if (user.photoUrl != null) {
            Glide.with(this).load(user.photoUrl).circleCrop().into(avatarView)
        } else {
            // Show initials
            val initials = (user.displayName ?: "U").take(2).uppercase()
            avatarView.contentDescription = initials
        }
    }

    private fun loadUserStats(view: View) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            view.findViewById<TextView>(R.id.tv_stat_cooked).text =
                doc.getLong("recipesCooked")?.toString() ?: "0"
            view.findViewById<TextView>(R.id.tv_stat_streak).text =
                "${doc.getLong("dayStreak") ?: 0}🔥"
        }
        db.collection("users").document(uid)
            .collection("saved_recipes").get()
            .addOnSuccessListener { snap ->
                view.findViewById<TextView>(R.id.tv_stat_saved).text =
                    snap.size().toString()
            }
    }

    private fun setupClickListeners(view: View) {
        view.findViewById<View>(R.id.row_edit_profile).setOnClickListener {
            // Navigate to edit profile screen
        }

        view.findViewById<View>(R.id.row_dietary).setOnClickListener {
            startActivity(Intent(requireContext(), PreferencesActivity::class.java))
        }

        view.findViewById<View>(R.id.row_history).setOnClickListener {
            // Navigate to history screen
        }

        view.findViewById<View>(R.id.row_help).setOnClickListener {
            // Open help URL or screen
        }

        view.findViewById<View>(R.id.row_sign_out).setOnClickListener {
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
}
