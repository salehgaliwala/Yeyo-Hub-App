/*
 * Copyright 2015 - 2021 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.trackigniter.client

import android.content.Context
import com.trackigniter.client.ProtocolFormatter.formatRequest
import com.trackigniter.client.RequestManager.sendRequestAsync
import com.trackigniter.client.PositionProvider.PositionListener
import com.trackigniter.client.NetworkManager.NetworkHandler
import android.os.Handler
import android.os.Looper
import androidx.preference.PreferenceManager
import android.util.Log
import com.trackigniter.client.DatabaseHelper.DatabaseHandler
import com.trackigniter.client.RequestManager.RequestHandler
import com.trackigniter.client.api.models.LocationRequest
import com.trackigniter.client.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class TrackingController(private val context: Context) : PositionListener, NetworkHandler {

    private val handler = Handler(Looper.getMainLooper())
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val positionProvider = PositionProviderFactory.create(context, this)
    private val databaseHelper = DatabaseHelper(context)
    private val networkManager = NetworkManager(context, this)
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val url: String = preferences.getString(MainFragment.KEY_URL, context.getString(R.string.settings_url_default_value))!!
    private val buffer: Boolean = preferences.getBoolean(MainFragment.KEY_BUFFER, true)

    private var isOnline = networkManager.isOnline
    private var isWaiting = false

    fun start() {
        if (isOnline) {
            read()
        }
        try {
            positionProvider.startUpdates()
        } catch (e: SecurityException) {
            Log.w(TAG, e)
        }
        networkManager.start()
    }

    fun stop() {
        networkManager.stop()
        try {
            positionProvider.stopUpdates()
        } catch (e: SecurityException) {
            Log.w(TAG, e)
        }
        handler.removeCallbacksAndMessages(null)
        job.cancel()
    }

    override fun onPositionUpdate(position: Position) {
        StatusActivity.addMessage(context.getString(R.string.status_location_update))
        if (buffer) {
            write(position)
        } else {
            send(position)
        }
    }

    override fun onPositionError(error: Throwable) {}
    override fun onNetworkUpdate(isOnline: Boolean) {
        val message = if (isOnline) R.string.status_network_online else R.string.status_network_offline
        StatusActivity.addMessage(context.getString(message))
        if (!this.isOnline && isOnline) {
            read()
        }
        this.isOnline = isOnline
    }

    //
    // State transition examples:
    //
    // write -> read -> send -> delete -> read
    //
    // read -> send -> retry -> read -> send
    //

    private fun log(action: String, position: Position?) {
        var formattedAction: String = action
        if (position != null) {
            formattedAction +=
                    " (id:" + position.id +
                    " time:" + position.time.time / 1000 +
                    " lat:" + position.latitude +
                    " lon:" + position.longitude + ")"
        }
        Log.d(TAG, formattedAction)
    }

    private fun write(position: Position) {
        log("write", position)
        databaseHelper.insertPositionAsync(position, object : DatabaseHandler<Unit?> {
            override fun onComplete(success: Boolean, result: Unit?) {
                if (success) {
                    if (isOnline && isWaiting) {
                        read()
                        isWaiting = false
                    }
                }
            }
        })
    }

    private fun read() {
        log("read", null)
        databaseHelper.selectPositionAsync(object : DatabaseHandler<Position?> {
            override fun onComplete(success: Boolean, result: Position?) {
                if (success) {
                    if (result != null) {
                        if (result.deviceId == preferences.getString(MainFragment.KEY_DEVICE, null)) {
                            send(result)
                        } else {
                            delete(result)
                        }
                    } else {
                        isWaiting = true
                    }
                } else {
                    retry()
                }
            }
        })
    }

    private fun delete(position: Position) {
        log("delete", position)
        databaseHelper.deletePositionAsync(position.id, object : DatabaseHandler<Unit?> {
            override fun onComplete(success: Boolean, result: Unit?) {
                if (success) {
                    read()
                } else {
                    retry()
                }
            }
        })
    }

    private var lastAutoRecoveryTime: Long = 0

    private fun send(position: Position) {
        log("send", position)
        // Self-Healing: Check if we have a vehicleId. If not, try to auto-recover it.
        scope.launch {
            var vehicleId = SessionManager.getVehicleId(context)
            var isDriverUpdate = false

            // Auto-Recover: If vehicleId is null, check if we have any active trips that we should be tracking
            // Throttle: Check every 5 seconds (5000ms) to ensure we pick up new trips quickly during testing
            if ((vehicleId.isNullOrEmpty() || vehicleId == "0") && 
                (System.currentTimeMillis() - lastAutoRecoveryTime > 5000)) {
                
                lastAutoRecoveryTime = System.currentTimeMillis()
                try {
                    Log.d(TAG, "Vehicle ID missing. Attempting auto-recovery from API trips...")
                    val trips = com.trackigniter.client.data.TripRepository.getTrips()
                    
                    // Log the count for debugging
                    RemoteLogger.log(context, "INFO", "Auto-Recover: Checked ${trips.size} trips.")

                    val activeTrip = trips.find { 
                        val st = it.status?.trim() ?: ""
                        val drs = it.driverResponseStatus?.trim() ?: ""
                        
                        // Final States: Trip is over
                        val isFinal = st.equals("Completed", true) || st.equals("Rejected", true) || st.equals("Cancelled", true)
                        
                        // Pending States: Trip hasn't started/accepted yet
                        // Note: If status is 'Assigned' but drs is 'Accepted', we usually treat it as ready/active.
                        // But strictly 'Assigned' usually implies waiting for acceptance.
                        val isPending = st.equals("Assigned", true) && !drs.equals("Accepted", true)

                        // Active: Not Final AND (Driver Accepted OR Status indicates progress like "Started", "Unloading", "In Transit" etc.)
                        val isMatch = !isFinal && !isPending && (drs.equals("Accepted", true) || st.length > 2)
                        
                        // Debug log for the first few trips to see statuses
                        if (!isMatch) {
                             // Log.d(TAG, "Skipping Trip ${it.id}: Status='$st', DriverStatus='$drs'") 
                        }
                        isMatch
                    }

                    if (activeTrip != null) {
                        // Prefer vehicleApiUsername, then vehicleId
                        val idsToTry = listOf(activeTrip.vehicleApiUsername, activeTrip.vehicleId)
                        val recoveredId = idsToTry.firstOrNull { !it.isNullOrEmpty() && it != "0" }
                        
                        if (recoveredId != null) {
                            val logMsg = "Auto-recovered active Vehicle ID: $recoveredId (from Trip ${activeTrip.id})."
                            Log.i(TAG, logMsg)
                            RemoteLogger.log(context, "INFO", logMsg)
                            SessionManager.saveVehicleId(context, recoveredId)
                            vehicleId = recoveredId
                        } else {
                            RemoteLogger.log(context, "WARN", "Found active Trip ${activeTrip.id} but IDs were invalid: $idsToTry")
                        }
                    } else {
                         // Only log this occasionally to avoid spam, or rely on the count log above
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-recovery failed: ${e.message}")
                    RemoteLogger.log(context, "ERROR", "Auto-recovery failed: ${e.message}")
                }
            }

            // Fallback: If still no vehicleId, try Driver ID
            if (vehicleId.isNullOrEmpty() || vehicleId == "0") {
                 val driverId = SessionManager.getDriverId(context)
                 if (!driverId.isNullOrEmpty()) {
                     Log.i(TAG, "Using Driver ID '$driverId' for tracking (No active vehicle found)")
                     vehicleId = driverId
                     isDriverUpdate = true
                 } else {
                     runOnMainThread { StatusActivity.addMessage("Send Fail: No Active Trip/Vehicle") }
                     RemoteLogger.log(context, "WARN", "Send Fail: No Active Trip/Vehicle (Driver ID missing too)")
                     Log.w(TAG, "Aborting send: vehicleId and driverId are null/empty")
                     if (buffer) retry()
                     return@launch
                 }
            }
            
            // Proceed with network request using the (potentially recovered) ID
            val request = com.trackigniter.client.api.models.LocationRequest(
                v_id = vehicleId,
                latitude = position.latitude,
                longitude = position.longitude,
                altitude = position.altitude,
                speed = position.speed,
                bearing = position.course,
                accuracy = position.accuracy,
                provider = position.provider,
                battery_level = position.battery,
                time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(position.time)
            )

            try {
                // Remove UI spam if needed, keeping error messages
                val response = com.trackigniter.client.api.RetrofitClient.apiService.track(request)
                if (response.isSuccessful) {
                    val statusMsg = if (isDriverUpdate) "Driver Location Updated" else "Send Success"
                    // runOnMainThread { StatusActivity.addMessage(statusMsg) } // Optional: Reduce spam
                    runOnMainThread { StatusActivity.addMessage(statusMsg) } 
                    if (buffer) delete(position)
                } else {
                    Log.e(TAG, "Tracking failed: ${response.code()} ${response.message()}")
                    RemoteLogger.log(context, "ERROR", "Tracking failed: ${response.code()} ${response.message()}")
                    runOnMainThread { StatusActivity.addMessage("Send Fail: ${response.code()}") }
                    if (buffer) retry()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tracking error", e)
                RemoteLogger.log(context, "ERROR", "Tracking exception: ${e.message}")
                runOnMainThread { StatusActivity.addMessage("Send Error: ${e.message}") }
                if (buffer) retry()
            }
        }
    }
    
    private fun runOnMainThread(action: () -> Unit) {
        handler.post(action)
    }

    private fun retry() {
        log("retry", null)
        handler.postDelayed({
            if (isOnline) {
                read()
            }
        }, RETRY_DELAY.toLong())
    }

    companion object {
        private val TAG = TrackingController::class.java.simpleName
        private const val RETRY_DELAY = 30 * 1000
    }

}
