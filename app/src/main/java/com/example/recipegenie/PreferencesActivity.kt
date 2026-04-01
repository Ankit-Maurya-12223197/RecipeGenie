// PreferencesActivity.kt
package com.example.recipegenie

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PreferencesActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var progressStep: LinearProgressIndicator
    private lateinit var tvStepIndicator: TextView
    private lateinit var chipGroupDiet: ChipGroup
    private lateinit var chipGroupCuisine: ChipGroup
    private lateinit var chipGroupAllergy: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_preferences)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        bindViews()
        setupClickListeners()
        setupChipListeners()
    }

    private fun bindViews() {
        progressStep = findViewById(R.id.progress_step)
        tvStepIndicator = findViewById(R.id.tv_step_indicator)
        chipGroupDiet = findViewById(R.id.chip_group_diet)
        chipGroupCuisine = findViewById(R.id.chip_group_cuisine)
        chipGroupAllergy = findViewById(R.id.chip_group_allergy)
    }

    private fun setupChipListeners() {
        // Style selected chips with orange highlight
        val allGroups = listOf(chipGroupDiet, chipGroupCuisine, chipGroupAllergy)
        allGroups.forEach { group ->
            for (i in 0 until group.childCount) {
                val chip = group.getChildAt(i) as? Chip ?: continue
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        chip.setChipBackgroundColorResource(R.color.orange_extra_light)
                        chip.setChipStrokeColorResource(R.color.orange_primary)
                        chip.setTextColor(getColor(R.color.orange_dark))
                    } else {
                        chip.setChipBackgroundColorResource(R.color.gray_bg)
                        chip.setChipStrokeColorResource(R.color.gray_border)
                        chip.setTextColor(getColor(R.color.gray_dark))
                    }
                }
            }
        }
    }

    private fun setupClickListeners() {
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_continue)
            .setOnClickListener { savePreferencesAndNavigate() }
    }

    private fun savePreferencesAndNavigate() {
        val selectedDiet = getSelectedChipText(chipGroupDiet)
        val selectedCuisines = getAllSelectedChipTexts(chipGroupCuisine)
        val selectedAllergies = getAllSelectedChipTexts(chipGroupAllergy)

        val preferences = hashMapOf(
            "dietType" to selectedDiet,
            "cuisines" to selectedCuisines,
            "allergies" to selectedAllergies
        )

        getSharedPreferences("recipe_genie_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("has_set_preferences", true)
            .putString("diet_type", selectedDiet)
            .apply()

        val uid = auth.currentUser?.uid
        if (uid == null) {
            navigateToMain()
            return
        }

        db.collection("users").document(uid)
            .set(preferences)
            .addOnSuccessListener {
                navigateToMain()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Preferences saved locally. Opening home screen.",
                    Toast.LENGTH_SHORT
                ).show()
                navigateToMain()
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun getSelectedChipText(group: ChipGroup): String {
        val id = group.checkedChipId
        if (id == ChipGroup.NO_ID) return ""
        return (group.findViewById<Chip>(id))?.text?.toString() ?: ""
    }

    private fun getAllSelectedChipTexts(group: ChipGroup): List<String> {
        val result = mutableListOf<String>()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) result.add(chip.text.toString())
        }
        return result
    }
}
