package com.example.recipegenie.data

data class RecipeCollection(
    val id: String = "",
    val name: String = "",
    val recipeCount: Int = 0,
    val coverImageUrl: String = "",
    val createdAt: Long = 0L
)