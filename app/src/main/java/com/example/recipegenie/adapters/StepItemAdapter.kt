package com.example.recipegenie.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipegenie.R
import com.example.recipegenie.data.Step

class StepItemAdapter(private val steps: List<Step>) :
    RecyclerView.Adapter<StepItemAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvStepNum: TextView = view.findViewById(R.id.tv_step_number)
        val tvInstruction: TextView = view.findViewById(R.id.tv_step_text)
        val tvTimer: TextView = view.findViewById(R.id.tv_step_timer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context)
            .inflate(R.layout.item_step, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val step = steps[position]
        holder.tvStepNum.text = "${step.stepNumber}"
        holder.tvInstruction.text = step.instruction
        val dur = step.durationSeconds
        if (dur != null && dur > 0) {
            val mins = dur / 60
            val secs = dur % 60
            holder.tvTimer.visibility = View.VISIBLE
            holder.tvTimer.text = if (secs == 0) "${mins} min" else "${mins}:${"%02d".format(secs)}"
        } else {
            holder.tvTimer.visibility = View.GONE
        }
    }

    override fun getItemCount() = steps.size
}
