// HomeFragment.kt
package com.example.recipegenie.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipegenie.R
import com.example.recipegenie.adapters.QuickMealAdapter
import com.example.recipegenie.adapters.RecipeCardAdapter
import com.example.recipegenie.data.Recipe
import com.example.recipegenie.data.SpoonacularRepository
import com.google.android.material.chip.ChipGroup
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import java.util.Calendar
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var rvTrending: RecyclerView
    private lateinit var rvQuickMeals: RecyclerView
    private lateinit var chipGroupCategory: ChipGroup
    private lateinit var tvGreeting: TextView
    private lateinit var tvUserName: TextView
    private lateinit var ivAvatar: ShapeableImageView

    private val trendingAdapter = RecipeCardAdapter(mutableListOf()) { recipe ->
        openRecipeDetail(recipe)
    }
    private val quickMealAdapter = QuickMealAdapter(mutableListOf()) { recipe ->
        openRecipeDetail(recipe)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        bindViews(view)
        setupGreeting()
        setupRecyclerViews()
        setupCategoryChips()
        loadRecipes("all")
    }

    private fun bindViews(view: View) {
        tvGreeting = view.findViewById(R.id.tv_greeting)
        tvUserName = view.findViewById(R.id.tv_user_name)
        ivAvatar = view.findViewById(R.id.iv_avatar)
        rvTrending = view.findViewById(R.id.rv_trending)
        rvQuickMeals = view.findViewById(R.id.rv_quick_meals)
        chipGroupCategory = view.findViewById(R.id.chip_group_category)
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            hour < 12 -> "Good morning,"
            hour < 17 -> "Good afternoon,"
            else -> "Good evening,"
        }
        tvGreeting.text = greeting

        val user = auth.currentUser
        val name = user?.displayName?.split(" ")?.firstOrNull() ?: "Chef"
        tvUserName.text = "$name 👋"
        loadAvatar()
    }

    override fun onResume() {
        super.onResume()
        if (::tvUserName.isInitialized) {
            setupGreeting()
        }
    }

    private fun loadAvatar() {
        val photoUrl = auth.currentUser?.photoUrl
        if (photoUrl != null) {
            Glide.with(this).load(photoUrl).circleCrop().into(ivAvatar)
        } else {
            ivAvatar.setImageResource(R.drawable.ic_person)
        }
    }

    private fun setupRecyclerViews() {
        rvTrending.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = trendingAdapter
        }
        rvQuickMeals.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = quickMealAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupCategoryChips() {
        chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val category = when (checkedIds.first()) {
                R.id.chip_all -> "all"
                R.id.chip_breakfast -> "breakfast"
                R.id.chip_lunch -> "lunch"
                R.id.chip_dinner -> "dinner"
                R.id.chip_snacks -> "snacks"
                R.id.chip_desserts -> "desserts"
                else -> "all"
            }
            loadRecipes(category)
        }
    }

    private fun loadRecipes(category: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val recipes = SpoonacularRepository.getHomeRecipes(category)
            if (!isAdded) return@launch

            trendingAdapter.updateData(recipes.take(5))
            quickMealAdapter.updateData(
                recipes.filter { it.cookTimeMinutes in 1..20 }.ifEmpty { recipes.take(4) }
            )
        }
    }

    private fun openRecipeDetail(recipe: Recipe) {
        val activity = requireActivity()
        val intent = android.content.Intent(
            activity,
            com.example.recipegenie.RecipeDetailActivity::class.java
        )
        intent.putExtra("recipe", recipe)
        intent.putExtra("recipe_id", recipe.id)
        intent.putExtra("recipe_title", recipe.title)
        activity.startActivity(intent)
    }
}
