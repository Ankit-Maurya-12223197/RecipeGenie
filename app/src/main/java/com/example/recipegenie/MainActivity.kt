// MainActivity.kt
package com.example.recipegenie

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.recipegenie.ui.HomeFragment
import com.example.recipegenie.ui.IngredientsFragment
import com.example.recipegenie.ui.SavedFragment
import com.example.recipegenie.ui.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        loadFragment(HomeFragment())

        bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.nav_home        -> HomeFragment()
                R.id.nav_search      -> IngredientsFragment()
                R.id.nav_saved       -> SavedFragment()
                R.id.nav_profile     -> ProfileFragment()
                else                 -> HomeFragment()
            }
            loadFragment(fragment)
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}