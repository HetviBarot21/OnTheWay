package com.example.ontheway

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ontheway.utils.DatabaseStats
import com.example.ontheway.utils.FirebaseInitializer
import com.example.ontheway.utils.ServiceStatus
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

/**
 * Debug screen to verify Firebase setup
 * Access this by adding a debug button in your app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()
    
    var serviceStatus by remember { mutableStateOf<ServiceStatus?>(null) }
    var databaseStats by remember { mutableStateOf<DatabaseStats?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            serviceStatus = FirebaseInitializer.verifyAllServices()
            if (auth.currentUser != null) {
                databaseStats = FirebaseInitializer.getDatabaseStats()
            }
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unknown error"
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Firebase Setup Status") },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Refresh button
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = ""
                        try {
                            serviceStatus = FirebaseInitializer.verifyAllServices()
                            if (auth.currentUser != null) {
                                databaseStats = FirebaseInitializer.getDatabaseStats()
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Unknown error"
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Status")
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Authentication Status
                StatusCard(
                    title = "Firebase Authentication",
                    isEnabled = serviceStatus?.authEnabled == true,
                    details = if (auth.currentUser != null) {
                        "Logged in as: ${auth.currentUser?.email}"
                    } else {
                        "Not logged in"
                    }
                )

                // Firestore Status
                StatusCard(
                    title = "Cloud Firestore",
                    isEnabled = serviceStatus?.firestoreEnabled == true,
                    details = if (serviceStatus?.firestoreEnabled == true) {
                        "Database is accessible"
                    } else {
                        "Enable Firestore in Firebase Console"
                    }
                )

                // User Profile Status
                if (auth.currentUser != null) {
                    StatusCard(
                        title = "User Profile",
                        isEnabled = serviceStatus?.userProfileExists == true,
                        details = if (serviceStatus?.userProfileExists == true) {
                            "Profile exists in Firestore"
                        } else {
                            "Profile not found - may need to re-register"
                        }
                    )
                }

                // Database Statistics
                if (databaseStats != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Database Statistics",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Divider()
                            StatRow("Circles", databaseStats!!.circleCount.toString())
                            StatRow("Pending Invites", databaseStats!!.pendingInvites.toString())
                            StatRow(
                                "Location Data",
                                if (databaseStats!!.hasLocationData) "Yes" else "No"
                            )
                        }
                    }
                }

                // Error Message
                if (errorMessage.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Error",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Setup Instructions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Setup Instructions",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = """
                                1. Enable Authentication in Firebase Console
                                2. Enable Cloud Firestore
                                3. Deploy security rules
                                4. Register a new account
                                5. Create a circle to test
                                
                                See FIREBASE_SETUP.md for details
                            """.trimIndent(),
                            fontSize = 14.sp
                        )
                    }
                }

                // Back button
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Back to App")
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    isEnabled: Boolean,
    details: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
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
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = details,
                    fontSize = 14.sp,
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            Icon(
                if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (isEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp)
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
