package com.example.recipegenie.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.recipegenie.R
import com.example.recipegenie.data.Ingredient

class IngredientItemAdapter(
    private val ingredients: List<Ingredient>,
    private val userIngredients: List<String>
) : RecyclerView.Adapter<IngredientItemAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivDot: ImageView = view.findViewById(R.id.iv_ingredient_dot)
        val tvName: TextView = view.findViewById(R.id.tv_ingredient_name)
        val tvAmount: TextView = view.findViewById(R.id.tv_ingredient_amount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = ingredients[position]
        val isAvailable = userIngredients.any {
            item.name.lowercase().contains(it.lowercase())
        }
        holder.tvName.text = item.name
        holder.tvAmount.text = item.amount
        val dotColor = if (isAvailable) R.color.green_dot else R.color.red_light
        holder.ivDot.setColorFilter(
            ContextCompat.getColor(holder.itemView.context, dotColor)
        )
        holder.tvName.alpha = if (isAvailable) 1f else 0.5f
    }

    override fun getItemCount() = ingredients.size
}
