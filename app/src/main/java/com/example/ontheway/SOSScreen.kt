package com.example.ontheway

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ontheway.services.CircleService
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val circleService = remember { CircleService() }
    val emailService = remember { EmailNotificationService() }
    
    var showCountdown by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(10) }
    var isSending by remember { mutableStateOf(false) }
    var sosTriggered by remember { mutableStateOf(false) }
    var sosError by remember { mutableStateOf<String?>(null) }
    var emailsSentCount by remember { mutableIntStateOf(0) }
    var notificationsSentCount by remember { mutableIntStateOf(0) }
    
    // Function to send SOS
    fun sendSOS() {
        scope.launch {
            isSending = true
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("Not authenticated")
                
                // Get current user's data from Firestore to get email and name
                val currentUserDoc = firestore.collection("users")
                    .document(userId)
                    .get()
                    .await()
                
                val currentUserEmail = currentUserDoc.getString("email") ?: auth.currentUser?.email ?: "Unknown"
                val userName = currentUserDoc.getString("name") ?: auth.currentUser?.displayName ?: currentUserEmail
                
                android.util.Log.d("SOSScreen", "Current user: $userName ($currentUserEmail)")
                
                // Get current location
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                    != PackageManager.PERMISSION_GRANTED) {
                    throw Exception("Location permission not granted")
                }
                
                val location = fusedLocationClient.lastLocation.await()
                if (location == null) {
                    throw Exception("Could not get current location")
                }
                
                val latitude = location.latitude
                val longitude = location.longitude
                val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"
                val timestamp = System.currentTimeMillis()
                
                android.util.Log.d("SOSScreen", "Location: $latitude, $longitude")
                
                // Get all circles user is in
                val circles = circleService.getUserCircles()
                
                if (circles.isEmpty()) {
                    throw Exception("You are not in any circles")
                }
                
                android.util.Log.d("SOSScreen", "Found ${circles.size} circles")
                
                // Track unique members to avoid duplicates
                val processedMembers = mutableSetOf<String>()
                var emailsSent = 0
                var notificationsSent = 0
                
                android.util.Log.d("SOSScreen", "=== Starting member processing ===")
                
                // Send notification to all circle members
                for (circle in circles) {
                    android.util.Log.d("SOSScreen", "Processing circle: ${circle.name} with ${circle.members.size} members")
                    android.util.Log.d("SOSScreen", "Circle members: ${circle.members}")
                    
                    for (memberId in circle.members) {
                        // Skip current user and already processed members
                        if (memberId == userId) {
                            android.util.Log.d("SOSScreen", "Skipping current user: $memberId")
                            continue
                        }
                        
                        if (processedMembers.contains(memberId)) {
                            android.util.Log.d("SOSScreen", "Skipping already processed member: $memberId")
                            continue
                        }
                        
                        processedMembers.add(memberId)
                        android.util.Log.d("SOSScreen", "Processing new member: $memberId")
                        
                        try {
                            // Get member's data from Firestore
                            android.util.Log.d("SOSScreen", "Fetching user data for: $memberId")
                            val memberDoc = firestore.collection("users")
                                .document(memberId)
                                .get()
                                .await()
                            
                            if (!memberDoc.exists()) {
                                android.util.Log.w("SOSScreen", "User document doesn't exist for: $memberId")
                                continue
                            }
                            
                            val memberEmail = memberDoc.getString("email")
                            val memberName = memberDoc.getString("name") ?: memberEmail ?: "Unknown"
                            
                            android.util.Log.d("SOSScreen", "Member data: name=$memberName, email=$memberEmail")
                            android.util.Log.d("SOSScreen", "Email is ${if (memberEmail.isNullOrEmpty()) "EMPTY/NULL" else "VALID"}")
                            
                            if (memberEmail != null && memberEmail.isNotEmpty()) {
                                // Create SOS notification in Firestore
                                val notification = hashMapOf(
                                    "title" to "🚨 EMERGENCY SOS",
                                    "message" to "$userName has sent an SOS! Last known location: $mapsLink",
                                    "type" to "SOS",
                                    "fromUserId" to userId,
                                    "fromUserName" to userName,
                                    "fromUserEmail" to currentUserEmail,
                                    "latitude" to latitude,
                                    "longitude" to longitude,
                                    "mapsLink" to mapsLink,
                                    "timestamp" to timestamp,
                                    "read" to false
                                )
                                
                                // Add to member's notifications collection
                                firestore.collection("users")
                                    .document(memberId)
                                    .collection("notifications")
                                    .add(notification)
                                    .await()
                                
                                notificationsSent++
                                android.util.Log.d("SOSScreen", "Notification created for $memberName")
                                
                                // Send email notification using same pattern as Share Ride
                                try {
                                    android.util.Log.d("SOSScreen", "=== Queueing SOS email ===")
                                    android.util.Log.d("SOSScreen", "To: $memberEmail")
                                    android.util.Log.d("SOSScreen", "From: $userName")
                                    
                                    // Use the same EmailNotificationService instance
                                    val emailSvc = EmailNotificationService()
                                    emailSvc.sendSOSEmail(
                                        memberEmail,
                                        userName,
                                        latitude,
                                        longitude,
                                        timestamp
                                    )
                                    emailsSent++
                                    android.util.Log.d("SOSScreen", "✓ Email queued for $memberEmail")
                                } catch (e: Exception) {
                                    android.util.Log.e("SOSScreen", "✗ Email failed for $memberEmail", e)
                                    e.printStackTrace()
                                }
                            } else {
                                android.util.Log.w("SOSScreen", "Member $memberId has no email")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("SOSScreen", "Error processing member $memberId", e)
                        }
                    }
                }
                
                // Store SOS event in Firestore
                val sosEvent = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "userEmail" to currentUserEmail,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "mapsLink" to mapsLink,
                    "timestamp" to timestamp,
                    "notificationsSent" to notificationsSent,
                    "emailsSent" to emailsSent,
                    "circlesCount" to circles.size,
                    "uniqueMembersNotified" to processedMembers.size
                )
                
                android.util.Log.d("SOSScreen", "=== Member Processing Complete ===")
                android.util.Log.d("SOSScreen", "Total members processed: ${processedMembers.size}")
                android.util.Log.d("SOSScreen", "Notifications created: $notificationsSent")
                android.util.Log.d("SOSScreen", "Emails queued: $emailsSent")
                
                if (emailsSent == 0) {
                    android.util.Log.e("SOSScreen", "⚠️ WARNING: NO EMAILS WERE QUEUED!")
                    android.util.Log.e("SOSScreen", "Check if members have email addresses in Firestore")
                }
                
                android.util.Log.d("SOSScreen", "=== Storing SOS Event ===")
                android.util.Log.d("SOSScreen", "Notifications sent: $notificationsSent")
                android.util.Log.d("SOSScreen", "Emails queued: $emailsSent")
                android.util.Log.d("SOSScreen", "Circles: ${circles.size}")
                android.util.Log.d("SOSScreen", "Unique members: ${processedMembers.size}")
                
                val sosEventRef = firestore.collection("sos_events")
                    .add(sosEvent)
                    .await()
                
                android.util.Log.d("SOSScreen", "✓ SOS event stored in Firestore with ID: ${sosEventRef.id}")
                android.util.Log.d("SOSScreen", "Check Firebase Console → Firestore → sos_events → ${sosEventRef.id}")
                
                // Update state
                emailsSentCount = emailsSent
                notificationsSentCount = notificationsSent
                sosTriggered = true
                
                android.util.Log.d("SOSScreen", "SOS sent successfully: $notificationsSent notifications, $emailsSent emails")
                
            } catch (e: Exception) {
                android.util.Log.e("SOSScreen", "Error sending SOS", e)
                sosError = e.message ?: "Unknown error occurred"
            } finally {
                isSending = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency SOS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFD32F2F)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Emergency",
                modifier = Modifier.size(120.dp),
                tint = Color(0xFFD32F2F)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Emergency SOS",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Press the button below to send emergency alerts",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (showCountdown) {
                Text(
                    text = countdown.toString(),
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD32F2F)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        showCountdown = false
                        countdown = 10
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text("Cancel", fontSize = 18.sp)
                }
            } else {
                Button(
                    onClick = {
                        showCountdown = true
                        // TODO: Start countdown and send SOS
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    Text(
                        text = "SEND SOS",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "How it works:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("1. Press the SOS button")
                    Text("2. 10-second countdown begins")
                    Text("3. All circle members will be notified")
                    Text("4. Your location will be shared")
                }
            }
        }
    }
    
    // Show success dialog
    if (sosTriggered) {
        AlertDialog(
            onDismissRequest = { sosTriggered = false },
            title = { Text("🚨 SOS Alert Sent!") },
            text = { 
                Column {
                    Text("Emergency alert has been sent successfully!")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• $notificationsSentCount in-app notifications sent")
                    Text("• $emailsSentCount emails queued")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Your circle members have been notified with your current location.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { 
                    sosTriggered = false
                    onBack()
                }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Show error dialog
    if (sosError != null) {
        AlertDialog(
            onDismissRequest = { sosError = null },
            title = { Text("Error") },
            text = { Text(sosError ?: "Unknown error") },
            confirmButton = {
                Button(onClick = { sosError = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Show loading
    if (isSending) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Sending SOS...") },
            text = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Alerting your circle members...")
                }
            },
            confirmButton = { }
        )
    }

    // Countdown effect - only handles countdown, doesn't send SOS
    LaunchedEffect(showCountdown, countdown) {
        if (showCountdown && countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        } else if (showCountdown && countdown == 0) {
            // Reset countdown state
            showCountdown = false
            countdown = 10
            // Trigger SOS sending
            sendSOS()
        }
    }
}
