package com.trackigniter.client

import com.google.gson.Gson
import com.trackigniter.client.api.models.DriverProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DriverProfileTest {

    @Test
    fun testDriverProfileDeserialization() {
        val json = """
            {
                "name": "John Doe",
                "license_no": "DL12345",
                "license_expiry": "2030-01-01",
                "total_trips": 42,
                "photo": "driver_123.jpg",
                "reference_id": "REF98765"
            }
        """.trimIndent()

        val gson = Gson()
        val profile = gson.fromJson(json, DriverProfile::class.java)

        assertNotNull(profile)
        assertEquals("John Doe", profile.name)
        assertEquals("DL12345", profile.licenseNo)
        assertEquals("2030-01-01", profile.licenseExpiry)
        assertEquals(42, profile.totalTrips)
        assertEquals("driver_123.jpg", profile.photo)
        assertEquals("REF98765", profile.referenceId)
    }
}
