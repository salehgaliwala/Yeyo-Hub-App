package com.trackigniter.client

import android.content.Context
import android.util.Log
import com.trackigniter.client.api.RetrofitClient
import com.trackigniter.client.api.models.LogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object RemoteLogger {
    private const val TAG = "RemoteLogger"
    private val scope = CoroutineScope(Dispatchers.IO)
    private val buffer = CopyOnWriteArrayList<LogEntry>()
    private const val BUFFER_LIMIT = 1
    private var isEnabled = true 

    fun init(context: Context) {
        // Could load "isEnabled" from preferences here
    }

    fun log(context: Context, level: String, message: String) {
        if (!SessionManager.isDebugEnabled(context)) return

        val driverId = SessionManager.getDriverId(context) ?: "unknown"
        val vehicleId = SessionManager.getVehicleId(context) ?: "unknown"
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val entry = LogEntry(
            driverId = driverId,
            vehicleId = vehicleId,
            level = level,
            message = message,
            timestamp = timestamp
        )

        buffer.add(entry)
        Log.d(TAG, "Buffered remote log: $message. Size: ${buffer.size}")

        if (buffer.size >= BUFFER_LIMIT) {
            flush()
        }
    }
    
    fun flush() {
        if (buffer.isEmpty()) return
        
        val logsToSend = ArrayList(buffer)
        buffer.clear()
        
        scope.launch {
            try {
                Log.d(TAG, "Flushing ${logsToSend.size} logs to server...")
                val response = RetrofitClient.apiService.sendLogs(logsToSend)
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to send logs: ${response.code()}")
                    // Optionally re-add to buffer if critical, but avoid infinite loops
                } else {
                    Log.d(TAG, "Logs sent successfully.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending logs: ${e.message}")
            }
        }
    }
}
