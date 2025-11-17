package com.example.ontheway

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ontheway.models.CircleMember
import com.example.ontheway.services.CircleService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.gestures.gestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userEmail: String,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCircles: () -> Unit = {},
    onNavigateToSOS: () -> Unit = {},
    onNavigateToSimulation: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val circleService = remember { CircleService() }
    val locationService = remember { LocationService(context) }
    
    var hasLocationPermission by remember { mutableStateOf(false) }
    var permissionChecked by remember { mutableStateOf(false) }
    var allMembers by remember { mutableStateOf(listOf<CircleMember>()) }
    var isLoadingMembers by remember { mutableStateOf(true) }
    var currentLocationName by remember { mutableStateOf("Loading location...") }
    var batteryPercentage by remember { mutableStateOf(0) }
    var lastNotificationCheck by remember { mutableStateOf(0L) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            try {
                hasLocationPermission = granted
                permissionChecked = true
                if (granted) {
                    locationService.startLocationUpdates { location ->
                        scope.launch {
                            try {
                                locationService.checkAndNotifyContacts(location)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    )

    // Check permission once on startup
    LaunchedEffect(Unit) {
        if (!permissionChecked) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            hasLocationPermission = granted
            permissionChecked = true
            
            // Get FCM token
            try {
                val fcmToken = NotificationHelper.getFCMToken()
                if (fcmToken != null) {
                    locationService.saveFCMToken(fcmToken)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Load all members from all circles and refresh every 5 seconds
    LaunchedEffect(Unit) {
        try {
            // Refresh members from all circles periodically
            while (true) {
                val circles = circleService.getUserCircles()
                val members = mutableListOf<CircleMember>()
                
                for (circle in circles) {
                    val circleMembers = circleService.getCircleMembers(circle.circleId)
                    members.addAll(circleMembers)
                }
                
                // Filter out current user and get distinct members
                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val updatedMembers = members
                    .distinctBy { it.userId }
                    .filter { it.userId != currentUserId }
                
                // Log position changes
                updatedMembers.forEach { newMember ->
                    val oldMember = allMembers.find { it.userId == newMember.userId }
                    if (oldMember != null && 
                        (oldMember.latitude != newMember.latitude || oldMember.longitude != newMember.longitude)) {
                        android.util.Log.d("LocationUpdate", 
                            "${newMember.name} moved from (${oldMember.latitude}, ${oldMember.longitude}) to (${newMember.latitude}, ${newMember.longitude})")
                    }
                }
                
                allMembers = updatedMembers
                delay(5000) // 5 seconds for more frequent updates
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Get battery percentage
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val batteryStatus: Intent? = context.registerReceiver(
                    null,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                batteryPercentage = if (level >= 0 && scale > 0) {
                    (level * 100 / scale)
                } else {
                    0
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            delay(30000) // Update every 30 seconds
        }
    }
    
    // Poll for new notifications every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    
                    // Get notifications newer than last check
                    val notifications = firestore.collection("users")
                        .document(userId)
                        .collection("notifications")
                        .whereGreaterThan("timestamp", lastNotificationCheck)
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get()
                        .await()
                    
                    // Show each new notification
                    for (doc in notifications.documents) {
                        val title = doc.getString("title") ?: "OnTheWay"
                        val message = doc.getString("message") ?: ""
                        val type = doc.getString("type") ?: ""
                        val timestamp = doc.getLong("timestamp") ?: 0L
                        
                        // Show local notification
                        NotificationHelper.showLocalNotification(context, title, message)
                        
                        android.util.Log.d("NotificationPoll", "New notification: $title - $message")
                        
                        // Update last check time
                        if (timestamp > lastNotificationCheck) {
                            lastNotificationCheck = timestamp
                        }
                        
                        // Mark as read
                        doc.reference.update("read", true)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationPoll", "Error polling notifications", e)
            }
            
            delay(30000) // Check every 30 seconds
        }
    }
    
    // Start location updates when permission is granted
    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                locationService.startLocationUpdates { location ->
                    scope.launch {
                        try {
                            locationService.checkAndNotifyContacts(location)
                            
                            // Get location name using Geocoder
                            try {
                                val geocoder = Geocoder(context)
                                val addresses: List<Address>? = geocoder.getFromLocation(
                                    location.latitude,
                                    location.longitude,
                                    1
                                )
                                if (!addresses.isNullOrEmpty()) {
                                    val address = addresses[0]
                                    
                                    // Try to get the most detailed address line first
                                    val addressLine = address.getAddressLine(0)
                                    
                                    if (addressLine != null && addressLine.isNotEmpty()) {
                                        // Parse the full address line to get street-level detail
                                        val addressParts = addressLine.split(",").map { it.trim() }
                                        currentLocationName = when {
                                            addressParts.size >= 2 -> "${addressParts[0]}, ${addressParts[1]}"
                                            addressParts.size == 1 -> addressParts[0]
                                            else -> addressLine
                                        }
                                    } else {
                                        // Fallback to building from components
                                        val parts = mutableListOf<String>()
                                        
                                        // Add premises or feature name
                                        if (address.premises != null) {
                                            parts.add(address.premises)
                                        } else if (address.featureName != null && address.featureName != address.locality) {
                                            parts.add(address.featureName)
                                        }
                                        
                                        // Add street number and name
                                        if (address.subThoroughfare != null && address.thoroughfare != null) {
                                            parts.add("${address.subThoroughfare} ${address.thoroughfare}")
                                        } else if (address.thoroughfare != null) {
                                            parts.add(address.thoroughfare)
                                        }
                                        
                                        // Add subLocality or locality
                                        if (address.subLocality != null) {
                                            parts.add(address.subLocality)
                                        } else if (address.locality != null) {
                                            parts.add(address.locality)
                                        }
                                        
                                        currentLocationName = if (parts.isNotEmpty()) {
                                            parts.take(2).joinToString(", ")
                                        } else {
                                            "${location.latitude}, ${location.longitude}"
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                currentLocationName = "${location.latitude}, ${location.longitude}"
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationService.stopLocationUpdates()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OnTheWay") },
                actions = {
                    IconButton(
                        onClick = onNavigateToSOS,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Emergency SOS")
                    }

                    IconButton(onClick = onNavigateToCircles) {
                        Icon(Icons.Default.Add, contentDescription = "Manage Circles")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (hasLocationPermission) {
            var mapView by remember { mutableStateOf<MapView?>(null) }
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Map takes 50% of screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f)
                ) {
                    AllMembersMap(
                        members = allMembers,
                        onMapReady = { mv -> mapView = mv }
                    )
                }
                
                // Members list takes remaining 50%
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    MembersList(
                        members = allMembers,
                        mapView = mapView,
                        onShareRide = { member ->
                            scope.launch {
                                try {
                                    android.util.Log.d("ShareRide", "Starting ride share with ${member.email}")
                                    
                                    // Share ride with this member - use their location as destination
                                    locationService.addContact(
                                        member.email,
                                        member.userId,
                                        member.latitude,
                                        member.longitude
                                    )
                                    
                                    android.util.Log.d("ShareRide", "Contact added successfully")
                                    
                                    // Show confirmation
                                    android.widget.Toast.makeText(
                                        context,
                                        "Sharing ride with ${member.name}. They've been notified you're on the way.",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } catch (e: Exception) {
                                    android.util.Log.e("ShareRide", "Error sharing ride", e)
                                    
                                    // Show more specific error message
                                    val errorMsg = when {
                                        e.message?.contains("permission", ignoreCase = true) == true -> 
                                            "Ride sharing started (some features may be limited)"
                                        else -> 
                                            "Error: ${e.message}"
                                    }
                                    
                                    android.widget.Toast.makeText(
                                        context,
                                        errorMsg,
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    )
                }
            }
        } else {
            // Permission request UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Location Permission Required",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We need your location to share with your circles",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        try {
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

// Helper function to create circular marker with initial
fun createCircularMarker(initial: String, color: Int = android.graphics.Color.parseColor("#6200EE")): Bitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Draw circle background
    val circlePaint = Paint().apply {
        this.color = color
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)
    
    // Draw white border
    val borderPaint = Paint().apply {
        this.color = android.graphics.Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, borderPaint)
    
    // Draw initial text
    val textPaint = Paint().apply {
        this.color = android.graphics.Color.WHITE
        isAntiAlias = true
        textSize = 60f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    
    val textY = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(initial, size / 2f, textY, textPaint)
    
    return bitmap
}

@Composable
fun AllMembersMap(
    members: List<CircleMember>,
    onMapReady: (MapView) -> Unit = {}
) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasSetInitialCamera by remember { mutableStateOf(false) }
    var pointAnnotationManager by remember { mutableStateOf<com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager?>(null) }
    
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDestroy()
        }
    }
    
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mapView = mv
                onMapReady(mv)
                try {
                    mv.getMapboxMap().loadStyle(Style.MAPBOX_STREETS) { style ->
                        // Enable all zoom and pan gestures after style loads
                        mv.gestures.updateSettings {
                            pinchToZoomEnabled = true
                            doubleTapToZoomInEnabled = true
                            doubleTouchToZoomOutEnabled = true
                            quickZoomEnabled = true
                            pitchEnabled = true
                            rotateEnabled = true
                            scrollEnabled = true
                            simultaneousRotateAndPinchToZoomEnabled = true
                            pinchToZoomDecelerationEnabled = true
                            rotateDecelerationEnabled = true
                            scrollDecelerationEnabled = true
                        }
                        try {
                            // Enable user location
                            if (ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val locationPlugin = mv.location
                                locationPlugin.updateSettings {
                                    this.enabled = true
                                    this.pulsingEnabled = true
                                }
                                
                                // Get current location and center map
                                val fusedLocationClient: FusedLocationProviderClient = 
                                    LocationServices.getFusedLocationProviderClient(ctx)
                                
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                    location?.let {
                                        if (!hasSetInitialCamera) {
                                            val cameraOptions = CameraOptions.Builder()
                                                .center(Point.fromLngLat(it.longitude, it.latitude))
                                                .zoom(13.0)
                                                .build()
                                            mv.getMapboxMap().setCamera(cameraOptions)
                                            hasSetInitialCamera = true
                                        }
                                    }
                                }
                            }
                            
                            // Add markers for all members with valid locations
                            val annotationApi = mv.annotations
                            pointAnnotationManager = annotationApi.createPointAnnotationManager()
                            
                            members.filter { it.latitude != 0.0 && it.longitude != 0.0 }.forEach { member ->
                                // Get first initial of first name
                                val initial = member.name.firstOrNull()?.uppercase() ?: "?"
                                
                                // Create circular marker bitmap (dimmed if not active)
                                val color = if (member.isActive) {
                                    android.graphics.Color.parseColor("#6200EE")
                                } else {
                                    android.graphics.Color.parseColor("#9E9E9E")
                                }
                                val markerBitmap = createCircularMarker(initial, color)
                                
                                val pointAnnotationOptions = PointAnnotationOptions()
                                    .withPoint(Point.fromLngLat(member.longitude, member.latitude))
                                    .withIconImage(markerBitmap)
                                
                                pointAnnotationManager?.create(pointAnnotationOptions)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        },
        update = { mv ->
            // Update markers when members change
            try {
                pointAnnotationManager?.let { manager ->
                    val validMembers = members.filter { it.latitude != 0.0 && it.longitude != 0.0 }
                    
                    // Clear all existing markers
                    manager.deleteAll()
                    
                    // Add updated markers for all members with valid locations
                    validMembers.forEach { member ->
                        val initial = member.name.firstOrNull()?.uppercase() ?: "?"
                        
                        // Use different color for active vs inactive
                        val color = if (member.isActive) {
                            android.graphics.Color.parseColor("#6200EE")
                        } else {
                            android.graphics.Color.parseColor("#9E9E9E")
                        }
                        val markerBitmap = createCircularMarker(initial, color)
                        
                        val pointAnnotationOptions = PointAnnotationOptions()
                            .withPoint(Point.fromLngLat(member.longitude, member.latitude))
                            .withIconImage(markerBitmap)
                        
                        manager.create(pointAnnotationOptions)
                        
                        android.util.Log.d("MapUpdate", "Updated marker for ${member.name} at ${member.latitude}, ${member.longitude}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MapUpdate", "Error updating markers", e)
                e.printStackTrace()
            }
        }
    )
}

@Composable
fun MembersList(
    members: List<CircleMember>,
    mapView: MapView?,
    onShareRide: (CircleMember) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Circle Members",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        if (members.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No members yet.\nJoin or create a circle to get started.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(members.size) { index ->
                    MemberCard(
                        member = members[index],
                        onShareRide = { onShareRide(members[index]) },
                        onMemberClick = { member ->
                            // Center map on this member's location
                            mapView?.let { mv ->
                                if (member.latitude != 0.0 && member.longitude != 0.0) {
                                    val cameraOptions = CameraOptions.Builder()
                                        .center(Point.fromLngLat(member.longitude, member.latitude))
                                        .zoom(15.0)
                                        .build()
                                    mv.getMapboxMap().setCamera(cameraOptions)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MemberCard(
    member: CircleMember,
    onShareRide: () -> Unit,
    onMemberClick: (CircleMember) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationService = remember { LocationService(context) }
    var isSharing by remember { mutableStateOf(false) }
    var someoneComingToMe by remember { mutableStateOf(false) }
    var currentETA by remember { mutableStateOf<Int?>(null) }
    var currentDistance by remember { mutableStateOf<Double?>(null) }
    var locationName by remember { mutableStateOf<String?>(null) }
    
    // Check if this is the current user
    val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    val isCurrentUser = member.userId == currentUserId
    
    // Get location name for this member
    LaunchedEffect(member.latitude, member.longitude) {
        if (member.isActive && member.latitude != 0.0 && member.longitude != 0.0) {
            try {
                val geocoder = Geocoder(context)
                val addresses = geocoder.getFromLocation(member.latitude, member.longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    
                    // Log all available address components for debugging
                    android.util.Log.d("Geocoding", """
                        Address for ${member.name}:
                        featureName: ${address.featureName}
                        subThoroughfare: ${address.subThoroughfare}
                        thoroughfare: ${address.thoroughfare}
                        subLocality: ${address.subLocality}
                        locality: ${address.locality}
                        subAdminArea: ${address.subAdminArea}
                        premises: ${address.premises}
                        Full address: ${address.getAddressLine(0)}
                    """.trimIndent())
                    
                    // Try to get the most detailed address line first
                    val addressLine = address.getAddressLine(0)
                    
                    if (addressLine != null && addressLine.isNotEmpty()) {
                        // Parse the full address line to get street-level detail
                        val addressParts = addressLine.split(",").map { it.trim() }
                        locationName = when {
                            addressParts.size >= 2 -> "${addressParts[0]}, ${addressParts[1]}"
                            addressParts.size == 1 -> addressParts[0]
                            else -> addressLine
                        }
                    } else {
                        // Fallback to building from components
                        val parts = mutableListOf<String>()
                        
                        // Add premises or feature name
                        if (address.premises != null) {
                            parts.add(address.premises)
                        } else if (address.featureName != null && address.featureName != address.locality) {
                            parts.add(address.featureName)
                        }
                        
                        // Add street number and name
                        if (address.subThoroughfare != null && address.thoroughfare != null) {
                            parts.add("${address.subThoroughfare} ${address.thoroughfare}")
                        } else if (address.thoroughfare != null) {
                            parts.add(address.thoroughfare)
                        }
                        
                        // Add subLocality or locality
                        if (address.subLocality != null) {
                            parts.add(address.subLocality)
                        } else if (address.locality != null) {
                            parts.add(address.locality)
                        }
                        
                        locationName = if (parts.isNotEmpty()) {
                            parts.take(2).joinToString(", ")
                        } else {
                            "${member.latitude}, ${member.longitude}"
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Geocoding", "Error geocoding for ${member.name}", e)
                locationName = "${member.latitude}, ${member.longitude}"
            }
        }
    }
    
    // Check if currently sharing with this member OR if they're coming to you
    LaunchedEffect(member.email) {
        while (true) {
            try {
                // Check if I'm going to them
                val contacts = locationService.getContacts()
                val contact = contacts.find { it.email == member.email }
                isSharing = contact != null
                
                android.util.Log.d("MemberCard", "Checking ${member.name}: isSharing=$isSharing")
                
                // Check if they're coming to me
                val incomingRides = locationService.getIncomingRides()
                android.util.Log.d("MemberCard", "Incoming rides count: ${incomingRides.size}")
                
                incomingRides.forEach { ride ->
                    android.util.Log.d("MemberCard", "Incoming ride from: ${ride["senderEmail"]} (looking for ${member.email})")
                }
                
                val incomingRide = incomingRides.find { 
                    it["senderEmail"] == member.email 
                }
                someoneComingToMe = incomingRide != null
                
                android.util.Log.d("MemberCard", "${member.name} coming to me: $someoneComingToMe")
                
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                    fusedLocationClient.lastLocation.addOnSuccessListener { myLocation ->
                        myLocation?.let {
                            if (isSharing) {
                                // I'm going to them - calculate distance from me to them
                                val distance = locationService.calculateDistance(
                                    it.latitude,
                                    it.longitude,
                                    member.latitude,
                                    member.longitude
                                )
                                currentDistance = distance
                                currentETA = locationService.calculateETA(distance, it.speed.toDouble())
                            } else if (someoneComingToMe && member.latitude != 0.0 && member.longitude != 0.0) {
                                // They're coming to me - calculate distance from them to me
                                val distance = locationService.calculateDistance(
                                    member.latitude,
                                    member.longitude,
                                    it.latitude,
                                    it.longitude
                                )
                                currentDistance = distance
                                currentETA = locationService.calculateETA(distance, 0.0)
                            } else {
                                currentDistance = null
                                currentETA = null
                            }
                        }
                    }
                }
                
                delay(10000) // Update every 10 seconds
            } catch (e: Exception) {
                e.printStackTrace()
                delay(10000)
            }
        }
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMemberClick(member) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: Icon and info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (member.isActive)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = member.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Location name - always show if available
                    if (locationName != null) {
                        Text(
                            text = locationName!!,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    
                    // Status or ETA
                    if (isSharing) {
                        // You're going to them
                        if (currentETA != null && currentDistance != null) {
                            Text(
                                text = when {
                                    currentDistance!! < 100 -> "You've arrived!"
                                    currentETA!! <= 1 -> "Arriving in less than 1 min"
                                    else -> "Arriving in $currentETA min"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    currentDistance!! < 100 -> MaterialTheme.colorScheme.primary
                                    currentETA!! <= 2 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                            )
                        } else {
                            Text(
                                text = "Calculating ETA...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    } else if (currentETA != null && currentDistance != null) {
                        // They're coming to you
                        Text(
                            text = when {
                                currentDistance!! < 100 -> "${member.name} has arrived!"
                                currentETA!! <= 1 -> "${member.name} arriving in less than 1 min"
                                else -> "${member.name} arriving in $currentETA min"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                currentDistance!! < 100 -> MaterialTheme.colorScheme.primary
                                currentETA!! <= 2 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Show last updated time
                            if (member.lastUpdated > 0) {
                                val timeDiff = System.currentTimeMillis() - member.lastUpdated
                                val minutesAgo = (timeDiff / 60000).toInt()
                                val hoursAgo = minutesAgo / 60
                                val daysAgo = hoursAgo / 24
                                
                                Text(
                                    text = when {
                                        minutesAgo < 1 -> "Just now"
                                        minutesAgo < 60 -> "$minutesAgo min ago"
                                        hoursAgo < 24 -> "${hoursAgo}h ago"
                                        else -> "${daysAgo}d ago"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Battery indicator
                            if (member.batteryLevel > 0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BatteryFull,
                                        contentDescription = "Battery",
                                        modifier = Modifier.size(14.dp),
                                        tint = when {
                                            member.batteryLevel > 50 -> MaterialTheme.colorScheme.primary
                                            member.batteryLevel > 20 -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Text(
                                        text = "${member.batteryLevel}%",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Right side: Button - only show if not current user and they have a valid location
            if (!isCurrentUser && member.latitude != 0.0 && member.longitude != 0.0) {
                if (isSharing) {
                    // Show "Stop Sharing" button if I'm going to them
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    locationService.removeContact(member.email)
                                    isSharing = false
                                    android.widget.Toast.makeText(
                                        context,
                                        "Stopped sharing with ${member.name}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "Failed to stop sharing: ${e.message}",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Stop Sharing", fontSize = 12.sp)
                    }
                } else if (!someoneComingToMe) {
                    // Only show "Share Ride" button if they're NOT already coming to me
                    Button(
                        onClick = {
                            android.util.Log.d("ShareRideButton", "Button clicked for ${member.name}")
                            onShareRide()
                            isSharing = true
                        },
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share Ride", fontSize = 12.sp)
                    }
                }
            }
        }
    }}



