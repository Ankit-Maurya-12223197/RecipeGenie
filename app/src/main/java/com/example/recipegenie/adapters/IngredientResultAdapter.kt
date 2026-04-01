package com.example.recipegenie.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipegenie.R
import com.example.recipegenie.data.Recipe

class IngredientResultAdapter(
    private val recipes: MutableList<Recipe>,
    private val onItemClick: (Recipe) -> Unit
) : RecyclerView.Adapter<IngredientResultAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.iv_result_thumb)
        val tvTitle: TextView = view.findViewById(R.id.tv_result_title)
        val tvTime: TextView = view.findViewById(R.id.tv_result_time)
        val tvMatchBadge: TextView = view.findViewById(R.id.tv_match_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient_result, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.tvTitle.text = recipe.title
        holder.tvTime.text = "${recipe.cookTimeMinutes} min"

        Glide.with(holder.itemView.context)
            .load(recipe.imageUrl)
            .placeholder(R.drawable.placeholder_recipe)
            .centerCrop()
            .into(holder.ivThumb)

        when {
            recipe.missedIngredientCount == 0 -> {
                holder.tvMatchBadge.text = "All match"
                holder.tvMatchBadge.setBackgroundResource(R.drawable.bg_badge_green)
                holder.tvMatchBadge.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.green_text))
            }
            else -> {
                holder.tvMatchBadge.text = "Missing ${recipe.missedIngredientCount}"
                holder.tvMatchBadge.setBackgroundResource(R.drawable.bg_badge_yellow)
                holder.tvMatchBadge.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.yellow_text))
            }
        }

        holder.itemView.setOnClickListener { onItemClick(recipe) }
    }

    override fun getItemCount() = recipes.size

    fun updateData(newRecipes: List<Recipe>) {
        recipes.clear()
        recipes.addAll(newRecipes)
        notifyDataSetChanged()
    }
}
