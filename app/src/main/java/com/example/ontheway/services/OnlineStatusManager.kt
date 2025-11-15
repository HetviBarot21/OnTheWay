package com.example.ontheway.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.ontheway.models.OnlineStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class OnlineStatusManager(private val context: Context) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private var heartbeatJob: Job? = null
    private val statusListeners = mutableMapOf<String, ListenerRegistration>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    companion object {
        private const val HEARTBEAT_INTERVAL = 30000L // 30 seconds
        private const val ONLINE_THRESHOLD = 60000L // 60 seconds - consider offline if no update
        private const val TAG = "OnlineStatusManager"
    }

    /**
     * Update current user's online status
     * @param isOnline Whether user is online
     */
    suspend fun setOnlineStatus(isOnline: Boolean) {
        val userId = auth.currentUser?.uid
        
        if (userId == null) {
            Log.e(TAG, "Cannot set online status - user not authenticated")
            return
        }
        
        try {
            val connectionType = if (isOnline) getConnectionType() else "offline"
            
            val status = OnlineStatus(
                userId = userId,
                isOnline = isOnline,
                lastSeen = System.currentTimeMillis(),
                connectionType = connectionType
            )
            
            Log.d(TAG, "Setting online status for user $userId: $isOnline, connection: $connectionType")
            Log.d(TAG, "Writing to path: users/$userId/status/online")
            
            firestore.collection("users")
                .document(userId)
                .collection("status")
                .document("online")
                .set(status)
                .await()
            
            Log.d(TAG, "✅ Online status updated successfully!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error setting online status: ${e.message}", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            e.printStackTrace()
        }
    }

    /**
     * Get connection type (wifi, cellular, or offline)
     */
    private fun getConnectionType(): String {
        val network = connectivityManager.activeNetwork ?: return "offline"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "offline"
        
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "wifi"
            else -> "offline"
        }
    }

    /**
     * Check if device has internet connection
     */
    private fun hasInternetConnection(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Start heartbeat to maintain online status
     * Updates lastSeen every 30 seconds while app is active
     */
    fun startHeartbeat() {
        stopHeartbeat() // Stop any existing heartbeat
        
        Log.d(TAG, "Starting heartbeat")
        
        heartbeatJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val isOnline = hasInternetConnection()
                    setOnlineStatus(isOnline)
                    delay(HEARTBEAT_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in heartbeat", e)
                    delay(HEARTBEAT_INTERVAL)
                }
            }
        }
        
        // Also listen for network changes
        setupNetworkCallback()
    }

    /**
     * Stop heartbeat
     */
    fun stopHeartbeat() {
        Log.d(TAG, "Stopping heartbeat")
        
        heartbeatJob?.cancel()
        heartbeatJob = null
        
        // Set status to offline when stopping
        CoroutineScope(Dispatchers.IO).launch {
            setOnlineStatus(false)
        }
        
        // Remove network callback
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering network callback", e)
            }
        }
        networkCallback = null
    }

    /**
     * Setup network callback to detect connection changes
     */
    private fun setupNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                CoroutineScope(Dispatchers.IO).launch {
                    setOnlineStatus(true)
                }
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                CoroutineScope(Dispatchers.IO).launch {
                    setOnlineStatus(false)
                }
            }
        }
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering network callback", e)
        }
    }

    /**
     * Listen to online status changes for circle members
     * @param circleId The circle ID
     * @param onStatusChange Callback when status changes (userId, isOnline)
     */
    fun observeOnlineStatus(
        circleId: String,
        onStatusChange: (userId: String, isOnline: Boolean) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get circle members
                val circleDoc = firestore.collection("circles")
                    .document(circleId)
                    .get()
                    .await()
                
                val members = circleDoc.get("members") as? List<*> ?: emptyList<String>()
                
                Log.d(TAG, "Observing online status for ${members.size} members")
                
                // Listen to each member's status
                members.forEach { memberId ->
                    if (memberId is String) {
                        val listener = firestore.collection("users")
                            .document(memberId)
                            .collection("status")
                            .document("online")
                            .addSnapshotListener { snapshot, error ->
                                if (error != null) {
                                    Log.e(TAG, "Error listening to status for $memberId", error)
                                    return@addSnapshotListener
                                }
                                
                                if (snapshot != null && snapshot.exists()) {
                                    val status = snapshot.toObject(OnlineStatus::class.java)
                                    if (status != null) {
                                        val isOnline = isUserOnline(status)
                                        Log.d(TAG, "Status update for $memberId: $isOnline")
                                        onStatusChange(memberId, isOnline)
                                    }
                                } else {
                                    // No status document = offline
                                    onStatusChange(memberId, false)
                                }
                            }
                        
                        statusListeners[memberId] = listener
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error observing online status", e)
                e.printStackTrace()
            }
        }
    }

    /**
     * Stop observing online status
     */
    fun stopObserving() {
        Log.d(TAG, "Stopping all status listeners")
        statusListeners.values.forEach { it.remove() }
        statusListeners.clear()
    }

    /**
     * Get online status for a specific user
     * @param userId The user ID
     * @return True if online, false otherwise
     */
    suspend fun getUserOnlineStatus(userId: String): Boolean {
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("status")
                .document("online")
                .get()
                .await()
            
            if (snapshot.exists()) {
                val status = snapshot.toObject(OnlineStatus::class.java)
                if (status != null) {
                    isUserOnline(status)
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user online status", e)
            false
        }
    }

    /**
     * Determine if user is online based on status
     * User is online if lastSeen is within the last 60 seconds
     */
    private fun isUserOnline(status: OnlineStatus): Boolean {
        val timeSinceLastSeen = System.currentTimeMillis() - status.lastSeen
        return status.isOnline && timeSinceLastSeen < ONLINE_THRESHOLD
    }

    /**
     * Get all online users in a circle
     * @param circleId The circle ID
     * @return List of online user IDs
     */
    suspend fun getOnlineUsersInCircle(circleId: String): List<String> {
        return try {
            val circleDoc = firestore.collection("circles")
                .document(circleId)
                .get()
                .await()
            
            val members = circleDoc.get("members") as? List<*> ?: emptyList<String>()
            
            val onlineUsers = mutableListOf<String>()
            
            members.forEach { memberId ->
                if (memberId is String) {
                    if (getUserOnlineStatus(memberId)) {
                        onlineUsers.add(memberId)
                    }
                }
            }
            
            Log.d(TAG, "Found ${onlineUsers.size} online users in circle")
            
            onlineUsers
        } catch (e: Exception) {
            Log.e(TAG, "Error getting online users", e)
            emptyList()
        }
    }
}
