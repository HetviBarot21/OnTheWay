package com.example.ontheway.models

data class User(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val phoneHash: String = "",
    val fcmToken: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val profileImageUrl: String = ""
)

data class Circle(
    val circleId: String = "",
    val name: String = "",
    val createdBy: String = "",
    val createdAt: Long = 0L,
    val inviteCode: String = "",
    val members: List<String> = emptyList()
)

data class CircleMember(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastUpdated: Long = 0L,
    val isActive: Boolean = false,
    val isSharingTrip: Boolean = false,
    val tripDestinationLat: Double? = null,
    val tripDestinationLng: Double? = null,
    val eta: Int? = null
)

data class LocationUpdate(
    val userId: String = "",
    val circleId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val speed: Float = 0f,
    val accuracy: Float = 0f
)

data class CircleInvite(
    val inviteId: String = "",
    val circleId: String = "",
    val circleName: String = "",
    val invitedBy: String = "",
    val invitedByName: String = "",
    val inviteCode: String = "",
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val status: String = "pending" // pending, accepted, declined
)

data class Trip(
    val tripId: String = "",
    val userId: String = "",
    val circleId: String = "",
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val destinationName: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val isActive: Boolean = true,
    val sharedWith: List<String> = emptyList() // User IDs
)

data class Geofence(
    val geofenceId: String = "",
    val userId: String = "",
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radius: Float = 100f, // meters
    val type: String = "custom", // home, work, school, custom
    val createdAt: Long = 0L
)
