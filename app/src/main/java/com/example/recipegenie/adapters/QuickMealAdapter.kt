package com.example.recipegenie.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipegenie.R
import com.example.recipegenie.data.Recipe

class QuickMealAdapter(
    private val recipes: MutableList<Recipe>,
    private val onCookClick: (Recipe) -> Unit
) : RecyclerView.Adapter<QuickMealAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEmoji: TextView = view.findViewById(R.id.tv_quick_emoji)
        val tvTitle: TextView = view.findViewById(R.id.tv_quick_title)
        val tvMeta: TextView = view.findViewById(R.id.tv_quick_meta)
        val btnCook: View = view.findViewById(R.id.btn_quick_cook)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_meal, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.tvTitle.text = recipe.title
        holder.tvMeta.text = "${recipe.cookTimeMinutes} min · ${recipe.ingredients?.size ?: 0} ingredients"
        holder.btnCook.setOnClickListener { onCookClick(recipe) }
        holder.itemView.setOnClickListener { onCookClick(recipe) }
    }

    override fun getItemCount() = recipes.size

    fun updateData(newRecipes: List<Recipe>) {
        recipes.clear()
        recipes.addAll(newRecipes)
        notifyDataSetChanged()
    }
}