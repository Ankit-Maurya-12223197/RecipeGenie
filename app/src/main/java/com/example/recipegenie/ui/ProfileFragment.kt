package com.example.recipegenie.ui

import android.content.Intent
import android.util.Log
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.recipegenie.LoginActivity
import com.example.recipegenie.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata

class ProfileFragment : Fragment() {

    companion object {
        private const val TAG = "ProfileFragment"
        private const val STORAGE_BUCKET = "gs://recipegenie-5bd43.firebasestorage.app"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var avatarView: ShapeableImageView? = null

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                uploadProfilePhoto(uri)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance(STORAGE_BUCKET)
        avatarView = view.findViewById(R.id.iv_profile_avatar)

        loadUserProfile(view)
        loadUserStats(view)
        setupClickListeners(view)
    }

    private fun loadUserProfile(view: View) {
        val user = auth.currentUser ?: return
        view.findViewById<TextView>(R.id.tv_profile_name).text =
            user.displayName ?: "Chef"
        view.findViewById<TextView>(R.id.tv_profile_email).text = user.email ?: ""

        val avatarView = avatarView ?: view.findViewById(R.id.iv_profile_avatar)
        if (user.photoUrl != null) {
            Glide.with(this).load(user.photoUrl).circleCrop().into(avatarView)
        } else {
            avatarView.setImageResource(R.drawable.ic_person)
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
        view.findViewById<View>(R.id.iv_profile_avatar).setOnClickListener {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        view.findViewById<View>(R.id.row_edit_profile).setOnClickListener {
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
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

    private fun uploadProfilePhoto(uri: Uri) {
        val user = auth.currentUser ?: return
        val avatarView = avatarView ?: return

        Glide.with(this).load(uri).circleCrop().into(avatarView)
        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show()

        val photoRef = storage.reference
            .child("profile_photos")
            .child(user.uid)
            .child("avatar_${System.currentTimeMillis()}.jpg")
        val metadata = StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()

        photoRef.putFile(uri, metadata)
            .addOnSuccessListener {
                photoRef.downloadUrl
                    .addOnSuccessListener { downloadUri ->
                        Log.d(TAG, "Photo uploaded: $downloadUri")
                        updateUserPhoto(downloadUri)
                    }
                    .addOnFailureListener { exception ->
                        if (!isAdded) return@addOnFailureListener
                        Log.e(TAG, "Couldn't fetch download URL", exception)
                        Toast.makeText(
                            requireContext(),
                            exception.localizedMessage ?: "Couldn't get photo URL",
                            Toast.LENGTH_LONG
                        ).show()
                        loadUserProfile(requireView())
                    }
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Couldn't upload photo", exception)
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: "Couldn't upload photo",
                    Toast.LENGTH_LONG
                ).show()
                loadUserProfile(requireView())
            }
    }

    private fun updateUserPhoto(downloadUri: Uri) {
        val user = auth.currentUser ?: return
        val profileUpdates = UserProfileChangeRequest.Builder()
            .setPhotoUri(downloadUri)
            .build()

        user.updateProfile(profileUpdates)
            .addOnSuccessListener {
                db.collection("users")
                    .document(user.uid)
                    .set(mapOf("photoUrl" to downloadUri.toString()), com.google.firebase.firestore.SetOptions.merge())
                    .addOnCompleteListener {
                        if (!isAdded) return@addOnCompleteListener
                        Log.d(TAG, "Profile photo updated in auth and firestore")
                        Toast.makeText(
                            requireContext(),
                            "Profile photo updated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
            .addOnFailureListener { exception ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Couldn't update user profile photo", exception)
                Toast.makeText(
                    requireContext(),
                    exception.localizedMessage ?: "Couldn't update profile photo",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    override fun onDestroyView() {
        avatarView = null
        super.onDestroyView()
    }
}
