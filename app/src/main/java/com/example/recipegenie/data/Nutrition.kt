package com.example.recipegenie.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Nutrition(
    val calories: Int = 0,
    val protein: Int = 0,   // grams
    val carbs: Int = 0,
    val fat: Int = 0,
    val fiber: Int = 0
) : Parcelable