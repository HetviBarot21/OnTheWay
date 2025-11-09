package com.example.ontheway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userEmail: String,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToCircles: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val circleService = remember { CircleService() }
    val locationService = remember { LocationService(context) }
    
    var hasLocationPermission by remember { mutableStateOf(false) }
    var allMembers by remember { mutableStateOf(listOf<CircleMember>()) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            try {
                hasLocationPermission = granted
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

    // Load all members from all circles and refresh every 10 seconds
    LaunchedEffect(Unit) {
        try {
            // Check location permission
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            
            hasLocationPermission = granted
            
            // Get FCM token
            try {
                val fcmToken = NotificationHelper.getFCMToken()
                if (fcmToken != null) {
                    locationService.saveFCMToken(fcmToken)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Refresh members from all circles periodically
            while (true) {
                val circles = circleService.getUserCircles()
                val members = mutableListOf<CircleMember>()
                
                for (circle in circles) {
                    val circleMembers = circleService.getCircleMembers(circle.circleId)
                    members.addAll(circleMembers)
                }
                
                allMembers = members.distinctBy { it.userId }
                delay(10000) // 10 seconds
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Map takes 60% of screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                ) {
                    AllMembersMap(members = allMembers)
                }
                
                // Members list takes remaining 40%
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
                ) {
                    MembersList(
                        members = allMembers,
                        onShareRide = { member ->
                            scope.launch {
                                try {
                                    // Share ride with this member - use their location as destination
                                    locationService.addContact(
                                        member.email,
                                        member.latitude,
                                        member.longitude
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
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

@Composable
fun AllMembersMap(members: List<CircleMember>) {
    val context = LocalContext.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    
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
                try {
                    mv.getMapboxMap().loadStyle(Style.MAPBOX_STREETS) { style ->
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
                            }
                            
                            // Add markers for all active members
                            val annotationApi = mv.annotations
                            val pointAnnotationManager = annotationApi.createPointAnnotationManager()
                            
                            members.filter { it.isActive }.forEach { member ->
                                val pointAnnotationOptions = PointAnnotationOptions()
                                    .withPoint(Point.fromLngLat(member.longitude, member.latitude))
                                    .withTextField(member.name)
                                
                                pointAnnotationManager.create(pointAnnotationOptions)
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
                val annotationApi = mv.annotations
                val pointAnnotationManager = annotationApi.createPointAnnotationManager()
                pointAnnotationManager.deleteAll()
                
                members.filter { it.isActive }.forEach { member ->
                    val pointAnnotationOptions = PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(member.longitude, member.latitude))
                        .withTextField(member.name)
                    
                    pointAnnotationManager.create(pointAnnotationOptions)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    )
}

@Composable
fun MembersList(
    members: List<CircleMember>,
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
                        onShareRide = { onShareRide(members[index]) }
                    )
                }
            }
        }
    }
}

@Composable
fun MemberCard(
    member: CircleMember,
    onShareRide: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (member.isActive)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    if (member.isActive) Icons.Default.LocationOn else Icons.Default.LocationOff,
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
                    if (member.isActive) {
                        Text(
                            text = "Active now",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "Offline",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            if (member.isActive) {
                Button(
                    onClick = onShareRide,
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
}


