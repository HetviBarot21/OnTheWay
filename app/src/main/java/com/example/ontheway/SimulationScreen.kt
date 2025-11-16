package com.example.ontheway

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.example.ontheway.services.LocationSimulationService
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.location
import kotlinx.coroutines.launch

data class SimulationRoute(
    val name: String,
    val points: List<Point>,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val simulationService = remember { LocationSimulationService(context) }
    
    var isSimulating by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var simulationSpeed by remember { mutableStateOf(1f) }
    var selectedRoute by remember { mutableStateOf<SimulationRoute?>(null) }
    var showRouteSelector by remember { mutableStateOf(false) }
    var currentPointIndex by remember { mutableStateOf(0) }
    var totalPoints by remember { mutableStateOf(0) }
    
    // Predefined routes for testing
    val testRoutes = remember {
        listOf(
            SimulationRoute(
                name = "Short Test (2km)",
                points = generateShortRoute(),
                description = "2km route, ~5 min drive"
            ),
            SimulationRoute(
                name = "Medium Test (5km)",
                points = generateMediumRoute(),
                description = "5km route, ~12 min drive"
            ),
            SimulationRoute(
                name = "Long Test (10km)",
                points = generateLongRoute(),
                description = "10km route, ~20 min drive"
            ),
            SimulationRoute(
                name = "Custom GPX",
                points = emptyList(),
                description = "Load from test_route.gpx"
            )
        )
    }
    
    // Listen to simulation updates
    LaunchedEffect(Unit) {
        simulationService.locationFlow.collect { location ->
            currentLocation = location
        }
    }
    
    LaunchedEffect(Unit) {
        simulationService.progressFlow.collect { progress ->
            currentPointIndex = progress.first
            totalPoints = progress.second
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location Simulation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Map view
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SimulationMapView(
                    currentLocation = currentLocation,
                    routePoints = selectedRoute?.points ?: emptyList(),
                    isSimulating = isSimulating
                )
            }
            
            // Controls
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Route selection
                    OutlinedButton(
                        onClick = { showRouteSelector = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSimulating
                    ) {
                        Icon(Icons.Default.Place, null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedRoute?.name ?: "Select Route")
                    }
                    
                    if (selectedRoute != null) {
                        Text(
                            text = selectedRoute!!.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Progress
                    if (isSimulating && totalPoints > 0) {
                        Column {
                            LinearProgressIndicator(
                                progress = currentPointIndex.toFloat() / totalPoints,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Point $currentPointIndex of $totalPoints",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    
                    // Speed control
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speed: ${simulationSpeed}x", modifier = Modifier.width(80.dp))
                        Slider(
                            value = simulationSpeed,
                            onValueChange = { 
                                simulationSpeed = it
                                if (isSimulating) {
                                    simulationService.setSpeed(it)
                                }
                            },
                            valueRange = 0.5f..5f,
                            steps = 8,
                            enabled = !isSimulating
                        )
                    }
                    
                    // Control buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isSimulating) {
                                    scope.launch {
                                        simulationService.stopSimulation()
                                        isSimulating = false
                                    }
                                } else {
                                    selectedRoute?.let { route ->
                                        scope.launch {
                                            isSimulating = true
                                            simulationService.startSimulation(
                                                route.points,
                                                simulationSpeed
                                            )
                                            isSimulating = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = selectedRoute != null,
                            colors = if (isSimulating) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Icon(
                                if (isSimulating) Icons.Default.Stop else Icons.Default.PlayArrow,
                                null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSimulating) "Stop" else "Start")
                        }
                        
                        if (isSimulating) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        if (simulationService.isPaused()) {
                                            simulationService.resumeSimulation()
                                        } else {
                                            simulationService.pauseSimulation()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    if (simulationService.isPaused()) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    null
                                )
                            }
                        }
                    }
                    
                    // Current location info
                    currentLocation?.let { loc ->
                        Divider()
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Current Location",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "Lat: ${"%.6f".format(loc.latitude)}",
                                fontSize = 12.sp
                            )
                            Text(
                                "Lng: ${"%.6f".format(loc.longitude)}",
                                fontSize = 12.sp
                            )
                            Text(
                                "Speed: ${"%.1f".format(loc.speed)} m/s",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Route selector dialog
    if (showRouteSelector) {
        AlertDialog(
            onDismissRequest = { showRouteSelector = false },
            title = { Text("Select Test Route") },
            text = {
                LazyColumn {
                    items(testRoutes) { route ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = {
                                selectedRoute = route
                                showRouteSelector = false
                            }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    route.name,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    route.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRouteSelector = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SimulationMapView(
    currentLocation: Location?,
    routePoints: List<Point>,
    isSimulating: Boolean
) {
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
                mv.getMapboxMap().loadStyle(Style.MAPBOX_STREETS) { style ->
                    // Enable gestures for zoom and pan
                    mv.gestures.updateSettings {
                        pinchToZoomEnabled = true
                        rotateEnabled = true
                        scrollEnabled = true
                    }
                    
                    // Enable location component
                    if (ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        mv.location.updateSettings {
                            this.enabled = true
                            this.pulsingEnabled = true
                        }
                    }
                }
            }
        },
        update = { mv ->
            currentLocation?.let { loc ->
                // Update camera to follow location
                mv.getMapboxMap().setCamera(
                    CameraOptions.Builder()
                        .center(Point.fromLngLat(loc.longitude, loc.latitude))
                        .zoom(14.0)
                        .build()
                )
                
                // Update location marker
                try {
                    val annotationApi = mv.annotations
                    val pointAnnotationManager = annotationApi.createPointAnnotationManager()
                    pointAnnotationManager.deleteAll()
                    
                    // Add current location marker
                    val currentMarker = PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(loc.longitude, loc.latitude))
                        .withTextField("You")
                        .withIconSize(1.5)
                    pointAnnotationManager.create(currentMarker)
                    
                    // Add route points if available
                    if (routePoints.isNotEmpty()) {
                        // Start point
                        val startMarker = PointAnnotationOptions()
                            .withPoint(routePoints.first())
                            .withTextField("Start")
                        pointAnnotationManager.create(startMarker)
                        
                        // End point
                        val endMarker = PointAnnotationOptions()
                            .withPoint(routePoints.last())
                            .withTextField("End")
                        pointAnnotationManager.create(endMarker)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )
}

// Generate test routes
private fun generateShortRoute(): List<Point> {
    val start = Point.fromLngLat(-122.4194, 37.7749) // San Francisco
    val end = Point.fromLngLat(-122.4094, 37.7849)
    return interpolateRoute(start, end, 20) // 20 points
}

private fun generateMediumRoute(): List<Point> {
    val start = Point.fromLngLat(-122.4194, 37.7749)
    val end = Point.fromLngLat(-122.3894, 37.7949)
    return interpolateRoute(start, end, 50) // 50 points
}

private fun generateLongRoute(): List<Point> {
    val start = Point.fromLngLat(-122.4194, 37.7749)
    val end = Point.fromLngLat(-122.3594, 37.8149)
    return interpolateRoute(start, end, 100) // 100 points
}

private fun interpolateRoute(start: Point, end: Point, numPoints: Int): List<Point> {
    val points = mutableListOf<Point>()
    for (i in 0 until numPoints) {
        val fraction = i.toDouble() / (numPoints - 1)
        val lng = start.longitude() + (end.longitude() - start.longitude()) * fraction
        val lat = start.latitude() + (end.latitude() - start.latitude()) * fraction
        points.add(Point.fromLngLat(lng, lat))
    }
    return points
}
