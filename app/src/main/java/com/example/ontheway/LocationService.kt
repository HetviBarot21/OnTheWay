package com.example.ontheway

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.ontheway.services.CircleService
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Contact(
    val email: String = "",
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val notified2Min: Boolean = false,
    val notifiedArrived: Boolean = false
)

data class UserLocation(
    val userId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L
)

class LocationService(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val geofencingClient: GeofencingClient =
        LocationServices.getGeofencingClient(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val circleService = CircleService()
    private val httpClient = OkHttpClient()

    private var locationCallback: LocationCallback? = null
    private var lastETACheck = 0L
    private val ETA_CHECK_INTERVAL = 30000L // 30 seconds

    fun startLocationUpdates(onLocationUpdate: (Location) -> Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10000L // 10 seconds
        ).apply {
            setMinUpdateIntervalMillis(5000L) // 5 seconds
            setMaxUpdateDelayMillis(2000L) // Max 2 second delay
            setMinUpdateDistanceMeters(5f) // Only update if moved 5 meters
            setWaitForAccurateLocation(true) // Wait for accurate location
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // Only use location if accuracy is good (< 50 meters)
                    if (location.accuracy < 50f) {
                        onLocationUpdate(location)
                        updateLocationInFirestore(location)
                    }
                    
                    // Update location for all circles
                    CoroutineScope(Dispatchers.IO).launch {
                        circleService.updateLocationForCircles(
                            location.latitude,
                            location.longitude,
                            location.speed,
                            location.accuracy
                        )
                        
                        // Check ETA every 30 seconds
                        if (System.currentTimeMillis() - lastETACheck > ETA_CHECK_INTERVAL) {
                            checkAndNotifyContacts(location)
                            lastETACheck = System.currentTimeMillis()
                        }
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            null
        )
    }

    fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
    }

    private fun updateLocationInFirestore(location: Location) {
        val userId = auth.currentUser?.uid ?: return
        
        val userLocation = UserLocation(
            userId = userId,
            latitude = location.latitude,
            longitude = location.longitude,
            timestamp = System.currentTimeMillis()
        )

        firestore.collection("locations")
            .document(userId)
            .set(userLocation)
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    // Add geofence for a location
    fun addGeofence(
        geofenceId: String,
        latitude: Double,
        longitude: Double,
        radius: Float = 100f
    ) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        geofencingClient.addGeofences(geofencingRequest, pendingIntent)
            .addOnSuccessListener {
                // Geofence added successfully
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    // Remove geofence
    fun removeGeofence(geofenceId: String) {
        geofencingClient.removeGeofences(listOf(geofenceId))
            .addOnSuccessListener {
                // Geofence removed
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    suspend fun addContact(contactEmail: String, destinationLat: Double, destinationLng: Double) {
        val userId = auth.currentUser?.uid ?: return
        
        val contact = Contact(
            email = contactEmail,
            destinationLat = destinationLat,
            destinationLng = destinationLng,
            notified2Min = false,
            notifiedArrived = false
        )

        firestore.collection("users")
            .document(userId)
            .collection("contacts")
            .document(contactEmail)
            .set(contact)
            .await()
        
        // Add geofence for destination
        addGeofence(
            "contact_$contactEmail",
            destinationLat,
            destinationLng,
            100f
        )
    }

    suspend fun removeContact(contactEmail: String) {
        val userId = auth.currentUser?.uid ?: return
        
        firestore.collection("users")
            .document(userId)
            .collection("contacts")
            .document(contactEmail)
            .delete()
            .await()
        
        // Remove geofence
        removeGeofence("contact_$contactEmail")
    }

    suspend fun getContacts(): List<Contact> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("contacts")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { it.toObject(Contact::class.java) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    fun calculateETA(distanceMeters: Double, speedMps: Double = 13.89): Int {
        // Default speed: 13.89 m/s = 50 km/h
        return if (speedMps > 0) {
            (distanceMeters / speedMps / 60).toInt() // minutes
        } else {
            (distanceMeters / 13.89 / 60).toInt()
        }
    }

    // Calculate ETA using Google Distance Matrix API
    suspend fun calculateETAWithAPI(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String
    ): Int? {
        return try {
            val url = "https://maps.googleapis.com/maps/api/distancematrix/json?" +
                    "origins=$originLat,$originLng" +
                    "&destinations=$destLat,$destLng" +
                    "&mode=driving" +
                    "&key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .build()

            val response = httpClient.newCall(request).execute()
            val jsonResponse = JSONObject(response.body?.string() ?: "")

            if (jsonResponse.getString("status") == "OK") {
                val rows = jsonResponse.getJSONArray("rows")
                val elements = rows.getJSONObject(0).getJSONArray("elements")
                val element = elements.getJSONObject(0)

                if (element.getString("status") == "OK") {
                    val duration = element.getJSONObject("duration")
                    val seconds = duration.getInt("value")
                    return seconds / 60 // Convert to minutes
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun checkAndNotifyContacts(currentLocation: Location) {
        val userId = auth.currentUser?.uid ?: return
        val contacts = getContacts()

        for (contact in contacts) {
            val distance = calculateDistance(
                currentLocation.latitude,
                currentLocation.longitude,
                contact.destinationLat,
                contact.destinationLng
            )

            val eta = calculateETA(distance, currentLocation.speed.toDouble())

            // Notify when 2 minutes away
            if (eta <= 2 && !contact.notified2Min && distance > 100) {
                sendNotification(contact.email, "2_minutes", eta)
                updateContactNotification(contact.email, "notified2Min", true)
            }

            // Notify when arrived (within 100 meters)
            if (distance <= 100 && !contact.notifiedArrived) {
                sendNotification(contact.email, "arrived", 0)
                updateContactNotification(contact.email, "notifiedArrived", true)
            }
        }
    }

    private suspend fun updateContactNotification(contactEmail: String, field: String, value: Boolean) {
        val userId = auth.currentUser?.uid ?: return
        
        firestore.collection("users")
            .document(userId)
            .collection("contacts")
            .document(contactEmail)
            .update(field, value)
            .await()
    }

    private fun sendNotification(recipientEmail: String, type: String, eta: Int) {
        val userId = auth.currentUser?.uid ?: return
        val userName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Someone"

        // Get recipient's FCM token from Firestore
        firestore.collection("users")
            .whereEqualTo("email", recipientEmail)
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val fcmToken = document.getString("fcmToken")
                    if (fcmToken != null) {
                        // Send notification via FCM
                        val notificationData = hashMapOf(
                            "token" to fcmToken,
                            "from" to userName,
                            "type" to type,
                            "eta" to eta,
                            "timestamp" to System.currentTimeMillis()
                        )
                        
                        // Queue notification for Cloud Function to send
                        firestore.collection("notifications")
                            .add(notificationData)
                            .addOnSuccessListener {
                                // Also show local notification for testing
                                val message = when (type) {
                                    "2_minutes" -> "$userName is 2 minutes away (ETA: $eta min)"
                                    "arrived" -> "$userName has arrived"
                                    else -> "Location update from $userName"
                                }
                                NotificationHelper.showLocalNotification(
                                    context,
                                    "OnTheWay Update",
                                    message
                                )
                            }
                            .addOnFailureListener { e ->
                                e.printStackTrace()
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }
    
    suspend fun saveFCMToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        
        firestore.collection("users")
            .document(userId)
            .update("fcmToken", token)
            .await()
    }
}

// Geofence Broadcast Receiver
class GeofenceBroadcastReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null || geofencingEvent.hasError()) {
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // User entered geofence
            NotificationHelper.showLocalNotification(
                context,
                "Arrived",
                "You have arrived at your destination"
            )
        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            // User left geofence
            NotificationHelper.showLocalNotification(
                context,
                "Left",
                "You have left the location"
            )
        }
    }
}
