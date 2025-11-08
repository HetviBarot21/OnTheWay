package com.example.ontheway

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ontheway.ui.theme.OnTheWayTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        setContent {
            OnTheWayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    @Composable
    fun AppNavigation() {
        val auth = FirebaseAuth.getInstance()
        val startScreen = if (auth.currentUser != null) "home" else "landing"
        var currentScreen by remember { mutableStateOf(startScreen) }
        var selectedCircleId by remember { mutableStateOf<String?>(null) }

        when (currentScreen) {
            "landing" -> LandingPage(
                onGetStarted = { currentScreen = "login" }
            )
            "login" -> LoginScreen(
                onLoginSuccess = { currentScreen = "home" },
                onNavigateToSignUp = { currentScreen = "signup" }
            )
            "signup" -> SignUpScreen(
                onSignUpSuccess = { currentScreen = "home" },
                onNavigateToLogin = { currentScreen = "login" }
            )
            "home" -> {
                val userEmail = try {
                    FirebaseAuth.getInstance().currentUser?.email ?: "User"
                } catch (e: Exception) {
                    "User"
                }
                HomeScreen(
                    userEmail = userEmail,
                    onLogout = {
                        try {
                            FirebaseAuth.getInstance().signOut()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        currentScreen = "landing"
                    },
                    onNavigateToSettings = { currentScreen = "settings" },
                    onNavigateToCircles = { currentScreen = "circles" }
                )
            }
            "settings" -> {
                val userEmail = try {
                    FirebaseAuth.getInstance().currentUser?.email ?: "User"
                } catch (e: Exception) {
                    "User"
                }
                SettingsScreen(
                    userEmail = userEmail,
                    onBack = { currentScreen = "home" },
                    onLogout = {
                        try {
                            FirebaseAuth.getInstance().signOut()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        currentScreen = "landing"
                    }
                )
            }
            "circles" -> {
                CirclesScreen(
                    onBack = { currentScreen = "home" },
                    onCircleSelected = { circleId ->
                        selectedCircleId = circleId
                        currentScreen = "circle_detail"
                    }
                )
            }
            "circle_detail" -> {
                selectedCircleId?.let { circleId ->
                    CircleDetailScreen(
                        circleId = circleId,
                        onBack = { currentScreen = "circles" }
                    )
                }
            }
        }
    }

    @Composable
    fun LandingPage(onGetStarted: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "OnTheWay",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Your journey starts here",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .width(280.dp)
                        .height(56.dp)
                ) {
                    Text(text = "Get Started", fontSize = 18.sp)
                }
            }
        }
    }
}
