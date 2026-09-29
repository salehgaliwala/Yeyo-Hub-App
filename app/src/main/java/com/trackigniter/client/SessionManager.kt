package com.trackigniter.client

import android.content.Context
import androidx.preference.PreferenceManager

object SessionManager {

    private const val PREF_NAME = "trackigniter_prefs" // Added this constant
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_VEHICLE_ID = "vehicle_id"
    private const val KEY_DRIVER_ID = "driver_id"
    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_FCM_TOKEN = "fcm_token"
    private const val KEY_DEBUG_ENABLED = "debug_enabled" // Added this constant

    fun saveToken(context: Context, token: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(KEY_TOKEN, token).putBoolean(KEY_IS_LOGGED_IN, true).apply()
    }

    fun saveDebugEnabled(context: Context, isEnabled: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(KEY_DEBUG_ENABLED, isEnabled).apply()
    }

    fun isDebugEnabled(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_DEBUG_ENABLED, false)
    }

    fun getToken(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(KEY_TOKEN, null)
    }

    fun saveVehicleId(context: Context, vehicleId: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        android.util.Log.d("SessionManager", "Saving vehicleId: '$vehicleId'")
        prefs.edit().putString(KEY_VEHICLE_ID, vehicleId).apply()
    }

    fun getVehicleId(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val vehicleId = prefs.getString(KEY_VEHICLE_ID, null)
        // android.util.Log.d("SessionManager", "Retrieved vehicleId: '$vehicleId'")
        return vehicleId
    }

    fun saveDriverId(context: Context, driverId: String?) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(KEY_DRIVER_ID, driverId).apply()
    }

    fun getDriverId(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(KEY_DRIVER_ID, null)
    }

    fun isLoggedIn(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun logout(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().remove(KEY_TOKEN).remove(KEY_VEHICLE_ID).remove(KEY_IS_LOGGED_IN).apply()
    }

    fun saveFcmToken(context: Context, token: String) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getFcmToken(context: Context): String? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return prefs.getString(KEY_FCM_TOKEN, null)
    }
}
