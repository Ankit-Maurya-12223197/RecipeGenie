package com.example.recipegenie.ui

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipegenie.R
import com.example.recipegenie.RecipeDetailActivity
import com.example.recipegenie.adapters.IngredientResultAdapter
import com.example.recipegenie.data.GeminiRecipeRepository
import com.example.recipegenie.data.Recipe
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class IngredientsFragment : Fragment() {

    private val ingredientsList = mutableListOf<String>()

    private lateinit var etIngredient: TextInputEditText
    private lateinit var chipGroupIngredients: ChipGroup
    private lateinit var btnFindRecipes: MaterialButton
    private lateinit var progressSearch: LinearProgressIndicator
    private lateinit var rvResults: RecyclerView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var llResultsHeader: LinearLayout
    private lateinit var tvResultsCount: TextView

    private val resultsAdapter = IngredientResultAdapter(mutableListOf()) { recipe ->
        startActivity(Intent(requireContext(), RecipeDetailActivity::class.java).apply {
            putExtra("recipe", recipe)
            putExtra("recipe_id", recipe.id)
            putExtra("recipe_title", recipe.title)
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ingredients, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupClickListeners()
        setupRecyclerView()
    }

    private fun bindViews(view: View) {
        etIngredient = view.findViewById(R.id.et_ingredient)
        chipGroupIngredients = view.findViewById(R.id.chip_group_ingredients)
        btnFindRecipes = view.findViewById(R.id.btn_find_recipes)
        progressSearch = view.findViewById(R.id.progress_search)
        rvResults = view.findViewById(R.id.rv_results)
        llEmptyState = view.findViewById(R.id.ll_empty_state)
        llResultsHeader = view.findViewById(R.id.ll_results_header)
        tvResultsCount = view.findViewById(R.id.tv_results_count)
    }

    private fun setupClickListeners() {
        view?.findViewById<MaterialButton>(R.id.btn_add_ingredient)
            ?.setOnClickListener { addIngredient() }

        etIngredient.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER) {
                addIngredient(); true
            } else false
        }

        btnFindRecipes.setOnClickListener {
            if (ingredientsList.isEmpty()) {
                etIngredient.error = "Add at least one ingredient"
                return@setOnClickListener
            }
            searchRecipes()
        }
    }

    private fun setupRecyclerView() {
        rvResults.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = resultsAdapter
        }
    }

    private fun addIngredient() {
        val ingredient = etIngredient.text?.toString()?.trim() ?: return
        if (ingredient.isEmpty() || ingredientsList.contains(ingredient.lowercase())) return

        ingredientsList.add(ingredient.lowercase())
        addChip(ingredient)
        etIngredient.setText("")
        updateFindButtonText()

        // Hide keyboard
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(etIngredient.windowToken, 0)
    }

    private fun addChip(ingredient: String) {
        val chip = Chip(requireContext()).apply {
            text = ingredient
            isCloseIconVisible = true
            setChipBackgroundColorResource(R.color.orange_extra_light)
            setChipStrokeColorResource(R.color.orange_border)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.orange_dark))
            setCloseIconTintResource(R.color.orange_primary)
            setOnCloseIconClickListener {
                ingredientsList.remove(ingredient.lowercase())
                chipGroupIngredients.removeView(this)
                updateFindButtonText()
            }
        }
        chipGroupIngredients.addView(chip)
    }

    private fun updateFindButtonText() {
        val count = ingredientsList.size
        btnFindRecipes.text = if (count == 0) "Find recipes"
        else "Find recipes ($count ingredient${if (count > 1) "s" else ""})"
    }

    private fun searchRecipes() {
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                GeminiRecipeRepository.generateRecipesFromIngredients(ingredientsList)
            }.onSuccess { recipes ->
                if (!isAdded) return@onSuccess
                showLoading(false)
                displayResults(recipes)
                if (recipes.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        "No recipes generated. Try different ingredients.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.onFailure { error ->
                if (!isAdded) return@onFailure
                showLoading(false)
                displayResults(emptyList())
                Toast.makeText(
                    requireContext(),
                    error.localizedMessage ?: "Gemini recipe generation failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun displayResults(recipes: List<Recipe>) {
        if (recipes.isEmpty()) {
            llEmptyState.visibility = View.VISIBLE
            rvResults.visibility = View.GONE
            llResultsHeader.visibility = View.GONE
            return
        }
        llEmptyState.visibility = View.GONE
        rvResults.visibility = View.VISIBLE
        llResultsHeader.visibility = View.VISIBLE
        tvResultsCount.text = "${recipes.size} recipe${if (recipes.size > 1) "s" else ""} found"
        resultsAdapter.updateData(recipes)
    }

    private fun showLoading(show: Boolean) {
        progressSearch.visibility = if (show) View.VISIBLE else View.GONE
        btnFindRecipes.isEnabled = !show
    }
}
