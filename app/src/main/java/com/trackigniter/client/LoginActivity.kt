package com.trackigniter.client

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trackigniter.client.api.RetrofitClient
import com.trackigniter.client.api.models.LoginRequest
import kotlinx.coroutines.launch
import com.google.firebase.messaging.FirebaseMessaging

class LoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SessionManager.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        supportActionBar?.hide() // Hide Action Bar
        setContentView(R.layout.activity_login)

        checkPermissions()

        val usernameInput = findViewById<EditText>(R.id.usernameInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val loginButton = findViewById<Button>(R.id.loginButton)

        passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                loginButton.performClick()
                true
            } else {
                false
            }
        }

        loginButton.setOnClickListener {
            val username = usernameInput.text.toString()
            val password = passwordInput.text.toString()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                loginButton.isEnabled = false // Prevent double clicks
                val originalText = loginButton.text
                loginButton.text = "Checking Credentials..."

                lifecycleScope.launch {
                    try {
                        val response = RetrofitClient.apiService.login(LoginRequest(username, password))
                        if (response.isSuccessful) {
                            val body = response.body()
                            // Logic: If status is 200, we consider it success.
                            // If 'token' is in body, use it.
                            // If 'success' field exists and is false, then fail.
                            if (body != null) {
                                val token = body.token
                                val isSuccess = body.success ?: true // Default to true if success field missing but 200 OK

                                if (isSuccess && !token.isNullOrEmpty()) {
                                    SessionManager.saveToken(this@LoginActivity, token)
                                    SessionManager.saveDriverId(this@LoginActivity, username)

                                    val debugEnabled = body.mobileDebugEnabled ?: false
                                    SessionManager.saveDebugEnabled(this@LoginActivity, debugEnabled)

                                    if (debugEnabled) {
                                        RemoteLogger.log(this@LoginActivity, "INFO", "Login successful. Driver: $username. Debug: Enabled")
                                    }
                                    // Auto-start tracking service
                                    try {
                                        val serviceIntent = Intent(this@LoginActivity, TrackingService::class.java)
                                        androidx.core.content.ContextCompat.startForegroundService(this@LoginActivity, serviceIntent)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    // 2. Refresh FCM Token (Crucial for Push Notifications)
                                    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val fcmToken = task.result
                                            if (!fcmToken.isNullOrEmpty()) {
                                                SessionManager.saveFcmToken(this@LoginActivity, fcmToken)
                                                // Send to server in background
                                                lifecycleScope.launch {
                                                    try {
                                                        // This call ensures the server ties the token to the user immediately
                                                        com.trackigniter.client.data.TripRepository.updateFcmToken(fcmToken)
                                                        RemoteLogger.log(this@LoginActivity, "INFO", "FCM Token updated on login.")
                                                    } catch (e: Exception) {
                                                        RemoteLogger.log(this@LoginActivity, "ERROR", "Failed to update FCM token on login: ${e.message}")
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                        // Proceed to Main Activity regardless of FCM success to avoid blocking user
                                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                                        finish()
                                    }
                                } else {
                                     val msg = body.message ?: "Login failed (No token or success=false)"
                                     Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                // Empty body on 200 OK?
                                Toast.makeText(this@LoginActivity, "Login failed: Empty response from server", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            val errorMsg = try {
                                response.errorBody()?.string() ?: response.message()
                            } catch (e: Exception) {
                                response.message()
                            }
                            Toast.makeText(this@LoginActivity, "Login failed: $errorMsg (Code: ${response.code()})", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    } finally {
                        loginButton.isEnabled = true
                        loginButton.text = originalText
                    }
                }
            } else {
                Toast.makeText(this, R.string.login_error_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun checkPermissions() {
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
        }
    }
}
