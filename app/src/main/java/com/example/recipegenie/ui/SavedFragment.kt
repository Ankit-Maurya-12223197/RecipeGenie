package com.example.recipegenie.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.recipegenie.R
import com.example.recipegenie.RecipeDetailActivity
import com.example.recipegenie.adapters.RecipeCardAdapter
import com.example.recipegenie.data.Recipe
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SavedFragment : Fragment() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var rvSavedRecipes: RecyclerView
    private lateinit var llEmpty: LinearLayout

    private val savedAdapter = RecipeCardAdapter(mutableListOf()) { recipe ->
        startActivity(Intent(requireContext(), RecipeDetailActivity::class.java).apply {
            putExtra("recipe", recipe)
            putExtra("recipe_id", recipe.id)
            putExtra("recipe_title", recipe.title)
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_saved, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        rvSavedRecipes = view.findViewById(R.id.rv_saved_recipes)
        llEmpty = view.findViewById(R.id.ll_saved_empty)

        rvSavedRecipes.adapter = savedAdapter
        rvSavedRecipes.layoutManager = GridLayoutManager(requireContext(), 2)

        loadSavedRecipes()
    }

    override fun onResume() {
        super.onResume()
        loadSavedRecipes()
    }

    private fun loadSavedRecipes() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid)
            .collection("saved_recipes")
            .orderBy("savedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    llEmpty.visibility = View.VISIBLE
                    rvSavedRecipes.visibility = View.GONE
                    return@addOnSuccessListener
                }
                llEmpty.visibility = View.GONE
                rvSavedRecipes.visibility = View.VISIBLE
                val recipes = snapshot.documents.mapNotNull { it.toObject(Recipe::class.java) }
                savedAdapter.updateData(recipes)
            }
            .addOnFailureListener {
                llEmpty.visibility = View.VISIBLE
                rvSavedRecipes.visibility = View.GONE
            }
    }
}
