package com.example.recipegenie.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.recipegenie.R
import com.example.recipegenie.data.ChatMessage

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_AI = 0
        const val TYPE_USER = 1
    }

    override fun getItemViewType(position: Int) =
        if (messages[position].isFromAi) TYPE_AI else TYPE_USER

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_AI) {
            AiViewHolder(inflater.inflate(R.layout.item_chat_ai, parent, false))
        } else {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        when (holder) {
            is AiViewHolder -> holder.bind(msg)
            is UserViewHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount() = messages.size

    inner class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tv_ai_message)
        fun bind(msg: ChatMessage) { tvText.text = msg.text }
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvText: TextView = view.findViewById(R.id.tv_user_message)
        fun bind(msg: ChatMessage) { tvText.text = msg.text }
    }
}