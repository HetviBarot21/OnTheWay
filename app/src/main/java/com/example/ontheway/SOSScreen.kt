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
    
    var showCountdown by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(15) }
    var isSending by remember { mutableStateOf(false) }
    var sosTriggered by remember { mutableStateOf(false) }
    var sosError by remember { mutableStateOf<String?>(null) }

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
                        countdown = 15
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
                    Text("2. 15-second countdown begins")
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
                Text("Emergency alert has been sent to all your circle members with your current location.")
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

    // Countdown effect
    LaunchedEffect(showCountdown, countdown) {
        if (showCountdown && countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        } else if (showCountdown && countdown == 0) {
            // SOS triggered - send alerts!
            showCountdown = false
            countdown = 15
            
            // Send SOS alerts directly in LaunchedEffect
            isSending = true
            try {
                val userId = auth.currentUser?.uid ?: throw Exception("Not authenticated")
                val userName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "Someone"
                
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
                
                // Get all circles user is in
                val circles = circleService.getUserCircles()
                
                if (circles.isEmpty()) {
                    throw Exception("You are not in any circles")
                }
                
                // Send notification to all circle members
                var notificationsSent = 0
                for (circle in circles) {
                    for (memberId in circle.members) {
                        if (memberId != userId) {
                            try {
                                val memberDoc = firestore.collection("users")
                                    .document(memberId)
                                    .get()
                                    .await()
                                
                                val memberEmail = memberDoc.getString("email")
                                
                                if (memberEmail != null) {
                                    // Create SOS notification
                                    val notification = hashMapOf(
                                        "title" to "🚨 EMERGENCY SOS",
                                        "message" to "$userName has sent an SOS! Last known location: $mapsLink",
                                        "type" to "SOS",
                                        "fromUserId" to userId,
                                        "fromUserName" to userName,
                                        "latitude" to latitude,
                                        "longitude" to longitude,
                                        "mapsLink" to mapsLink,
                                        "timestamp" to System.currentTimeMillis()
                                    )
                                    
                                    // Add to member's notifications collection
                                    firestore.collection("users")
                                        .document(memberId)
                                        .collection("notifications")
                                        .add(notification)
                                        .await()
                                    
                                    // Send email notification
                                    try {
                                        val emailData = hashMapOf(
                                            "to" to memberEmail,
                                            "from" to "noreply@ontheway.app",
                                            "subject" to "🚨 EMERGENCY SOS from $userName",
                                            "html" to """
                                                <html>
                                                <body style="font-family: Arial, sans-serif; padding: 20px;">
                                                    <div style="background-color: #d32f2f; color: white; padding: 20px; border-radius: 8px;">
                                                        <h1>🚨 EMERGENCY SOS ALERT</h1>
                                                    </div>
                                                    <div style="padding: 20px; background-color: #f5f5f5; margin-top: 20px; border-radius: 8px;">
                                                        <p style="font-size: 18px;"><strong>$userName</strong> has sent an emergency SOS alert!</p>
                                                        
                                                        <h3>Last Known Location:</h3>
                                                        <p>
                                                            <strong>Latitude:</strong> $latitude<br>
                                                            <strong>Longitude:</strong> $longitude
                                                        </p>
                                                        
                                                        <a href="$mapsLink" style="display: inline-block; background-color: #4285f4; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; margin-top: 10px;">
                                                            View on Google Maps
                                                        </a>
                                                        
                                                        <p style="margin-top: 20px; color: #666;">
                                                            <strong>Time:</strong> ${java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
                                                        </p>
                                                        
                                                        <p style="margin-top: 20px; color: #d32f2f; font-weight: bold;">
                                                            Please check on them immediately!
                                                        </p>
                                                    </div>
                                                    <p style="margin-top: 20px; color: #999; font-size: 12px;">
                                                        This is an automated message from OnTheWay App
                                                    </p>
                                                </body>
                                                </html>
                                            """.trimIndent(),
                                            "timestamp" to System.currentTimeMillis(),
                                            "status" to "pending"
                                        )
                                        
                                        firestore.collection("mail")
                                            .add(emailData)
                                            .await()
                                        
                                        android.util.Log.d("SOSScreen", "Email queued for $memberEmail")
                                    } catch (e: Exception) {
                                        android.util.Log.e("SOSScreen", "Error sending email to $memberEmail", e)
                                    }
                                    
                                    // Show local notification
                                    NotificationHelper.showLocalNotification(
                                        context,
                                        "🚨 EMERGENCY SOS",
                                        "$userName has sent an SOS! Location: $latitude, $longitude"
                                    )
                                    
                                    notificationsSent++
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("SOSScreen", "Error sending to $memberId", e)
                            }
                        }
                    }
                }
                
                // Store SOS event
                val sosEvent = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "timestamp" to System.currentTimeMillis(),
                    "notificationsSent" to notificationsSent
                )
                
                firestore.collection("sos_events")
                    .add(sosEvent)
                    .await()
                
                sosTriggered = true
                android.util.Log.d("SOSScreen", "SOS sent to $notificationsSent members")
                
            } catch (e: Exception) {
                android.util.Log.e("SOSScreen", "Error sending SOS", e)
                sosError = e.message
            } finally {
                isSending = false
            }
        }
    }
    
    // Show alert when SOS is triggered
    if (sosTriggered) {
        AlertDialog(
            onDismissRequest = { sosTriggered = false },
            title = { Text("SOS Alert Sent!") },
            text = { 
                Text("Emergency alert has been sent to your circle members with your current location.")
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
}
