package com.example.ontheway.services

import android.util.Log
import com.example.ontheway.models.Trip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TripService {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Create a new ride share trip
     * @param circleId The circle to share the trip with
     * @param destinationLat Destination latitude
     * @param destinationLng Destination longitude
     * @param destinationName Name of the destination
     * @param sharedWith List of user IDs to share with (empty = entire circle)
     * @return Created Trip object
     */
    suspend fun createTrip(
        circleId: String,
        destinationLat: Double,
        destinationLng: Double,
        destinationName: String,
        sharedWith: List<String> = emptyList()
    ): Trip {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        val userName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Unknown"
        
        // Get user's current location from Firestore
        val locationDoc = firestore.collection("locations")
            .document(userId)
            .collection("updates")
            .document(circleId)
            .get()
            .await()
        
        val currentLat = locationDoc.getDouble("latitude") ?: 0.0
        val currentLng = locationDoc.getDouble("longitude") ?: 0.0
        
        val trip = Trip(
            tripId = UUID.randomUUID().toString(),
            userId = userId,
            userName = userName,
            circleId = circleId,
            startLat = currentLat,
            startLng = currentLng,
            destinationLat = destinationLat,
            destinationLng = destinationLng,
            destinationName = destinationName,
            startTime = System.currentTimeMillis(),
            endTime = null,
            isActive = true,
            sharedWith = sharedWith,
            currentLat = currentLat,
            currentLng = currentLng,
            currentSpeed = 0f,
            lastUpdated = System.currentTimeMillis(),
            notified2Min = false,
            notifiedArrival = false,
            eta = null
        )
        
        Log.d("TripService", "Creating trip: ${trip.tripId} to ${trip.destinationName}")
        
        firestore.collection("trips")
            .document(trip.tripId)
            .set(trip)
            .await()
        
        Log.d("TripService", "Trip created successfully")
        
        return trip
    }

    /**
     * Get all active trips for a specific circle
     * @param circleId The circle ID
     * @return List of active trips
     */
    suspend fun getActiveTrips(circleId: String): List<Trip> {
        return try {
            Log.d("TripService", "Getting active trips for circle: $circleId")
            
            val snapshot = firestore.collection("trips")
                .whereEqualTo("circleId", circleId)
                .whereEqualTo("isActive", true)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val trips = snapshot.documents.mapNotNull { it.toObject(Trip::class.java) }
            
            Log.d("TripService", "Found ${trips.size} active trips")
            
            trips
        } catch (e: Exception) {
            Log.e("TripService", "Error getting active trips", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get all active trips shared with the current user
     * @return List of trips shared with current user
     */
    suspend fun getTripsSharedWithMe(): List<Trip> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        
        return try {
            Log.d("TripService", "Getting trips shared with user: $userId")
            
            // Get trips where user is in sharedWith list OR trips in user's circles
            val circles = CircleService().getUserCircles()
            val circleIds = circles.map { it.circleId }
            
            if (circleIds.isEmpty()) {
                return emptyList()
            }
            
            val allTrips = mutableListOf<Trip>()
            
            // Firestore 'in' query supports max 10 items, so batch them
            circleIds.chunked(10).forEach { batch ->
                val snapshot = firestore.collection("trips")
                    .whereIn("circleId", batch)
                    .whereEqualTo("isActive", true)
                    .get()
                    .await()
                
                val trips = snapshot.documents.mapNotNull { it.toObject(Trip::class.java) }
                    .filter { trip ->
                        // Exclude user's own trips
                        trip.userId != userId &&
                        // Include if sharedWith is empty (shared with entire circle) or user is in sharedWith list
                        (trip.sharedWith.isEmpty() || trip.sharedWith.contains(userId))
                    }
                
                allTrips.addAll(trips)
            }
            
            Log.d("TripService", "Found ${allTrips.size} trips shared with user")
            
            allTrips
        } catch (e: Exception) {
            Log.e("TripService", "Error getting trips shared with user", e)
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Stop an active trip
     * @param tripId The trip ID to stop
     * @return True if successful, false otherwise
     */
    suspend fun stopTrip(tripId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            Log.d("TripService", "Stopping trip: $tripId")
            
            val tripDoc = firestore.collection("trips")
                .document(tripId)
                .get()
                .await()
            
            val trip = tripDoc.toObject(Trip::class.java)
            
            if (trip == null) {
                Log.w("TripService", "Trip not found: $tripId")
                return false
            }
            
            // Verify user owns this trip
            if (trip.userId != userId) {
                Log.w("TripService", "User $userId does not own trip $tripId")
                return false
            }
            
            firestore.collection("trips")
                .document(tripId)
                .update(
                    mapOf(
                        "isActive" to false,
                        "endTime" to System.currentTimeMillis()
                    )
                )
                .await()
            
            Log.d("TripService", "Trip stopped successfully")
            
            true
        } catch (e: Exception) {
            Log.e("TripService", "Error stopping trip", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Mark a trip as completed (automatically called when destination reached)
     * @param tripId The trip ID to complete
     */
    suspend fun completeTrip(tripId: String) {
        try {
            Log.d("TripService", "Completing trip: $tripId")
            
            firestore.collection("trips")
                .document(tripId)
                .update(
                    mapOf(
                        "isActive" to false,
                        "endTime" to System.currentTimeMillis()
                    )
                )
                .await()
            
            Log.d("TripService", "Trip completed successfully")
        } catch (e: Exception) {
            Log.e("TripService", "Error completing trip", e)
            e.printStackTrace()
        }
    }

    /**
     * Update trip location (called by LocationService on each GPS update)
     * @param tripId The trip ID
     * @param latitude Current latitude
     * @param longitude Current longitude
     * @param speed Current speed in m/s
     */
    suspend fun updateTripLocation(
        tripId: String,
        latitude: Double,
        longitude: Double,
        speed: Float
    ) {
        try {
            firestore.collection("trips")
                .document(tripId)
                .update(
                    mapOf(
                        "currentLat" to latitude,
                        "currentLng" to longitude,
                        "currentSpeed" to speed,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            Log.e("TripService", "Error updating trip location", e)
            e.printStackTrace()
        }
    }

    /**
     * Get a specific trip by ID
     * @param tripId The trip ID
     * @return Trip object or null if not found
     */
    suspend fun getTrip(tripId: String): Trip? {
        return try {
            val snapshot = firestore.collection("trips")
                .document(tripId)
                .get()
                .await()
            
            snapshot.toObject(Trip::class.java)
        } catch (e: Exception) {
            Log.e("TripService", "Error getting trip", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * Get user's active trip in a specific circle
     * @param userId The user ID
     * @param circleId The circle ID
     * @return Active trip or null
     */
    suspend fun getUserActiveTrip(userId: String, circleId: String): Trip? {
        return try {
            val snapshot = firestore.collection("trips")
                .whereEqualTo("userId", userId)
                .whereEqualTo("circleId", circleId)
                .whereEqualTo("isActive", true)
                .limit(1)
                .get()
                .await()
            
            snapshot.documents.firstOrNull()?.toObject(Trip::class.java)
        } catch (e: Exception) {
            Log.e("TripService", "Error getting user active trip", e)
            e.printStackTrace()
            null
        }
    }
}
