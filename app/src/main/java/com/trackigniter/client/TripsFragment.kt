@file:Suppress("DEPRECATION")
package com.trackigniter.client

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.trackigniter.client.api.RetrofitClient
import com.trackigniter.client.api.models.Trip
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.trackigniter.client.data.TripRepository
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.io.File

class TripsFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private var refreshJob: kotlinx.coroutines.Job? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var adapter: TripsAdapter
    private lateinit var emptyView: TextView

    private var allTrips = listOf<Trip>()

    // Camera helpers (simplified for brevity, keeping existing logic if needed or assuming repository handles it)
    private var currentPhotoTripId: String? = null
    private var currentPhotoType: String = "start"

    // For Fuel Receipt
    private var fuelImageFile: File? = null
    private var fuelImageBitmap: android.graphics.Bitmap? = null

    private val fuelImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
         if (result.resultCode == android.app.Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? android.graphics.Bitmap
            if (imageBitmap != null) {
                fuelImageBitmap = imageBitmap
                Toast.makeText(context, "Receipt attached", Toast.LENGTH_SHORT).show()
                view?.findViewById<TextView>(R.id.txtImageName)?.text = "Receipt Captured"
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val imageBitmap = result.data?.extras?.get("data") as? android.graphics.Bitmap
            if (imageBitmap != null && currentPhotoTripId != null) {
                uploadProofImage(imageBitmap, currentPhotoTripId!!, currentPhotoType)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_trips, container, false)
        recyclerView = view.findViewById(R.id.recyclerViewTrips)
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout)
        tabLayout = view.findViewById(R.id.tabLayout)
        emptyView = view.findViewById(R.id.emptyView)

        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = TripsAdapter(mutableListOf(), this)
        recyclerView.adapter = adapter

        loadTrips()

        swipeRefreshLayout.setOnRefreshListener { loadTrips() }

        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) { filterAndShowTrips() }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        return view
    }

    private val refreshReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (isAdded) {
                loadTrips(isAuto = false) // Force refresh visual indicator? Or true for silent? Let's do silent but maybe add toast?
                // Actually if network comes back, users want to see it updating.
                // But let's use isAuto=true to avoid conflicting with manual swipe.
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startAutoRefresh()
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(refreshReceiver, android.content.IntentFilter("com.trackigniter.client.ACTION_REFRESH_DATA"))
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(requireContext())
                .unregisterReceiver(refreshReceiver)
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        refreshJob = viewLifecycleOwner.lifecycleScope.launch {
            if (isAdded) loadTrips(isAuto = true) // Run immediately on start/resume
            while (true) {
                kotlinx.coroutines.delay(20000)
                if (isAdded) loadTrips(isAuto = true)
            }
        }
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    private fun loadTrips(isAuto: Boolean = false) {
        if (!isAuto) swipeRefreshLayout.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val fetched = TripRepository.getTrips()
                if (isAdded) {
                    allTrips = fetched
                    filterAndShowTrips()
                }
            } catch (e: Exception) {
                if (!isAuto && isAdded) Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                if (isAdded) swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun filterAndShowTrips() {
        val isCompletedTab = tabLayout.selectedTabPosition == 1
        val filtered = if (isCompletedTab) {
            allTrips.filter { it.status?.trim().equals("Completed", ignoreCase = true) }
        } else {
            allTrips.filter { !it.status?.trim().equals("Completed", ignoreCase = true) }
        }

        // Auto-sync vehicleId for tracking:
        // If there's any active/started trip in the current list, make sure it's the active tracking vehicle
        if (!isCompletedTab) {
            android.util.Log.d("TripsFragment", "Checking ${filtered.size} trips for auto-sync...")
            val activeTrip = filtered.find {
                val st = it.status?.trim() ?: ""
                val drs = it.driverResponseStatus?.trim() ?: ""
                // Log each trip's status for debug
                val isFinal = st.equals("Completed", true) || st.equals("Rejected", true) || st.equals("Cancelled", true)
                val isPending = st.equals("Assigned", true) && !drs.equals("Accepted", true)
                val isMatch = !isFinal && !isPending && (drs.equals("Accepted", true) || st.length > 2)

                // Verbose log only if debug enabled
                if (SessionManager.isDebugEnabled(requireContext())) {
                    RemoteLogger.log(requireContext(), "DEBUG", "Trip ${it.id}: Status='$st' -> Match=$isMatch")
                }
                isMatch
            }

            if (activeTrip != null) {
                 RemoteLogger.log(requireContext(), "INFO", "TripsFragment: Found active trip ${activeTrip.id}, syncing vehicle ${activeTrip.vehicleApiUsername}")
            }


            if (activeTrip != null) {
                // Prefer vehicleApiUsername, then vehicleId
                val idsToTry = listOf(activeTrip.vehicleApiUsername, activeTrip.vehicleId)
                val bestId = idsToTry.firstOrNull { !it.isNullOrEmpty() && it != "0" }

                android.util.Log.d("TripsFragment", "Auto-syncing - Found Active Trip: ${activeTrip.id}. vehicleId=${activeTrip.vehicleId}, apiUsername=${activeTrip.vehicleApiUsername}. Selected: $bestId")

                if (bestId != null) {
                    SessionManager.saveVehicleId(requireContext(), bestId)
                } else {
                     android.util.Log.w("TripsFragment", "Active Trip found but no valid Vehicle ID available (Both 0 or null).")
                }
            } else {
                android.util.Log.d("TripsFragment", "No active trip found in this list.")
            }
        }

        if (filtered.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyView.text = if (isCompletedTab) "No completed trips" else "No active trips"
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }

        adapter.updateData(filtered, isCompletedTab)
    }

    // --- Actions ---

    fun onTripAction(trip: Trip, action: String) {
        when(action) {
            "ACCEPT" -> updateStatus(trip, "Accepted")
            "REJECT" -> showRejectDialog(trip)
            "FUEL" -> showFuelMmgt(trip)
            "EXPENSE" -> showExpenseMmgt(trip)
            "NAVIGATE" -> navigateTo(trip)
            "PROOF" -> showProofDialog(trip)
            "UPDATE_STATUS" -> showStatusSelectionDialog(trip)
            "INCIDENT" -> showIncidentDialog(trip)
            "DOCS" -> showDocsDialog(trip)
        }
    }

    private fun navigateTo(trip: Trip) {
        try {
            val gmmIntentUri = android.net.Uri.parse("google.navigation:q=${trip.to}")
            val mapIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Google Maps not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatus(trip: Trip, status: String, reason: String? = null, odometer: String? = null) {
        android.util.Log.d("TripsFragment", "Updating status for trip ${trip.id} to $status. Vehicle ID: ${trip.vehicleId}")
        lifecycleScope.launch {
            try {
                trip.id?.let { id ->
                    TripRepository.updateStatus(id, status, reason, odometer)

                    // If accepted or started, mark this as the active vehicle for tracking
                    if (status.contains("Accepted", true) || status.contains("Started", true)) {
                        android.util.Log.d("TripsFragment", "Saving vehicleId ${trip.vehicleId} to SessionManager")
                        SessionManager.saveVehicleId(requireContext(), trip.vehicleId)
                        SessionManager.saveDriverId(requireContext(), SessionManager.getDriverId(requireContext()))
                    } else if (status.contains("Completed", true) || status.contains("Rejected", true)) {
                        android.util.Log.d("TripsFragment", "Clearing vehicleId from SessionManager")
                        SessionManager.saveVehicleId(requireContext(), null)
                    }

                    loadTrips() // Refresh list
                    Toast.makeText(context, "Status updated to $status", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showStatusSelectionDialog(trip: Trip) {
        lifecycleScope.launch {
            try {
                val statuses = TripRepository.getStatuses()
                val items = statuses.map { it.status ?: "Unknown" }.toTypedArray()

                AlertDialog.Builder(requireContext())
                    .setTitle("Update Trip Status")
                    .setItems(items) { _, which ->
                        val selectedStatus = items[which]
                        if (selectedStatus.contains("Started", true) || selectedStatus.contains("Completed", true)) {
                            showOdometerPrompt(trip, selectedStatus)
                        } else {
                            updateStatus(trip, selectedStatus)
                        }
                    }
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showOdometerPrompt(trip: Trip, status: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_odometer_input, null)
        val title = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val message = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val input = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputOdometer)

        title.text = "Odometer Required"
        message.text = "Please enter current reading for '$status'"
        input.hint = "Reading (km)"

        AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Update Status") { _, _ ->
                val reading = input.text.toString()
                if (reading.isNotEmpty()) {
                    updateStatus(trip, status, odometer = reading)
                } else {
                    Toast.makeText(context, "Odometer is mandatory for '$status'", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFuelMmgt(trip: Trip) {
        showManagementDialog(trip, true)
    }

    private fun showExpenseMmgt(trip: Trip) {
        showManagementDialog(trip, false)
    }

    // Unified Bottom Sheet for Fuel/Expense
    private fun showManagementDialog(trip: Trip, isFuel: Boolean) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_trip_mgmt, null)
        bottomSheet.setContentView(view)

        val title = view.findViewById<TextView>(R.id.dialogTitle)
        val recycler = view.findViewById<RecyclerView>(R.id.recyclerEntries)
        val emptyText = view.findViewById<TextView>(R.id.emptyState)
        val input1 = view.findViewById<EditText>(R.id.input1)
        val input2 = view.findViewById<EditText>(R.id.input2)
        val inputOdo = view.findViewById<EditText>(R.id.inputOdometer)
        val btnAttach = view.findViewById<Button>(R.id.btnAttachImage)
        val txtImageName = view.findViewById<TextView>(R.id.txtImageName)
        val btnAdd = view.findViewById<Button>(R.id.btnAdd)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        title.text = if (isFuel) "Manage Fuel" else "Manage Expenses"
        input1.hint = "Amount" // Common
        input2.hint = if (isFuel) "Quantity" else "Description"
        inputOdo.visibility = if (isFuel) View.VISIBLE else View.GONE

        // Show/Hide Image Upload for Fuel
        if(isFuel) {
            btnAttach.visibility = View.VISIBLE
            txtImageName.visibility = View.VISIBLE
            fuelImageBitmap = null // Reset
            txtImageName.text = "No receipt selected"

            btnAttach.setOnClickListener {
                 val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                 fuelImageLauncher.launch(intent)
            }
        } else {
            btnAttach.visibility = View.GONE
            txtImageName.visibility = View.GONE
        }

        recycler.layoutManager = LinearLayoutManager(context)
        val adapter = InfoEntryAdapter(
            onDelete = { id, isFuelEntry ->
                // Delete Logic
                 lifecycleScope.launch {
                    try {
                        val req = com.trackigniter.client.api.models.DeleteRequest(id)
                        if (isFuelEntry) RetrofitClient.apiService.deleteFuel(req)
                        else RetrofitClient.apiService.deleteExpense(req)
                        loadMgmtData(trip.id!!, isFuel, recycler, emptyText) // Refresh
                    } catch(e: Exception) { Toast.makeText(context, "Error deleting", Toast.LENGTH_SHORT).show() }
                }
            },
            onReceiptClick = { imagePath ->
                val fullUrl = if (imagePath.startsWith("http")) imagePath
                              else "https://codeforts.com/trackigniter2/assets/uploads/$imagePath"
                downloadFile(fullUrl, "receipt_${System.currentTimeMillis()}.jpg")
            }
        )
        recycler.adapter = adapter

        // Load Initial Data
        loadMgmtData(trip.id!!, isFuel, recycler, emptyText)

        btnAdd.setOnClickListener {
            val v1 = input1.text.toString() // Amount
            val v2 = input2.text.toString() // Qty or Desc
            val v3 = inputOdo.text.toString()

            if (v1.isNotEmpty() && v2.isNotEmpty()) {
                 lifecycleScope.launch {
                    try {
                        val map = mutableMapOf(
                            "trip_id" to (trip.id ?: ""),
                            "vehicle_id" to (trip.vehicleId ?: ""),
                            "amount" to v1
                        )
                        if (isFuel) {
                            // Fuel Logic with Image
                            val vIdPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), trip.vehicleId ?: "")
                            val qtyPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), v2)
                            val odoPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), v3)
                            val pricePart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), v1)
                            val datePart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()))
                            val addedByPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), SessionManager.getDriverId(requireContext()) ?: "0")
                            val commentsPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), "App Entry")
                            val sourcePart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), "vendor")
                            val vendorPart = okhttp3.RequestBody.create("text/plain".toMediaTypeOrNull(), "Unknown") // Optional input could be added

                            var body: okhttp3.MultipartBody.Part? = null
                            if (fuelImageBitmap != null) {
                                val file = File(requireContext().cacheDir, "fuel_receipt_${System.currentTimeMillis()}.jpg")
                                val out = java.io.FileOutputStream(file)
                                fuelImageBitmap!!.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                                out.flush()
                                out.close()

                                val reqFile = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), file)
                                body = okhttp3.MultipartBody.Part.createFormData("fuel_image", file.name, reqFile)
                            }

                            RetrofitClient.apiService.addFuelWithImage(vIdPart, qtyPart, odoPart, pricePart, datePart, addedByPart, commentsPart, sourcePart, vendorPart, body)

                        } else {
                            map["title"] = v2
                            RetrofitClient.apiService.addExpense(map)
                        }

                        Toast.makeText(context, "Entry Added Successfully", Toast.LENGTH_SHORT).show()
                        bottomSheet.dismiss()

                    } catch(e: Exception) {
                        Toast.makeText(context, "Failed to add: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Fill required fields", Toast.LENGTH_SHORT).show()
            }
        }

        btnClose.setOnClickListener { bottomSheet.dismiss() }
        bottomSheet.show()
    }

    private fun loadMgmtData(tripId: String, isFuel: Boolean, recycler: RecyclerView, empty: TextView) {
        lifecycleScope.launch {
            try {
                val adapter = recycler.adapter as InfoEntryAdapter
                if (isFuel) {
                    val res = RetrofitClient.apiService.getFuel(tripId)
                    val list = res.body() ?: emptyList()
                    adapter.submitFuel(list)
                    empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    val res = RetrofitClient.apiService.getExpenses(tripId)
                    val list = res.body() ?: emptyList()
                    adapter.submitExpenses(list)
                    empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun showRejectDialog(trip: Trip) {
         // ... implementation uses dialog_simple_input ...
         // Keeping it brief, similar to showStartDialog but calls updateStatus with "Rejected"
    }


    private fun showProofDialog(trip: Trip) {
        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        layout.gravity = android.view.Gravity.CENTER

        val btnStart = com.google.android.material.button.MaterialButton(context)
        val isStarted = !trip.startProof.isNullOrEmpty()
        btnStart.text = if (isStarted) "Start Trip Proof (Uploaded)" else "Start Trip Proof"
        btnStart.setIconResource(R.drawable.ic_play)
        btnStart.setTextColor(android.graphics.Color.WHITE)
        btnStart.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        btnStart.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.primary, null))
        btnStart.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
        btnStart.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 24 }

        val btnEnd = com.google.android.material.button.MaterialButton(context)
        val isEnded = !trip.endProof.isNullOrEmpty()
        btnEnd.text = if (isEnded) "End Trip Proof (Uploaded)" else "End Trip Proof"
        btnEnd.setIconResource(R.drawable.ic_check)
        btnEnd.setTextColor(android.graphics.Color.WHITE)
        btnEnd.iconTint = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
        btnEnd.backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.primary, null))
        btnEnd.iconGravity = com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START
        btnEnd.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        layout.addView(btnStart)
        layout.addView(btnEnd)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Upload Proof")
            .setView(layout)
            .setNegativeButton("Cancel", null)
            .create()

        btnStart.setOnClickListener {
            dialog.dismiss()
            initiatePhotoCapture(trip.id, "start")
        }

        btnEnd.setOnClickListener {
            dialog.dismiss()
            initiatePhotoCapture(trip.id, "end")
        }

        dialog.show()
    }

    private fun initiatePhotoCapture(tripId: String?, type: String) {
        currentPhotoTripId = tripId
        currentPhotoType = type
        val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureLauncher.launch(intent)
    }

    private fun showIncidentDialog(trip: Trip) {
        val context = requireContext()
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)

        val issues = listOf("Tyres", "Engine", "Oil Leak", "Brakes", "Lights", "Vehicle Cleanliness", "Other")
        val checkboxes = mutableListOf<android.widget.CheckBox>()

        for (issue in issues) {
            val cb = android.widget.CheckBox(context)
            cb.text = issue
            checkboxes.add(cb)
            layout.addView(cb)
        }

        val input = EditText(context)
        input.hint = "Additional comments..."
        layout.addView(input)

        AlertDialog.Builder(context)
            .setTitle("Report Incident")
            .setView(layout)
            .setPositiveButton("Submit") { _, _ ->
                 val selected = checkboxes.filter { it.isChecked }.map { it.text.toString() }
                 val comment = input.text.toString()

                 if (selected.isEmpty() && comment.isBlank()) {
                     Toast.makeText(context, "Select an issue or add comments", Toast.LENGTH_SHORT).show()
                     return@setPositiveButton
                 }

                 val description = buildString {
                     if (selected.isNotEmpty()) append(selected.joinToString(", ")).append(". ")
                     if (comment.isNotBlank()) append(comment)
                 }

                 lifecycleScope.launch {
                     try {
                          val req = com.trackigniter.client.api.models.IncidentRequest(trip.vehicleId ?: "0", description)
                          RetrofitClient.apiService.reportIncident(req)
                          Toast.makeText(context, "Incident Reported", Toast.LENGTH_SHORT).show()
                     } catch(e: Exception) {
                          Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                     }
                 }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDocsDialog(trip: Trip) {
        val documents = trip.documents ?: emptyList()
        if (documents.isEmpty()) {
            Toast.makeText(context, "No documents available", Toast.LENGTH_SHORT).show()
            return
        }

        val context = requireContext()
        val listView = android.widget.ListView(context)

        val adapter = object : android.widget.ArrayAdapter<com.trackigniter.client.api.models.TripDocument>(context, R.layout.item_doc_row, R.id.docName, documents) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                // Manually inflate to ensure we target our custom layout
                val inflater = LayoutInflater.from(context)
                val view = convertView ?: inflater.inflate(R.layout.item_doc_row, parent, false)

                val doc = getItem(position)
                val nameView = view.findViewById<TextView>(R.id.docName)
                val actionView = view.findViewById<TextView>(R.id.docAction)

                nameView.text = doc?.name ?: "Unnamed Doc"
                actionView.text = "Tap to download"

                return view
            }
        }

        listView.adapter = adapter

        val dialog = AlertDialog.Builder(context)
            .setTitle("Trip Documents")
            .setView(listView)
            .setPositiveButton("Close", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val doc = documents[position]
             if (!doc.url.isNullOrEmpty()) {
                 downloadFile(doc.url, doc.name ?: "document_${System.currentTimeMillis()}")
                 Toast.makeText(context, "Downloading...", Toast.LENGTH_SHORT).show()
             }
        }

        dialog.show()
    }

    private fun downloadFile(url: String, fileName: String) {
        val sanitizedUrl = url.replace(" ", "%20")
        Toast.makeText(requireContext(), "Downloading from: $sanitizedUrl", Toast.LENGTH_LONG).show() // Debug Toast

        try {
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(sanitizedUrl))
            request.setTitle(fileName)
            request.setDescription("Downloading content...")
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "$fileName.pdf")
            request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
            request.setAllowedOverMetered(true)
            request.setAllowedOverRoaming(true)

            val manager = requireContext().getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            manager.enqueue(request)
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
            // Fallback to browser
            try {
                val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(sanitizedUrl))
                browserIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(browserIntent)
            } catch (e2: Exception) {
                 Toast.makeText(context, "Browser open failed: ${e2.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadProofImage(bitmap: android.graphics.Bitmap, tripId: String, type: String) {
        lifecycleScope.launch {
            try {
                // Save bitmap to temp file
                val file = File(requireContext().cacheDir, "proof_${tripId}_${type}.jpg")
                val out = java.io.FileOutputStream(file)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
                out.close()

                TripRepository.uploadProof(tripId, type, file)
                Toast.makeText(context, "Proof uploaded successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Proof upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- Adapter ---

    inner class TripsAdapter(
        private var trips: MutableList<Trip>,
        private val fragment: TripsFragment
    ) : RecyclerView.Adapter<TripsAdapter.TripViewHolder>() {

        private var isCompletedList = false

        fun updateData(newTrips: List<Trip>, isCompleted: Boolean) {
            trips.clear()
            trips.addAll(newTrips)
            isCompletedList = isCompleted
            notifyDataSetChanged()
        }

        inner class TripViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tripId: TextView? = itemView.findViewById(R.id.tripId)
            val date: TextView? = itemView.findViewById(R.id.tripDateTime)
            val from: TextView? = itemView.findViewById(R.id.tripFrom)
            val to: TextView? = itemView.findViewById(R.id.tripTo)
            val status: TextView? = itemView.findViewById(R.id.tripStatus)
            val layoutDecision: View? = itemView.findViewById(R.id.layoutDecision)
            val btnAccept: View? = itemView.findViewById(R.id.btnAccept)
            val btnReject: View? = itemView.findViewById(R.id.btnReject)
            val layoutInProgress: View? = itemView.findViewById(R.id.layoutInProgress)
            val btnFuel: View? = itemView.findViewById(R.id.btnFuel)
            val btnExpense: View? = itemView.findViewById(R.id.btnExpense)
            val btnProof: View? = itemView.findViewById(R.id.btnProof)
            val btnNavigate: View? = itemView.findViewById(R.id.btnNavigate)
            val btnUpdateStatus: View? = itemView.findViewById(R.id.btnUpdateStatus)
            val layoutStatusInfo: View? = itemView.findViewById(R.id.layoutStatusInfo)

            val scheduledStart: TextView? = itemView.findViewById(R.id.tripScheduledStart)
            val scheduledEnd: TextView? = itemView.findViewById(R.id.tripScheduledEnd)
            val stops: TextView? = itemView.findViewById(R.id.tripStops)
            val layoutStops: View? = itemView.findViewById(R.id.layoutStops)

            val customer: TextView? = itemView.findViewById(R.id.tripCustomer)
            val vehicle: TextView? = itemView.findViewById(R.id.tripVehicle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
            return try {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_trip, parent, false)
                TripViewHolder(view)
            } catch (e: Exception) {
                // Fallback to minimal view if inflation fails
                val tv = TextView(parent.context)
                tv.text = "Layout Error"
                TripViewHolder(tv)
            }
        }

        override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
            try {
                val trip = trips[position]

                holder.tripId?.text = "#TRIP-${trip.bookingId ?: trip.id}"
                holder.date?.text = trip.startDate ?: "Date N/A"
                holder.from?.text = trip.from ?: "N/A"
                holder.to?.text = trip.to ?: "N/A"

                holder.customer?.text = trip.customerName ?: "N/A"
                val vehicleInfo = buildString {
                    append(trip.vehicleName ?: "N/A")
                    if (!trip.vehicleRegNo.isNullOrEmpty()) {
                        append(" (${trip.vehicleRegNo})")
                    }
                }
                holder.vehicle?.text = vehicleInfo

                holder.status?.text = trip.status ?: "Assigned"

                holder.layoutDecision?.visibility = View.GONE
                holder.layoutInProgress?.visibility = View.GONE
                holder.btnUpdateStatus?.visibility = View.GONE
                holder.layoutStatusInfo?.visibility = View.GONE

                // Populate more fields
                holder.scheduledStart?.text = trip.startDate ?: "N/A"
                holder.scheduledEnd?.text = trip.endDate ?: "N/A"
                if (!trip.stops.isNullOrEmpty()) {
                    var finalStops = "None"
                    try {
                         // Try parsing as JSON Array first
                         val jsonArray = org.json.JSONArray(trip.stops)
                         val processed = ArrayList<String>()
                         for (i in 0 until jsonArray.length()) {
                             val fullAddr = jsonArray.getString(i)
                             // Take first 2 parts of address
                             val parts = fullAddr.split(",").take(2).joinToString(", ")
                             processed.add(parts)
                         }
                         finalStops = processed.joinToString("   ➜   ")
                    } catch (e: Exception) {
                         // Fallback: It might be a plain string or already formatted
                         // If it looks like a list "A, B", treat commas as separators?
                         // No, addresses have commas. Just display as is but clean brackets.
                         finalStops = trip.stops!!
                             .replace("[", "").replace("]", "")
                             .replace("\"", "")
                             // Do NOT replace commas blindly
                    }

                    holder.stops?.text = finalStops
                    holder.layoutStops?.visibility = View.VISIBLE
                } else {
                    holder.layoutStops?.visibility = View.GONE
                }

                val drs = trip.driverResponseStatus?.trim() ?: "Pending"
                val st = trip.status?.trim() ?: ""

                // Improved Acceptance Check:
                // A trip is considered accepted if driver status is 'Accepted' OR it's not 'Pending'/'Rejected'
                val isAcceptedByDriver = drs.equals("Accepted", true) || (!drs.equals("Pending", true) && !drs.isNullOrBlank())

                if (isCompletedList || st.equals("Completed", true) || st.equals("Rejected", true)) {
                    // No actions
                    if (st.equals("Completed", true)) holder.layoutStatusInfo?.visibility = View.VISIBLE
                } else if (!isAcceptedByDriver) {
                    // Driver hasn't accepted yet
                    holder.layoutDecision?.visibility = View.VISIBLE
                    holder.layoutStatusInfo?.visibility = View.GONE // Hide status info until accepted
                } else {
                    // Driver has accepted
                    holder.layoutStatusInfo?.visibility = View.VISIBLE
                    holder.btnUpdateStatus?.visibility = View.VISIBLE
                    holder.layoutInProgress?.visibility = View.VISIBLE // Show Nav, Fuel, etc. once accepted
                }

                holder.btnAccept?.setOnClickListener { fragment.onTripAction(trip, "ACCEPT") }
                holder.btnReject?.setOnClickListener { fragment.onTripAction(trip, "REJECT") }

                // Restored Actions
                holder.btnFuel?.setOnClickListener { fragment.onTripAction(trip, "FUEL") }
                holder.btnExpense?.setOnClickListener { fragment.onTripAction(trip, "EXPENSE") }
                holder.btnProof?.setOnClickListener { fragment.onTripAction(trip, "PROOF") }
                holder.btnNavigate?.setOnClickListener { fragment.onTripAction(trip, "NAVIGATE") }
                holder.btnUpdateStatus?.setOnClickListener { fragment.onTripAction(trip, "UPDATE_STATUS") }

                // New Buttons
                holder.itemView.findViewById<View>(R.id.btnIncident).setOnClickListener { fragment.onTripAction(trip, "INCIDENT") }
                holder.itemView.findViewById<View>(R.id.btnDocuments).setOnClickListener { fragment.onTripAction(trip, "DOCS") }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun getItemCount() = trips.size
    }
}
