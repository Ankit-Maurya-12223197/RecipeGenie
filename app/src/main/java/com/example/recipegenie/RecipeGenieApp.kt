package com.example.recipegenie

import android.app.Application
import com.google.firebase.FirebaseApp

class RecipeGenieApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
