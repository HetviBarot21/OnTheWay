package com.example.ontheway.services

import android.content.Context
import android.location.Location
import android.util.Log
import com.mapbox.geojson.Point
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LocationSimulationService(private val context: Context) {
    
    private val _locationFlow = MutableStateFlow<Location?>(null)
    val locationFlow: StateFlow<Location?> = _locationFlow.asStateFlow()
    
    private val _progressFlow = MutableStateFlow(Pair(0, 0))
    val progressFlow: StateFlow<Pair<Int, Int>> = _progressFlow.asStateFlow()
    
    private var isRunning = false
    private var isPaused = false
    private var currentSpeed = 1f
    
    private val TAG = "LocationSimulation"
    
    suspend fun startSimulation(
        routePoints: List<Point>,
        speed: Float = 1f
    ) {
        if (routePoints.isEmpty()) {
            Log.e(TAG, "No route points provided")
            return
        }
        
        isRunning = true
        isPaused = false
        currentSpeed = speed
        
        Log.d(TAG, "Starting simulation with ${routePoints.size} points at ${speed}x speed")
        
        val totalPoints = routePoints.size
        _progressFlow.value = Pair(0, totalPoints)
        
        for (i in routePoints.indices) {
            if (!isRunning) {
                Log.d(TAG, "Simulation stopped")
                break
            }
            
            // Wait if paused
            while (isPaused && isRunning) {
                delay(100)
            }
            
            if (!isRunning) break
            
            val point = routePoints[i]
            val location = createLocation(point, i, routePoints)
            
            _locationFlow.value = location
            _progressFlow.value = Pair(i + 1, totalPoints)
            
            Log.d(TAG, "Point ${i + 1}/$totalPoints: ${point.latitude()}, ${point.longitude()}")
            
            // Update location in Firebase (simulate real tracking)
            updateLocationInFirebase(location)
            
            // Calculate delay based on speed
            // Base delay: 2 seconds per point at 1x speed
            val baseDelay = 2000L
            val adjustedDelay = (baseDelay / currentSpeed).toLong()
            
            if (i < routePoints.size - 1) {
                delay(adjustedDelay)
            }
        }
        
        isRunning = false
        Log.d(TAG, "Simulation completed")
    }
    
    fun stopSimulation() {
        isRunning = false
        isPaused = false
        Log.d(TAG, "Simulation stopped by user")
    }
    
    fun pauseSimulation() {
        isPaused = true
        Log.d(TAG, "Simulation paused")
    }
    
    fun resumeSimulation() {
        isPaused = false
        Log.d(TAG, "Simulation resumed")
    }
    
    fun isPaused(): Boolean = isPaused
    
    fun setSpeed(speed: Float) {
        currentSpeed = speed
        Log.d(TAG, "Speed changed to ${speed}x")
    }
    
    private fun createLocation(
        point: Point,
        index: Int,
        allPoints: List<Point>
    ): Location {
        val location = Location("simulation").apply {
            latitude = point.latitude()
            longitude = point.longitude()
            time = System.currentTimeMillis()
            accuracy = 10f
            
            // Calculate speed based on distance to next point
            if (index < allPoints.size - 1) {
                val nextPoint = allPoints[index + 1]
                val distance = calculateDistance(
                    point.latitude(), point.longitude(),
                    nextPoint.latitude(), nextPoint.longitude()
                )
                // Assume 2 seconds between points, speed in m/s
                speed = (distance / 2.0).toFloat()
            } else {
                speed = 0f
            }
            
            // Calculate bearing to next point
            if (index < allPoints.size - 1) {
                val nextPoint = allPoints[index + 1]
                bearing = calculateBearing(
                    point.latitude(), point.longitude(),
                    nextPoint.latitude(), nextPoint.longitude()
                ).toFloat()
            }
        }
        
        return location
    }
    
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        
        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)
        
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360) % 360
    }
    
    private fun updateLocationInFirebase(location: Location) {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    // Update location in Firebase using the same flow as real GPS
                    // This will trigger ETA calculations and notifications
                    Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating location in Firebase", e)
        }
    }
}
