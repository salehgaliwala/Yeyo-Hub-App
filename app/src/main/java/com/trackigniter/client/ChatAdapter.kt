package com.trackigniter.client

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.trackigniter.client.api.models.ChatMessage

class ChatAdapter(private var messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: LinearLayout = view.findViewById(R.id.cardChatMessage)
        val textMessage: TextView = view.findViewById(R.id.textChatMessage)
        val textTime: TextView = view.findViewById(R.id.textChatTime)
        val container: LinearLayout = view as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        holder.textMessage.text = msg.message
        holder.textTime.text = msg.createdAt

        val params = holder.bubble.layoutParams as LinearLayout.LayoutParams
        if (msg.isSenderDriver == 1) {
            // Driver (Me) - Right
            holder.container.gravity = Gravity.END
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_driver)
            holder.textMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.white))
            params.marginStart = 50
            params.marginEnd = 0
        } else {
            // Admin - Left
            holder.container.gravity = Gravity.START
            holder.bubble.setBackgroundResource(R.drawable.bg_bubble_admin)
            holder.textMessage.setTextColor(ContextCompat.getColor(holder.itemView.context, android.R.color.black))
            params.marginStart = 0
            params.marginEnd = 50
        }
        holder.bubble.layoutParams = params
    }

    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }
}
