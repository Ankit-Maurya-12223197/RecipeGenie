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

    companion object {
        private const val KEY_SELECTED_NAV_ITEM = "selected_nav_item"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
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

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
        } else {
            bottomNav.selectedItemId =
                savedInstanceState.getInt(KEY_SELECTED_NAV_ITEM, R.id.nav_home)
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(
            KEY_SELECTED_NAV_ITEM,
            findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId
        )
    }
}
