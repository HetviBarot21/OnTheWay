package com.example.ontheway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await

class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        remoteMessage.notification?.let {
            showNotification(
                title = it.title ?: "OnTheWay",
                message = it.body ?: "",
                isHighPriority = false
            )
        }
        
        remoteMessage.data.let { data ->
            val type = data["type"]
            val from = data["from"]
            val eta = data["eta"]
            val latitude = data["latitude"]
            val longitude = data["longitude"]
            
            when (type) {
                "sos" -> {
                    // High priority SOS notification
                    val mapsLink = if (latitude != null && longitude != null) {
                        "https://maps.google.com/?q=$latitude,$longitude"
                    } else null
                    
                    showNotification(
                        title = "🚨 EMERGENCY SOS",
                        message = "$from needs help! ${if (mapsLink != null) "Tap to see location." else ""}",
                        isHighPriority = true,
                        actionUrl = mapsLink
                    )
                }
                "2_minutes" -> {
                    showNotification(
                        title = "Almost There!",
                        message = "$from is 2 minutes away (ETA: $eta min)",
                        isHighPriority = false
                    )
                }
                "arrived" -> {
                    showNotification(
                        title = "Arrived!",
                        message = "$from has arrived at the destination",
                        isHighPriority = false
                    )
                }
            }
        }
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token to Firestore for this user
        saveTokenToFirestore(token)
    }
    
    private fun saveTokenToFirestore(token: String) {
        // This will be called from LocationService
    }
    
    private fun showNotification(
        title: String, 
        message: String, 
        isHighPriority: Boolean = false,
        actionUrl: String? = null
    ) {
        val channelId = if (isHighPriority) "ontheway_sos" else "ontheway_notifications"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                if (isHighPriority) "Emergency SOS" else "OnTheWay Notifications",
                if (isHighPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = if (isHighPriority) 
                    "Emergency SOS alerts from circle members" 
                else 
                    "Notifications for location sharing updates"
                if (isHighPriority) {
                    enableVibration(true)
                    enableLights(true)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = if (actionUrl != null) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(actionUrl))
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply {
                if (isHighPriority) {
                    setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
                    setCategory(NotificationCompat.CATEGORY_ALARM)
                }
            }
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

object NotificationHelper {
    
    suspend fun getFCMToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "ontheway_notifications"
            val channelName = "OnTheWay Notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = "Notifications for location sharing updates"
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    fun showLocalNotification(context: Context, title: String, message: String) {
        val channelId = "ontheway_notifications"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
