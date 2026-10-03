package com.trackigniter.client.api.models

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val token: String? = null,
    @SerializedName("mobile_debug_enabled") val mobileDebugEnabled: Boolean? = false
)

data class LogoutResponse(
    val status: String? = null,
    val message: String? = null
)

data class Trip(
    @SerializedName("id", alternate = ["trip_id", "t_id"])
    val id: String?,
    @SerializedName("t_trip_fromlocation", alternate = ["from", "from_location"])
    val from: String?,
    @SerializedName("t_trip_tolocation", alternate = ["to", "to_location"])
    val to: String?,
    @SerializedName("cost", alternate = ["amount"])
    val cost: String?,
    @SerializedName("t_trip_status", alternate = ["status"])
    var status: String?,
    @SerializedName("t_vehicle", alternate = ["vehicle_id", "t_vechicle"])
    var vehicleId: String?, 
    @SerializedName("t_trip_stops")
    val stops: String?,
    @SerializedName("t_trip_final_amount")
    val finalAmount: String?,
    @SerializedName("t_start_date")
    val startDate: String?,
    @SerializedName("t_end_date")
    val endDate: String?,
    @SerializedName("driver_response_status")
    val driverResponseStatus: String?,
    @SerializedName("v_api_username")
    val vehicleApiUsername: String?,
    @SerializedName("t_bookingid")
    val bookingId: String?,
    @SerializedName("c_name")
    val customerName: String?,
    @SerializedName("v_name")
    val vehicleName: String?,
    @SerializedName("v_registration_no")
    val vehicleRegNo: String?,
    @SerializedName("documents")
    val documents: List<TripDocument>? = null,
    var rejectionReason: String? = null,
    @SerializedName("t_start_proof") val startProof: String? = null,
    @SerializedName("t_end_proof") val endProof: String? = null
)

data class TripDocument(
    @SerializedName("td_name") val name: String?,
    @SerializedName("td_file_url") val url: String?
)

data class IncidentRequest(
    @SerializedName("v_id") val vehicleId: String,
    @SerializedName("description") val description: String
)

data class StatusOption(
    @SerializedName("tsm_id")
    val id: String?,
    @SerializedName("tsm_name")
    val status: String?
)

data class UpdateStatusRequest(
    val trip_id: String,
    val status: String,
    val reason: String? = null,
    val odometer_reading: String? = null
)

data class FuelEntryRequest(
    val v_id: String,
    val fuel_quantity: String,
    val odometerreading: String,
    val fuelprice: String,
    val fuelfilldate: String,
    val fueladdedby: String
)

data class TripExpenseRequest(
    val trip_id: String,
    val vehicle_id: String,
    val driver_id: String,
    val expense_date: String,
    val expense_for: String,
    val amount: Double,
    val notes: String,
    val showinvoice: Int = 0,
    val includetocustomer: Int = 0
)

data class LocationRequest(
    val v_id: String?, // Can be Vehicle ID or Driver ID (if no active vehicle)
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Double,
    val bearing: Double,
    val accuracy: Double,
    val provider: String?,
    val battery_level: Double?,
    val time: String? = null
)

data class ChatMessage(
    @SerializedName("msg_id")
    val id: Int?,
    @SerializedName("msg_driver_id")
    val driverId: Int,
    @SerializedName("msg_is_sender_driver")
    val isSenderDriver: Int, // 1 for driver, 0 for admin
    @SerializedName("msg_message")
    val message: String,
    @SerializedName("msg_created_at")
    val createdAt: String?,
    @SerializedName("msg_is_read")
    val isRead: Int
)

data class ChatSendRequest(
    val message: String
)

data class DriverProfile(
    val name: String?,
    @SerializedName("license_no")
    val licenseNo: String?,
    @SerializedName("license_expiry")
    val licenseExpiry: String?,
    @SerializedName("total_trips")
    val totalTrips: Int,
    val photo: String?,
    @SerializedName("reference_id")
    val referenceId: String? = null
)

data class Expense(
    @SerializedName("te_id") val id: String,
    @SerializedName("te_expense_for") val title: String,
    @SerializedName("te_amount") val amount: String,
    @SerializedName("te_expense_date") val date: String
)

data class Fuel(
    @SerializedName("v_fuel_id") val id: String,
    @SerializedName("v_fuel_quantity") val quantity: String,
    @SerializedName("v_fuelprice") val price: String,
    @SerializedName("v_odometerreading") val odometer: String,
    @SerializedName("v_fuelfilldate") val date: String,
    @SerializedName("v_fuel_image") val image: String?
)

data class DeleteRequest(val id: String)

data class LogEntry(
    @SerializedName("driver_id") val driverId: String?,
    @SerializedName("vehicle_id") val vehicleId: String?,
    @SerializedName("level") val level: String,
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: String
)
