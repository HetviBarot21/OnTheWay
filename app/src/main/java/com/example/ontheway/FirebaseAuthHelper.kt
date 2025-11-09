package com.example.ontheway

import com.example.ontheway.models.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

object FirebaseAuthHelper {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Register a new user with email and password
     * Automatically creates user profile in Firestore
     */
    suspend fun registerUser(
        email: String, 
        password: String, 
        name: String = "",
        phoneNumber: String = ""
    ): Result<FirebaseUser?> {
        return try {
            // Create Firebase Auth user
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            
            if (user != null) {
                // Update display name if provided
                if (name.isNotEmpty()) {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build()
                    user.updateProfile(profileUpdates).await()
                }
                
                // Create user profile in Firestore
                val userProfile = User(
                    userId = user.uid,
                    name = name.ifEmpty { email.substringBefore("@") },
                    email = email,
                    phoneNumber = phoneNumber,
                    phoneHash = if (phoneNumber.isNotEmpty()) hashPhoneNumber(phoneNumber) else "",
                    fcmToken = "",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    profileImageUrl = ""
                )
                
                firestore.collection("users")
                    .document(user.uid)
                    .set(userProfile)
                    .await()
                
                // Check for pending invites
                if (phoneNumber.isNotEmpty()) {
                    checkPendingInvites(user.uid, phoneNumber)
                }
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Login existing user with email and password
     */
    suspend fun loginUser(email: String, password: String): Result<FirebaseUser?> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            
            // Update last login time
            result.user?.let { user ->
                firestore.collection("users")
                    .document(user.uid)
                    .update("updatedAt", System.currentTimeMillis())
                    .await()
            }
            
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logout current user
     */
    fun logoutUser() {
        auth.signOut()
    }

    /**
     * Update user profile in Firestore
     */
    suspend fun updateUserProfile(
        name: String? = null,
        phoneNumber: String? = null,
        profileImageUrl: String? = null
    ): Result<Boolean> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not authenticated")
            
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to System.currentTimeMillis()
            )
            
            name?.let { updates["name"] = it }
            phoneNumber?.let { 
                updates["phoneNumber"] = it
                updates["phoneHash"] = hashPhoneNumber(it)
            }
            profileImageUrl?.let { updates["profileImageUrl"] = it }
            
            firestore.collection("users")
                .document(userId)
                .update(updates)
                .await()
            
            // Update Firebase Auth display name if provided
            if (name != null) {
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(name)
                    .build()
                auth.currentUser?.updateProfile(profileUpdates)?.await()
            }
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user profile from Firestore
     */
    suspend fun getUserProfile(userId: String? = null): User? {
        return try {
            val uid = userId ?: auth.currentUser?.uid ?: return null
            
            val doc = firestore.collection("users")
                .document(uid)
                .get()
                .await()
            
            doc.toObject(User::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Reset password via email
     */
    suspend fun resetPassword(email: String): Result<Boolean> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check for pending invites when user signs up
     */
    private suspend fun checkPendingInvites(userId: String, phoneNumber: String) {
        try {
            val phoneHash = hashPhoneNumber(phoneNumber)
            
            val invitesSnapshot = firestore.collection("pending_invites")
                .document(phoneHash)
                .collection("invites")
                .get()
                .await()
            
            // Move pending invites to user's invites
            for (doc in invitesSnapshot.documents) {
                firestore.collection("users")
                    .document(userId)
                    .collection("invites")
                    .document(doc.id)
                    .set(doc.data ?: emptyMap<String, Any>())
                    .await()
                
                // Delete from pending
                doc.reference.delete().await()
            }
            
            // Delete pending invites document if empty
            if (invitesSnapshot.documents.isNotEmpty()) {
                firestore.collection("pending_invites")
                    .document(phoneHash)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Hash phone number for privacy
     */
    private fun hashPhoneNumber(phoneNumber: String): String {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
        val bytes = MessageDigest.getInstance("SHA-256").digest(cleanPhone.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
