package com.example.ontheway.services

import android.util.Log
import com.example.ontheway.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.UUID

class CircleService {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Hash phone number for privacy
    fun hashPhoneNumber(phoneNumber: String): String {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
        val bytes = MessageDigest.getInstance("SHA-256").digest(cleanPhone.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Create a new circle
    suspend fun createCircle(name: String): Circle {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        val inviteCode = generateInviteCode()
        
        val circle = Circle(
            circleId = UUID.randomUUID().toString(),
            name = name,
            createdBy = userId,
            createdAt = System.currentTimeMillis(),
            inviteCode = inviteCode,
            members = listOf(userId)
        )
        
        firestore.collection("circles")
            .document(circle.circleId)
            .set(circle)
            .await()
        
        // Add user to circle members
        addUserToCircle(circle.circleId, userId)
        
        return circle
    }

    // Get all circles for current user
    suspend fun getUserCircles(): List<Circle> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        
        return try {
            val snapshot = firestore.collection("circles")
                .whereArrayContains("members", userId)
                .get()
                .await()
            
            snapshot.documents.mapNotNull { it.toObject(Circle::class.java) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Get circle members with their locations
    suspend fun getCircleMembers(circleId: String): List<CircleMember> {
        return try {
            Log.d("CircleService", "Getting members for circle: $circleId")
            
            val circle = firestore.collection("circles")
                .document(circleId)
                .get()
                .await()
                .toObject(Circle::class.java) ?: run {
                    Log.w("CircleService", "Circle not found: $circleId")
                    return emptyList()
                }
            
            Log.d("CircleService", "Circle has ${circle.members.size} member IDs: ${circle.members}")
            
            val members = mutableListOf<CircleMember>()
            
            for (memberId in circle.members) {
                Log.d("CircleService", "Fetching user data for: $memberId")
                
                // Get user info
                val userDoc = firestore.collection("users")
                    .document(memberId)
                    .get()
                    .await()
                
                val user = userDoc.toObject(User::class.java)
                
                if (user != null) {
                    Log.d("CircleService", "Found user: ${user.name} (${user.email})")
                } else {
                    Log.w("CircleService", "User document not found for: $memberId")
                }
                
                // Get latest location (skip if fails)
                val location = try {
                    val locationDoc = firestore.collection("locations")
                        .document(memberId)
                        .collection("updates")
                        .whereEqualTo("circleId", circleId)
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .limit(1)
                        .get()
                        .await()
                    
                    locationDoc.documents.firstOrNull()?.toObject(LocationUpdate::class.java)
                } catch (e: Exception) {
                    Log.w("CircleService", "Could not fetch location for $memberId: ${e.message}")
                    null
                }
                
                if (user != null) {
                    members.add(
                        CircleMember(
                            userId = memberId,
                            name = user.name,
                            email = user.email,
                            phoneNumber = user.phoneNumber,
                            latitude = location?.latitude ?: 0.0,
                            longitude = location?.longitude ?: 0.0,
                            lastUpdated = location?.timestamp ?: 0L,
                            isActive = (System.currentTimeMillis() - (location?.timestamp ?: 0L)) < 300000, // 5 minutes
                            batteryLevel = location?.batteryLevel ?: 0,
                            isCharging = location?.isCharging ?: false
                        )
                    )
                }
            }
            
            Log.d("CircleService", "Returning ${members.size} members")
            members
        } catch (e: Exception) {
            Log.e("CircleService", "Error getting circle members", e)
            e.printStackTrace()
            emptyList()
        }
    }

    // Join circle with invite code
    suspend fun joinCircleWithCode(inviteCode: String): Circle? {
        val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
        
        return try {
            Log.d("CircleService", "Searching for circle with code: $inviteCode")
            Log.d("CircleService", "Current user: $userId")
            
            val snapshot = firestore.collection("circles")
                .whereEqualTo("inviteCode", inviteCode)
                .limit(1)
                .get()
                .await()
            
            Log.d("CircleService", "Query returned ${snapshot.documents.size} documents")
            
            val circle = snapshot.documents.firstOrNull()?.toObject(Circle::class.java)
            
            if (circle != null) {
                Log.d("CircleService", "Found circle: ${circle.name} (ID: ${circle.circleId})")
                Log.d("CircleService", "Circle has ${circle.members.size} members")
                
                if (!circle.members.contains(userId)) {
                    Log.d("CircleService", "Adding user $userId to circle")
                    // Add user to circle
                    val updatedMembers = circle.members + userId
                    firestore.collection("circles")
                        .document(circle.circleId)
                        .update("members", updatedMembers)
                        .await()
                    
                    addUserToCircle(circle.circleId, userId)
                    
                    Log.d("CircleService", "Successfully added user to circle")
                    circle.copy(members = updatedMembers)
                } else {
                    Log.d("CircleService", "User already in circle")
                    circle
                }
            } else {
                Log.w("CircleService", "No circle found with invite code: $inviteCode")
                null
            }
        } catch (e: Exception) {
            Log.e("CircleService", "Error joining circle with code $inviteCode", e)
            e.printStackTrace()
            null
        }
    }

    // Find users by phone numbers (hashed)
    suspend fun findUsersByPhoneNumbers(phoneNumbers: List<String>): List<User> {
        val hashedNumbers = phoneNumbers.map { hashPhoneNumber(it) }
        
        return try {
            val users = mutableListOf<User>()
            
            // Firestore 'in' query supports max 10 items, so batch them
            hashedNumbers.chunked(10).forEach { batch ->
                val snapshot = firestore.collection("users")
                    .whereIn("phoneHash", batch)
                    .get()
                    .await()
                
                users.addAll(snapshot.documents.mapNotNull { it.toObject(User::class.java) })
            }
            
            users
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // Invite user to circle
    suspend fun inviteUserToCircle(circleId: String, phoneNumber: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        val userName = auth.currentUser?.displayName ?: "Someone"
        
        return try {
            val circle = firestore.collection("circles")
                .document(circleId)
                .get()
                .await()
                .toObject(Circle::class.java) ?: return false
            
            // Check if user exists
            val phoneHash = hashPhoneNumber(phoneNumber)
            val userSnapshot = firestore.collection("users")
                .whereEqualTo("phoneHash", phoneHash)
                .limit(1)
                .get()
                .await()
            
            val invite = CircleInvite(
                inviteId = UUID.randomUUID().toString(),
                circleId = circleId,
                circleName = circle.name,
                invitedBy = userId,
                invitedByName = userName,
                inviteCode = circle.inviteCode,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000) // 7 days
            )
            
            if (userSnapshot.documents.isNotEmpty()) {
                // User exists, send in-app invite
                val targetUserId = userSnapshot.documents.first().id
                firestore.collection("users")
                    .document(targetUserId)
                    .collection("invites")
                    .document(invite.inviteId)
                    .set(invite)
                    .await()
            } else {
                // User doesn't exist, store invite for when they sign up
                firestore.collection("pending_invites")
                    .document(phoneHash)
                    .collection("invites")
                    .document(invite.inviteId)
                    .set(invite)
                    .await()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Leave circle
    suspend fun leaveCircle(circleId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            val circle = firestore.collection("circles")
                .document(circleId)
                .get()
                .await()
                .toObject(Circle::class.java) ?: return false
            
            val updatedMembers = circle.members.filter { it != userId }
            
            if (updatedMembers.isEmpty()) {
                // Delete circle if no members left
                firestore.collection("circles")
                    .document(circleId)
                    .delete()
                    .await()
            } else {
                firestore.collection("circles")
                    .document(circleId)
                    .update("members", updatedMembers)
                    .await()
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Delete circle (only creator can delete)
    suspend fun deleteCircle(circleId: String): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        
        return try {
            val circle = firestore.collection("circles")
                .document(circleId)
                .get()
                .await()
                .toObject(Circle::class.java) ?: return false
            
            // Check if user is the creator
            if (circle.createdBy != userId) {
                return false
            }
            
            // Delete the circle
            firestore.collection("circles")
                .document(circleId)
                .delete()
                .await()
            
            // Clean up location updates for this circle
            for (memberId in circle.members) {
                try {
                    firestore.collection("locations")
                        .document(memberId)
                        .collection("updates")
                        .document(circleId)
                        .delete()
                        .await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Update location for all circles
    suspend fun updateLocationForCircles(latitude: Double, longitude: Double, speed: Float, accuracy: Float) {
        val userId = auth.currentUser?.uid ?: return
        val circles = getUserCircles()
        
        // Also update online status when updating location
        try {
            val onlineStatus = mapOf(
                "userId" to userId,
                "isOnline" to true,
                "lastSeen" to System.currentTimeMillis(),
                "connectionType" to "active"
            )
            
            firestore.collection("users")
                .document(userId)
                .collection("status")
                .document("online")
                .set(onlineStatus)
                .await()
                
            Log.d("CircleService", "Updated online status with location")
        } catch (e: Exception) {
            Log.e("CircleService", "Error updating online status", e)
        }
        
        for (circle in circles) {
            val locationUpdate = LocationUpdate(
                userId = userId,
                circleId = circle.circleId,
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis(),
                speed = speed,
                accuracy = accuracy
            )
            
            try {
                firestore.collection("locations")
                    .document(userId)
                    .collection("updates")
                    .document(circle.circleId)
                    .set(locationUpdate)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun addUserToCircle(circleId: String, userId: String) {
        try {
            firestore.collection("circle_members")
                .document("${circleId}_$userId")
                .set(mapOf(
                    "circleId" to circleId,
                    "userId" to userId,
                    "joinedAt" to System.currentTimeMillis()
                ))
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }

    // Update location for all circles
    suspend fun updateLocationForCircles(
        latitude: Double, 
        longitude: Double, 
        speed: Float, 
        accuracy: Float,
        batteryLevel: Int = 0,
        isCharging: Boolean = false
    ) {
        val userId = auth.currentUser?.uid ?: return
        val circles = getUserCircles()
        
        Log.d("CircleService", "Updating location for ${circles.size} circles")
        
        for (circle in circles) {
            val locationUpdate = LocationUpdate(
                userId = userId,
                circleId = circle.circleId,
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis(),
                speed = speed,
                accuracy = accuracy,
                batteryLevel = batteryLevel,
                isCharging = isCharging
            )
            
            try {
                firestore.collection("locations")
                    .document(userId)
                    .collection("updates")
                    .document(circle.circleId)
                    .set(locationUpdate)
                    .await()
                Log.d("CircleService", "Location updated for circle: ${circle.circleId}")
            } catch (e: Exception) {
                Log.e("CircleService", "Failed to update location for circle ${circle.circleId}", e)
                e.printStackTrace()
            }
        }
    }

}
