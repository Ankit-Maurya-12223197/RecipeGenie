package com.example.recipegenie.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Recipe(
    val id: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val cookTimeMinutes: Int = 0,
    val servings: Int = 4,
    val difficulty: String = "Easy",        // Easy / Medium / Hard
    val rating: Float = 0f,
    val category: String = "all",           // breakfast / lunch / dinner / snacks / desserts
    val cuisine: String = "",
    val description: String = "",
    val ingredients: List<Ingredient>? = null,
    val steps: List<Step>? = null,
    val nutrition: Nutrition? = null,
    var isSaved: Boolean = false,
    // Ingredient-match fields (populated by Spoonacular findByIngredients)
    val usedIngredientCount: Int = 0,
    val missedIngredientCount: Int = 0
) : Parcelable
