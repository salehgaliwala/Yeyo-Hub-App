package com.trackigniter.client

import android.view.Menu
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Session Check
        if (!SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main_drawer)
        
        // Setup Toolbar if it exists, otherwise Default ActionBar is used by Theme
        // We rely on the Activity's default ActionBar.

        if (savedInstanceState == null) {
            setupNavigation()
            // Default to Home (Trips)
            switchToFragment(TripsFragment(), getString(R.string.trips_title), R.id.nav_home)
            
            checkAndRequestPermissions()
        } else {
            setupNavigation()
        }

        // Prevent screen from locking while app is active (logged in)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun setupNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchToFragment(TripsFragment(), getString(R.string.trips_title))
                    true
                }
                R.id.nav_status -> {
                    switchToFragment(StatusFragment(), "Status Logs")
                    true
                }
                R.id.nav_booking -> {
                    switchToFragment(BookingFragment(), "Info")
                    true
                }
                R.id.nav_chat -> {
                    switchToFragment(ChatFragment(), "Driver Chat")
                    true
                }
                R.id.nav_settings -> {
                    switchToFragment(MainFragment(), "App Settings")
                    true
                }
                else -> false
            }
        }
    }

    private fun switchToFragment(fragment: Fragment, title: String, menuItemId: Int? = null) {
        replaceFragment(fragment)
        supportActionBar?.title = title
        menuItemId?.let {
            findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = it
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationEnabled()
    }

    private var locationAlertDialog: AlertDialog? = null

    private fun checkLocationEnabled() {
        val locationManager = getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)

        if (!isGpsEnabled) {
            if (locationAlertDialog == null || !locationAlertDialog!!.isShowing) {
                locationAlertDialog = AlertDialog.Builder(this)
                    .setTitle("Location Disabled")
                    .setMessage("Location Services must be enabled to use this app.")
                    .setCancelable(false)
                    .setPositiveButton("Enable") { _, _ ->
                        startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                    .show()
            }
        } else {
            if (locationAlertDialog != null && locationAlertDialog!!.isShowing) {
                locationAlertDialog!!.dismiss()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.CAMERA
        )

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val toRequest = permissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 1)
        } else {
            startTrackingService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startTrackingService()
            }
        }
    }

    private fun startTrackingService() {
        val intent = Intent(this, TrackingService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_options, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                showLogoutConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                performLogout()
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    private fun performLogout() {
        // Call Logout API
        lifecycleScope.launch {
            try {
                com.trackigniter.client.api.RetrofitClient.apiService.logout()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Clear Local Session
                SessionManager.logout(this@MainActivity)
                
                // Stop the tracking service
                val intent = Intent(this@MainActivity, TrackingService::class.java)
                stopService(intent)
        
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }
}
