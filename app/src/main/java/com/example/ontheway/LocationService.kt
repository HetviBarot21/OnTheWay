package com.example.ontheway

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
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
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    onLocationUpdate(location)
                    updateLocationInFirestore(location)
                    
                    // Get battery info
                    val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val batteryLevel = batteryStatus?.let {
                        val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        (level * 100 / scale.toFloat()).toInt()
                    } ?: 0
                    val isCharging = batteryStatus?.let {
                        val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        status == BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == BatteryManager.BATTERY_STATUS_FULL
                    } ?: false
                    
                    // Update location for all circles
                    CoroutineScope(Dispatchers.IO).launch {
                        circleService.updateLocationForCircles(
                            location.latitude,
                            location.longitude,
                            location.speed,
                            location.accuracy,
                            batteryLevel,
                            isCharging
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
                android.util.Log.d("LocationService", "Geofence added successfully")
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    fun removeGeofence(geofenceId: String) {
        geofencingClient.removeGeofences(listOf(geofenceId))
            .addOnSuccessListener {
                android.util.Log.d("LocationService", "Geofence removed")
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    suspend fun addContact(contactEmail: String, recipientUserId: String, destinationLat: Double, destinationLng: Double) {
        val userId = auth.currentUser?.uid ?: run {
            android.util.Log.e("LocationService", "User not authenticated")
            return
        }
        
        android.util.Log.d("LocationService", "Adding contact: $contactEmail (userId: $recipientUserId)")
        
        val contact = Contact(
            email = contactEmail,
            destinationLat = destinationLat,
            destinationLng = destinationLng,
            notified2Min = false,
            notifiedArrived = false
        )

        try {
            // Save to sender's contacts
            firestore.collection("users")
                .document(userId)
                .collection("contacts")
                .document(contactEmail)
                .set(contact)
                .await()
            
            android.util.Log.d("LocationService", "Contact saved")
            
            // Create a shared ride document so recipient can see it
            val rideData = hashMapOf(
                "senderId" to userId,
                "senderEmail" to (auth.currentUser?.email ?: ""),
                "recipientId" to recipientUserId,
                "recipientEmail" to contactEmail,
                "destinationLat" to destinationLat,
                "destinationLng" to destinationLng,
                "startTime" to System.currentTimeMillis(),
                "active" to true
            )
            
            firestore.collection("activeRides")
                .document("${userId}_${recipientUserId}")
                .set(rideData)
                .await()
            
            android.util.Log.d("LocationService", "Active ride created: ${userId}_${recipientUserId}")
            
            addGeofence(
                "contact_$contactEmail",
                destinationLat,
                destinationLng,
                100f
            )
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "Failed to add contact", e)
            throw e
        }
    }
    
    suspend fun removeContact(contactEmail: String) {
        val userId = auth.currentUser?.uid ?: return
        
        firestore.collection("users")
            .document(userId)
            .collection("contacts")
            .document(contactEmail)
            .delete()
            .await()
        
        // Also remove any active rides with this email
        try {
            val snapshot = firestore.collection("activeRides")
                .whereEqualTo("senderId", userId)
                .whereEqualTo("recipientEmail", contactEmail)
                .limit(1)
                .get()
                .await()
            
            snapshot.documents.firstOrNull()?.reference?.delete()?.await()
            android.util.Log.d("LocationService", "Active ride removed")
        } catch (e: Exception) {
            android.util.Log.w("LocationService", "Could not find active ride to delete", e)
        }
        
        removeGeofence("contact_$contactEmail")
    }
    
    suspend fun getIncomingRides(): List<Map<String, Any>> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        
        android.util.Log.d("LocationService", "Checking incoming rides for userId: $userId")
        
        return try {
            val snapshot = firestore.collection("activeRides")
                .whereEqualTo("recipientId", userId)
                .whereEqualTo("active", true)
                .get()
                .await()
            
            android.util.Log.d("LocationService", "Found ${snapshot.documents.size} incoming rides")
            
            val rides = snapshot.documents.mapNotNull { doc ->
                android.util.Log.d("LocationService", "Incoming ride: ${doc.id} - ${doc.data}")
                doc.data
            }
            
            rides
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "Error getting incoming rides", e)
            emptyList()
        }
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
        return if (speedMps > 0) {
            (distanceMeters / speedMps / 60).toInt()
        } else {
            (distanceMeters / 13.89 / 60).toInt()
        }
    }

    suspend fun checkAndNotifyContacts(location: Location) {
        try {
            val contacts = getContacts()
            val userId = auth.currentUser?.uid ?: return
            val userName = auth.currentUser?.displayName ?: "Someone"
            
            android.util.Log.d("LocationService", "Checking ${contacts.size} contacts")
            
            for (contact in contacts) {
                val distance = calculateDistance(
                    location.latitude,
                    location.longitude,
                    contact.destinationLat,
                    contact.destinationLng
                )
                
                val eta = calculateETA(distance, location.speed.toDouble())
                
                android.util.Log.d("LocationService", "Distance: ${distance}m, ETA: ${eta}min")
                
                if (eta <= 2 && !contact.notified2Min && distance > 100) {
                    android.util.Log.d("LocationService", "Sending 2-min notification")
                    sendNotificationToContact(
                        contact.email,
                        "$userName is 2 minutes away",
                        "ETA: 2 minutes"
                    )
                    
                    firestore.collection("users")
                        .document(userId)
                        .collection("contacts")
                        .document(contact.email)
                        .update("notified2Min", true)
                        .await()
                }
                
                if (distance <= 100 && !contact.notifiedArrived) {
                    android.util.Log.d("LocationService", "Sending arrival notification")
                    sendNotificationToContact(
                        contact.email,
                        "$userName has arrived",
                        "They are at the destination"
                    )
                    
                    firestore.collection("users")
                        .document(userId)
                        .collection("contacts")
                        .document(contact.email)
                        .update("notifiedArrived", true)
                        .await()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "Error checking contacts", e)
        }
    }

    suspend fun sendDepartureNotification(contactEmail: String, userName: String) {
        try {
            sendNotificationToContact(
                contactEmail,
                "$userName has left",
                "$userName is on the way to you"
            )
            android.util.Log.d("LocationService", "Departure notification sent to $contactEmail")
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "Error sending departure notification", e)
        }
    }
    
    private suspend fun sendNotificationToContact(contactEmail: String, title: String, message: String) {
        try {
            val userSnapshot = firestore.collection("users")
                .whereEqualTo("email", contactEmail)
                .limit(1)
                .get()
                .await()
            
            val targetUserId = userSnapshot.documents.firstOrNull()?.id ?: return
            
            val notificationData = hashMapOf(
                "title" to title,
                "message" to message,
                "type" to "ETA",
                "timestamp" to System.currentTimeMillis()
            )
            
            firestore.collection("users")
                .document(targetUserId)
                .collection("notifications")
                .add(notificationData)
                .await()
            
            NotificationHelper.showLocalNotification(context, title, message)
        } catch (e: Exception) {
            android.util.Log.e("LocationService", "Error sending notification", e)
        }
    }

    suspend fun saveFCMToken(token: String) {
        val userId = auth.currentUser?.uid ?: return
        try {
            firestore.collection("users")
                .document(userId)
                .update("fcmToken", token)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            NotificationHelper.showLocalNotification(
                context,
                "Arrived",
                "You have arrived at your destination"
            )
        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            NotificationHelper.showLocalNotification(
                context,
                "Left",
                "You have left the location"
            )
        }
    }
}
