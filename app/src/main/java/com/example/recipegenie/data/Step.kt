package com.example.recipegenie.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Step(
    val stepNumber: Int = 0,
    val instruction: String = "",
    val tip: String? = null,             // optional chef tip shown in cook mode
    val durationSeconds: Int? = null     // non-null → show timer in cook mode
) : Parcelable