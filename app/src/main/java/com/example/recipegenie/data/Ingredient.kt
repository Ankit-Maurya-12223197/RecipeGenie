package com.example.recipegenie.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Ingredient(
    val name: String = "",
    val amount: String = "",        // e.g. "200g" or "2 tbsp"
    val isAvailable: Boolean = false   // true = user has it, false = missing
) : Parcelable