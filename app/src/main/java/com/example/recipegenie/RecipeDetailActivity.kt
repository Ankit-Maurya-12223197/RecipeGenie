// RecipeDetailActivity.kt
package com.example.recipegenie

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipegenie.adapters.IngredientItemAdapter
import com.example.recipegenie.adapters.StepItemAdapter
import com.example.recipegenie.data.Ingredient
import com.example.recipegenie.data.Recipe
import com.example.recipegenie.data.Step
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RecipeDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var tvTitle: TextView
    private lateinit var tvCookTime: TextView
    private lateinit var tvServings: TextView
    private lateinit var tvDifficulty: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvServingsCount: TextView
    private lateinit var btnSave: ImageButton
    private lateinit var rvIngredients: RecyclerView
    private lateinit var rvSteps: RecyclerView

    private var recipe: Recipe? = null
    private var isSaved = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        setupToolbar()
        bindViews()
        setupClickListeners()

        val passedRecipe = intent.getParcelableExtra<Recipe>("recipe")
        if (passedRecipe != null) {
            recipe = passedRecipe
            displayRecipe(passedRecipe)
            return
        }

        val recipeId = intent.getStringExtra("recipe_id") ?: return
        loadRecipeDetails(recipeId)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tv_recipe_title)
        tvCookTime = findViewById(R.id.tv_cook_time)
        tvServings = findViewById(R.id.tv_servings)
        tvDifficulty = findViewById(R.id.tv_difficulty)
        tvRating = findViewById(R.id.tv_rating)
        tvServingsCount = findViewById(R.id.tv_servings_count)
        btnSave = findViewById(R.id.btn_save_recipe)
        rvIngredients = findViewById(R.id.rv_ingredients)
        rvSteps = findViewById(R.id.rv_steps)

        rvIngredients.layoutManager = LinearLayoutManager(this)
        rvSteps.layoutManager = LinearLayoutManager(this)
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener { toggleSave() }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_start_cooking)
            .setOnClickListener {
                recipe?.let { r ->
                    startActivity(Intent(this, CookModeActivity::class.java).apply {
                        putExtra("recipe_id", r.id)
                        putParcelableArrayListExtra("steps",
                            ArrayList(r.steps ?: emptyList()))
                    })
                }
            }

        findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_ask_ai)
            .setOnClickListener {
                recipe?.let { r ->
                    startActivity(Intent(this, AiChefActivity::class.java).apply {
                        putExtra("recipe_id", r.id)
                        putExtra("recipe_title", r.title)
                    })
                }
            }
    }

    private fun loadRecipeDetails(recipeId: String) {
        db.collection("recipes").document(recipeId)
            .get()
            .addOnSuccessListener { doc ->
                recipe = doc.toObject(Recipe::class.java)
                recipe?.let { displayRecipe(it) } ?: run {
                    Toast.makeText(this, "Recipe details unavailable", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't load recipe details", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayRecipe(r: Recipe) {
        tvTitle.text = r.title
        tvCookTime.text = r.cookTimeMinutes.toString()
        tvServings.text = r.servings.toString()
        tvServingsCount.text = "${r.servings} servings"
        tvDifficulty.text = r.difficulty
        tvRating.text = String.format("%.1f", r.rating)

        Glide.with(this)
            .load(r.imageUrl)
            .placeholder(R.drawable.placeholder_recipe)
            .centerCrop()
            .into(findViewById(R.id.iv_hero))

        // Load ingredients with match indicators
        val userIngredients = getUserIngredients()
        rvIngredients.adapter = IngredientItemAdapter(r.ingredients ?: emptyList(), userIngredients)

        // Load steps
        rvSteps.adapter = StepItemAdapter(r.steps ?: emptyList())

        // Set nutrition
        r.nutrition?.let { n ->
            findViewById<TextView>(R.id.tv_calories).text = n.calories.toString()
            findViewById<TextView>(R.id.tv_protein).text = "${n.protein}g"
            findViewById<TextView>(R.id.tv_carbs).text = "${n.carbs}g"
            findViewById<TextView>(R.id.tv_fat).text = "${n.fat}g"
        }

        // Check if already saved
        checkIfSaved(r.id)
    }

    private fun getUserIngredients(): List<String> {
        // Retrieve user's ingredient list from SharedPreferences or ViewModel
        val prefs = getSharedPreferences("recipe_genie_prefs", MODE_PRIVATE)
        return prefs.getStringSet("current_ingredients", emptySet())?.toList() ?: emptyList()
    }

    private fun checkIfSaved(recipeId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("saved_recipes").document(recipeId)
            .get()
            .addOnSuccessListener { doc ->
                isSaved = doc.exists()
                updateSaveIcon()
            }
    }

    private fun toggleSave() {
        val uid = auth.currentUser?.uid ?: return
        val currentRecipe = recipe ?: return
        val recipeId = currentRecipe.id
        val savedRef = db.collection("users").document(uid)
            .collection("saved_recipes").document(recipeId)

        if (isSaved) {
            savedRef.delete().addOnSuccessListener {
                isSaved = false
                updateSaveIcon()
                Toast.makeText(this, "Removed from saved", Toast.LENGTH_SHORT).show()
            }
        } else {
            val savedRecipe = hashMapOf(
                "id" to currentRecipe.id,
                "title" to currentRecipe.title,
                "imageUrl" to currentRecipe.imageUrl,
                "cookTimeMinutes" to currentRecipe.cookTimeMinutes,
                "servings" to currentRecipe.servings,
                "difficulty" to currentRecipe.difficulty,
                "rating" to currentRecipe.rating.toDouble(),
                "category" to currentRecipe.category,
                "cuisine" to currentRecipe.cuisine,
                "description" to currentRecipe.description,
                "ingredients" to currentRecipe.ingredients.orEmpty().map { ingredient ->
                    hashMapOf(
                        "name" to ingredient.name,
                        "amount" to ingredient.amount,
                        "isAvailable" to ingredient.isAvailable
                    )
                },
                "steps" to currentRecipe.steps.orEmpty().map { step ->
                    hashMapOf(
                        "stepNumber" to step.stepNumber,
                        "instruction" to step.instruction,
                        "tip" to step.tip,
                        "durationSeconds" to step.durationSeconds
                    )
                },
                "nutrition" to currentRecipe.nutrition?.let { nutrition ->
                    hashMapOf(
                        "calories" to nutrition.calories,
                        "protein" to nutrition.protein,
                        "carbs" to nutrition.carbs,
                        "fat" to nutrition.fat,
                        "fiber" to nutrition.fiber
                    )
                },
                "isSaved" to true,
                "usedIngredientCount" to currentRecipe.usedIngredientCount,
                "missedIngredientCount" to currentRecipe.missedIngredientCount,
                "savedAt" to System.currentTimeMillis()
            )

            savedRef.set(savedRecipe)
                .addOnSuccessListener {
                    isSaved = true
                    updateSaveIcon()
                    Toast.makeText(this, "Recipe saved!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(
                        this,
                        error.localizedMessage ?: "Couldn't save recipe",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun updateSaveIcon() {
        btnSave.setImageResource(
            if (isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }
}
