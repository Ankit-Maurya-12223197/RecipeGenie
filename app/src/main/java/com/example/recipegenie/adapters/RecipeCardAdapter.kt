package com.example.recipegenie.adapters


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.recipegenie.R
import com.example.recipegenie.data.Recipe

class RecipeCardAdapter(
    private val recipes: MutableList<Recipe>,
    private val onItemClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeCardAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.iv_recipe_thumb)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_recipe_title)
        val tvTime: TextView = itemView.findViewById(R.id.tv_recipe_time)
        val tvRating: TextView = itemView.findViewById(R.id.tv_recipe_rating)
        val tvDifficulty: TextView = itemView.findViewById(R.id.tv_recipe_difficulty)
        val btnFavourite: ImageButton = itemView.findViewById(R.id.btn_favourite)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.tvTitle.text = recipe.title
        holder.tvTime.text = "${recipe.cookTimeMinutes} min"
        holder.tvRating.text = "⭐ ${recipe.rating}"
        holder.tvDifficulty.text = recipe.difficulty

        Glide.with(holder.itemView.context)
            .load(recipe.imageUrl)
            .placeholder(R.drawable.placeholder_recipe)
            .centerCrop()
            .into(holder.ivThumbnail)

        holder.btnFavourite.setOnClickListener {
            recipe.isSaved = !recipe.isSaved
            holder.btnFavourite.setImageResource(
                if (recipe.isSaved) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
            )
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
