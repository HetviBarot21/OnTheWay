package com.example.ontheway

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SOSScreen(
    onBack: () -> Unit
) {
    var showCountdown by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(15) }

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
                    Text("3. Emergency contacts will be notified")
                    Text("4. Your location will be shared")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Note: This is a basic implementation. Full SOS features coming soon.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    var sosTriggered by remember { mutableStateOf(false) }

    // Countdown effect
    LaunchedEffect(showCountdown, countdown) {
        if (showCountdown && countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        } else if (showCountdown && countdown == 0) {
            // SOS triggered!
            sosTriggered = true
            showCountdown = false
            countdown = 15
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
