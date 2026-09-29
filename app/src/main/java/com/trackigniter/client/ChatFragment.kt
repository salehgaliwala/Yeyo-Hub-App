package com.trackigniter.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trackigniter.client.api.RetrofitClient
import com.trackigniter.client.api.models.ChatMessage
import com.trackigniter.client.api.models.ChatSendRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatFragment : Fragment() {

    private lateinit var adapter: ChatAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var input: EditText
    private var refreshJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewChat)
        input = view.findViewById(R.id.editChatMessage)
        val send = view.findViewById<View>(R.id.btnChatSend)

        adapter = ChatAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true // Start from bottom
        }
        recyclerView.adapter = adapter

        send.setOnClickListener {
            val msg = input.text.toString().trim()
            if (msg.isNotEmpty()) {
                sendMessage(msg)
            }
        }

        fetchMessages()
        startAutoRefresh()

        return view
    }

    private fun fetchMessages() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getChats()
                if (response.isSuccessful) {
                    val messages = response.body() ?: emptyList()
                    adapter.updateMessages(messages)
                    if (messages.isNotEmpty()) {
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }
            } catch (e: Exception) {
                Log.e("Chat", "Error fetching", e)
            }
        }
    }

    private fun sendMessage(msg: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Clear input immediately for better UX
                input.setText("")

                val response = RetrofitClient.apiService.sendChat(ChatSendRequest(msg))
                if (response.isSuccessful) {
                    fetchMessages()
                } else {
                    Toast.makeText(context, "Failed to send", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startAutoRefresh() {
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                delay(5000) // 5 seconds
                fetchMessages()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        refreshJob?.cancel()
    }
}
