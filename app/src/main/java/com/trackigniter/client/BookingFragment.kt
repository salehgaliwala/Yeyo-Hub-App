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

class BookingFragment : Fragment() {

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
                        nameText.text = it.name ?: "N/A"
                        tripsText.text = it.totalTrips.toString()
                        licenseText.text = it.licenseNo ?: "N/A"
                        expiryText.text = it.licenseExpiry ?: "N/A"
                        referenceIdText.text = it.referenceId ?: "N/A"

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
        val fullUrl = if (photoUrl.startsWith("http")) photoUrl
        else "https://rental.yeyocar.com/assets/uploads/$photoUrl"

        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL(fullUrl)
                val bitmap = android.graphics.BitmapFactory.decodeStream(url.openStream())
                if (bitmap != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        imageView.setImageBitmap(bitmap)
                        imageView.imageTintList = null
                        imageView.colorFilter = null
                    }
                }
            } catch (e: Exception) {
                Log.e("Booking", "Error loading profile photo", e)
            }
        }
    }
}
