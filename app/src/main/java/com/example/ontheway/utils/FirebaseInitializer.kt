package com.example.ontheway.utils

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Helper class to verify Firebase setup and initialize database
 */
object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Verify Firebase Authentication is working
     */
    suspend fun verifyAuthentication(): Boolean {
        return try {
            // Check if auth is initialized
            val currentUser = auth.currentUser
            Log.d(TAG, "Auth initialized. Current user: ${currentUser?.email ?: "None"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Auth verification failed", e)
            false
        }
    }

    /**
     * Verify Firestore is accessible
     */
    suspend fun verifyFirestore(): Boolean {
        return try {
            // Try to read from Firestore
            firestore.collection("_test")
                .limit(1)
                .get()
                .await()
            Log.d(TAG, "Firestore is accessible")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Firestore verification failed", e)
            false
        }
    }

    /**
     * Check if user profile exists in Firestore
     */
    suspend fun checkUserProfile(): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: return false
            
            val doc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            
            val exists = doc.exists()
            Log.d(TAG, "User profile exists: $exists")
            exists
        } catch (e: Exception) {
            Log.e(TAG, "User profile check failed", e)
            false
        }
    }

    /**
     * Get database statistics
     */
    suspend fun getDatabaseStats(): DatabaseStats {
        return try {
            val userId = auth.currentUser?.uid
            
            val stats = DatabaseStats()
            
            if (userId != null) {
                // Count user's circles
                val circlesSnapshot = firestore.collection("circles")
                    .whereArrayContains("members", userId)
                    .get()
                    .await()
                stats.circleCount = circlesSnapshot.size()
                
                // Count pending invites
                val invitesSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("invites")
                    .get()
                    .await()
                stats.pendingInvites = invitesSnapshot.size()
                
                // Check if user has location data
                val locationSnapshot = firestore.collection("locations")
                    .document(userId)
                    .collection("updates")
                    .limit(1)
                    .get()
                    .await()
                stats.hasLocationData = locationSnapshot.size() > 0
            }
            
            Log.d(TAG, "Database stats: $stats")
            stats
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get database stats", e)
            DatabaseStats()
        }
    }

    /**
     * Verify all Firebase services are working
     */
    suspend fun verifyAllServices(): ServiceStatus {
        val status = ServiceStatus()
        
        status.authEnabled = verifyAuthentication()
        status.firestoreEnabled = verifyFirestore()
        
        if (auth.currentUser != null) {
            status.userProfileExists = checkUserProfile()
        }
        
        Log.d(TAG, "Service status: $status")
        return status
    }

    /**
     * Create test data for development (only use in test mode!)
     */
    suspend fun createTestData(): Boolean {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            // This is just for testing - don't use in production
            Log.d(TAG, "Test data creation would go here")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create test data", e)
            false
        }
    }
}

data class DatabaseStats(
    var circleCount: Int = 0,
    var pendingInvites: Int = 0,
    var hasLocationData: Boolean = false
) {
    override fun toString(): String {
        return "Circles: $circleCount, Invites: $pendingInvites, Location: $hasLocationData"
    }
}

data class ServiceStatus(
    var authEnabled: Boolean = false,
    var firestoreEnabled: Boolean = false,
    var userProfileExists: Boolean = false
) {
    val allServicesWorking: Boolean
        get() = authEnabled && firestoreEnabled
    
    override fun toString(): String {
        return "Auth: $authEnabled, Firestore: $firestoreEnabled, Profile: $userProfileExists"
    }
}
