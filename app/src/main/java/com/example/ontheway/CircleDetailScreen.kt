package com.example.ontheway

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.ontheway.models.Circle
import com.example.ontheway.models.CircleMember
import com.example.ontheway.services.CircleService

import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.example.ontheway.utils.getContactEmail
import com.example.ontheway.utils.getContactPhone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleDetailScreen(
    circleId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val circleService = remember { CircleService() }
    
    var members by remember { mutableStateOf(listOf<CircleMember>()) }
    var circle by remember { mutableStateOf<Circle?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showMembersList by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }

    // Load circle info and refresh members every 10 seconds
    LaunchedEffect(circleId) {
        android.util.Log.d("CircleDetailScreen", "Starting circle detail for: $circleId")
        
        // Load circle info once
        val circles = circleService.getUserCircles()
        circle = circles.find { it.circleId == circleId }
        
        // Refresh members periodically
        while (true) {
            val fetchedMembers = circleService.getCircleMembers(circleId)
            android.util.Log.d("CircleDetailScreen", "Fetched ${fetchedMembers.size} members")
            
            // Members already have isActive calculated in CircleService
            android.util.Log.d("CircleDetailScreen", "Members: ${fetchedMembers.map { "${it.name}: isActive=${it.isActive}" }}")
            
            members = fetchedMembers
            isLoading = false
            delay(5000) // 5 seconds - faster refresh for testing
        }
    }
    


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Circle Members") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInviteDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Invite")
                    }
                    IconButton(onClick = { showMembersList = !showMembersList }) {
                        Icon(
                            if (showMembersList) Icons.Default.Place else Icons.Default.List,
                            contentDescription = "Toggle view"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                if (showMembersList) {
                    MembersList(members = members)
                } else {
                    CircleMembersMap(members = members)
                }
            }
        }
    }
    
    // Show invite dialog
    if (showInviteDialog && circle != null) {
        InviteToCircleDialog(
            circle = circle!!,
            onDismiss = { showInviteDialog = false },
            onInvite = { phoneOrEmail ->
                scope.launch {
                    val success = circleService.inviteUserToCircle(circleId, phoneOrEmail)
                    if (success) {
                        showInviteDialog = false
                    }
                }
            }
        )
    }
}

@Composable
fun CircleMembersMap(members: List<CircleMember>) {
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
                            
                            // Add markers for members
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
fun MembersList(members: List<CircleMember>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(members) { member ->
            MemberCard(member = member)
        }
    }
}

@Composable
fun MemberCard(member: CircleMember) {
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Active status indicator
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = if (member.isActive) androidx.compose.ui.graphics.Color(0xFF4CAF50) else androidx.compose.ui.graphics.Color(0xFF9E9E9E),
                        modifier = Modifier.size(12.dp)
                    ) {}
                    
                    Text(
                        text = member.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (member.isActive) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Active",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = member.email,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (member.isActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (member.isSharingTrip && member.eta != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "ETA: ${member.eta} min",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            text = "Last updated: ${formatTimestamp(member.lastUpdated)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Icon(
                if (member.isActive) Icons.Default.LocationOn else Icons.Default.LocationOff,
                contentDescription = null,
                tint = if (member.isActive) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "${hours / 24} days ago"
    }
}

@Composable
fun InviteToCircleDialog(
    circle: Circle,
    onDismiss: () -> Unit,
    onInvite: (String) -> Unit
) {
    val context = LocalContext.current
    var phoneOrEmail by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var showShareOptions by remember { mutableStateOf(false) }
    
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact(),
        onResult = { uri ->
            uri?.let {
                // Try to get phone number first, then email
                val phone = getContactPhone(context, it)
                val email = getContactEmail(context, it)
                phoneOrEmail = phone ?: email ?: ""
                if (phoneOrEmail.isEmpty()) {
                    errorMessage = "No phone or email found for this contact"
                }
            }
        }
    )
    
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                contactPickerLauncher.launch(null)
            } else {
                errorMessage = "Contacts permission denied"
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite to ${circle.name}") },
        text = {
            Column {
                Text(
                    text = "Invite code: ${circle.inviteCode}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Text(
                    text = "Select a contact or enter phone/email",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = phoneOrEmail,
                        onValueChange = {
                            phoneOrEmail = it
                            errorMessage = ""
                        },
                        label = { Text("Phone or Email") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = errorMessage.isNotEmpty(),
                        placeholder = { Text("+1234567890 or email@example.com") }
                    )
                    
                    IconButton(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                            
                            if (hasPermission) {
                                contactPickerLauncher.launch(null)
                            } else {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Pick from contacts",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { showShareOptions = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Invite Link")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        phoneOrEmail.isBlank() -> {
                            errorMessage = "Please enter phone or email"
                        }
                        else -> {
                            onInvite(phoneOrEmail.trim())
                        }
                    }
                },
                enabled = phoneOrEmail.isNotBlank()
            ) {
                Text("Send Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Share invite link
    if (showShareOptions) {
        val inviteMessage = "Join my circle '${circle.name}' on OnTheWay!\n\n" +
                "Use invite code: ${circle.inviteCode}\n\n" +
                "Download the app and enter this code to join."
        
        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, inviteMessage)
            type = "text/plain"
        }
        
        val shareIntent = android.content.Intent.createChooser(sendIntent, "Share invite via")
        context.startActivity(shareIntent)
        showShareOptions = false
    }
}


