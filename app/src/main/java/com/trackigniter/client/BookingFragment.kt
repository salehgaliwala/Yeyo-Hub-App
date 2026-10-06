package com.trackigniter.client

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trackigniter.client.api.RetrofitClient
import kotlinx.coroutines.launch

open class BookingFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_booking, container, false)
        fetchProfile(view)
        return view
    }

    private fun fetchProfile(view: View) {
        val profileImage = view.findViewById<ImageView>(R.id.profileImage)
        val nameText = view.findViewById<TextView>(R.id.profileName)
        val tripsText = view.findViewById<TextView>(R.id.profileTotalTrips)
        val licenseText = view.findViewById<TextView>(R.id.profileLicenseNo)
        val expiryText = view.findViewById<TextView>(R.id.profileLicenseExpiry)
        val referenceIdText = view.findViewById<TextView>(R.id.profileReferenceId)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = RetrofitClient.apiService.getProfile()
                if (response.isSuccessful) {
                    val profile = response.body()
                    profile?.let {
                        nameText.text = if (!it.name.isNullOrBlank()) it.name else "N/A"
                        tripsText.text = it.totalTrips.toString()
                        licenseText.text = if (!it.licenseNo.isNullOrBlank()) it.licenseNo else "N/A"
                        expiryText.text = if (!it.licenseExpiry.isNullOrBlank()) it.licenseExpiry else "N/A"
                        referenceIdText.text = if (!it.referenceId.isNullOrBlank()) it.referenceId else "N/A"

                        val photoUrl = it.photo
                        if (!photoUrl.isNullOrBlank()) {
                            loadProfileImage(profileImage, photoUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Booking", "Error fetching profile", e)
            }
        }
    }

    private fun loadProfileImage(imageView: ImageView, photoUrl: String) {
        var fullUrl = photoUrl.trim()
        if (!fullUrl.startsWith("http://") && !fullUrl.startsWith("https://")) {
            fullUrl = "https://rental.yeyocar.com/assets/uploads/$fullUrl"
        }
        if (fullUrl.startsWith("http://")) {
            fullUrl = fullUrl.replaceFirst("http://", "https://")
        }

        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(fullUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                imageView.setImageBitmap(bitmap)
                                imageView.imageTintList = null
                                imageView.colorFilter = null
                            }
                        }
                    }
                } else {
                    Log.e("Booking", "Failed to download image: ${response.code} from $fullUrl")
                }
            } catch (e: Exception) {
                Log.e("Booking", "Error loading profile photo from $fullUrl", e)
            }
        }
    }
}
