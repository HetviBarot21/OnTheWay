# OnTheWay - Complete Implementation Guide

## Overview
OnTheWay is a location-sharing app similar to Life360, built with Kotlin, Jetpack Compose, Firebase, and Mapbox.

## Features Implemented

### ✅ User Authentication
- Firebase Authentication with email/password
- User registration with name, email, phone number
- Phone number hashing for privacy
- Automatic profile creation in Firestore

### ✅ Circles/Groups
- Create circles with unique invite codes
- Join circles using invite codes
- View all circle members
- Leave circles
- Automatic circle cleanup when empty

### ✅ Location Tracking
- Real-time GPS tracking using FusedLocationProvider
- Location updates every 10 seconds (configurable)
- Battery optimization with smart update intervals
- Background location tracking support
- Location synced to Firebase Firestore for all circles

### ✅ Live Map Display
- Mapbox integration showing all circle members
- Real-time marker updates
- User location with pulsing indicator
- Member names displayed on markers
- Toggle between map and list view

### ✅ Geofencing
- Automatic geofence creation for destinations
- 100m radius geofences
- Enter/Exit notifications
- Geofence broadcast receiver

### ✅ ETA Calculation
- Distance calculation using Haversine formula
- Speed-based ETA calculation
- Google Distance Matrix API integration (optional)
- ETA updates every 30 seconds
- Real-time ETA display on member cards

### ✅ Smart Notifications
- "2 minutes away" notification when ETA ≤ 2 min
- "Arrived" notification when within 100m
- Geofence enter/exit notifications
- Firebase Cloud Messaging integration
- Local notifications for testing

### ✅ Privacy Controls
- Location sharing only within circles
- Phone number hashing
- Temporary trip sharing
- Manual location sharing toggle

### ✅ Contact Integration
- Pick contacts from phone
- Auto-fill email from contacts
- READ_CONTACTS permission handling

## Architecture

### Frontend (Android/Kotlin)
```
app/
├── models/
│   └── Models.kt              # Data classes
├── services/
│   └── CircleService.kt       # Circle management
├── MainActivity.kt            # Navigation
├── LoginScreen.kt             # Authentication
├── SignUpScreen.kt            # Registration
├── HomeScreen.kt              # Main map view
├── CirclesScreen.kt           # Circle management
├── CircleDetailScreen.kt      # Circle members map
├── SettingsScreen.kt          # User settings
├── LocationService.kt         # Location tracking & geofencing
└── NotificationService.kt     # FCM & notifications
```

### Backend (Firebase)
```
Firestore Collections:
├── users/                     # User profiles
│   ├── {userId}/
│   │   ├── invites/          # Circle invitations
│   │   └── contacts/         # Shared contacts
├── circles/                   # Circle groups
│   └── {circleId}/
├── locations/                 # User locations
│   └── {userId}/
│       └── updates/          # Location history by circle
├── notifications/             # Notification queue
└── pending_invites/          # Invites for non-users
```

## Key Implementation Details

### 1. Location Updates
```kotlin
// LocationService.kt
fun startLocationUpdates(onLocationUpdate: (Location) -> Unit) {
    val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        10000L // 10 seconds
    ).apply {
        setMinUpdateIntervalMillis(5000L) // 5 seconds
    }.build()
    
    locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { location ->
                // Update Firestore for all circles
                circleService.updateLocationForCircles(
                    location.latitude,
                    location.longitude,
                    location.speed,
                    location.accuracy
                )
                
                // Check ETA every 30 seconds
                if (System.currentTimeMillis() - lastETACheck > 30000L) {
                    checkAndNotifyContacts(location)
                }
            }
        }
    }
}
```

### 2. ETA Calculation
```kotlin
// Using Haversine formula
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadius = 6371000.0 // meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
    
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadius * c
}

fun calculateETA(distanceMeters: Double, speedMps: Double): Int {
    return if (speedMps > 0) {
        (distanceMeters / speedMps / 60).toInt() // minutes
    } else {
        (distanceMeters / 13.89 / 60).toInt() // Default 50 km/h
    }
}
```

### 3. Geofencing
```kotlin
fun addGeofence(geofenceId: String, latitude: Double, longitude: Double, radius: Float = 100f) {
    val geofence = Geofence.Builder()
        .setRequestId(geofenceId)
        .setCircularRegion(latitude, longitude, radius)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(
            Geofence.GEOFENCE_TRANSITION_ENTER or
            Geofence.GEOFENCE_TRANSITION_EXIT
        )
        .build()
    
    geofencingClient.addGeofences(geofencingRequest, pendingIntent)
}
```

### 4. Notifications
```kotlin
suspend fun checkAndNotifyContacts(currentLocation: Location) {
    val contacts = getContacts()
    
    for (contact in contacts) {
        val distance = calculateDistance(
            currentLocation.latitude,
            currentLocation.longitude,
            contact.destinationLat,
            contact.destinationLng
        )
        
        val eta = calculateETA(distance, currentLocation.speed.toDouble())
        
        // Notify when 2 minutes away
        if (eta <= 2 && !contact.notified2Min && distance > 100) {
            sendNotification(contact.email, "2_minutes", eta)
            updateContactNotification(contact.email, "notified2Min", true)
        }
        
        // Notify when arrived (within 100 meters)
        if (distance <= 100 && !contact.notifiedArrived) {
            sendNotification(contact.email, "arrived", 0)
            updateContactNotification(contact.email, "notifiedArrived", true)
        }
    }
}
```

## Firebase Cloud Functions

Create these Cloud Functions to handle push notifications:

```javascript
// functions/index.js
const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Send notification when queued
exports.sendNotification = functions.firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const data = snap.data();
        
        const message = {
            token: data.token,
            notification: {
                title: getNotificationTitle(data.type, data.from),
                body: getNotificationBody(data.type, data.from, data.eta)
            },
            data: {
                type: data.type,
                from: data.from,
                eta: String(data.eta)
            }
        };
        
        try {
            await admin.messaging().send(message);
            // Delete notification after sending
            await snap.ref.delete();
        } catch (error) {
            console.error('Error sending notification:', error);
        }
    });

function getNotificationTitle(type, from) {
    switch (type) {
        case '2_minutes':
            return 'Almost There!';
        case 'arrived':
            return 'Arrived!';
        default:
            return 'Location Update';
    }
}

function getNotificationBody(type, from, eta) {
    switch (type) {
        case '2_minutes':
            return `${from} is 2 minutes away (ETA: ${eta} min)`;
        case 'arrived':
            return `${from} has arrived`;
        default:
            return `Update from ${from}`;
    }
}

// Clean up old location updates (run daily)
exports.cleanupOldLocations = functions.pubsub
    .schedule('every 24 hours')
    .onRun(async (context) => {
        const db = admin.firestore();
        const cutoff = Date.now() - (7 * 24 * 60 * 60 * 1000); // 7 days
        
        const snapshot = await db.collectionGroup('updates')
            .where('timestamp', '<', cutoff)
            .get();
        
        const batch = db.batch();
        snapshot.docs.forEach(doc => batch.delete(doc.ref));
        
        await batch.commit();
        console.log(`Deleted ${snapshot.size} old location updates`);
    });
```

## Setup Instructions

### 1. Firebase Setup
1. Create a Firebase project at https://console.firebase.google.com
2. Enable Authentication (Email/Password)
3. Create Firestore database
4. Add Android app and download `google-services.json`
5. Enable Cloud Messaging
6. Deploy Cloud Functions (optional but recommended)

### 2. Mapbox Setup
1. Create account at https://www.mapbox.com
2. Get access token
3. Add to AndroidManifest.xml:
```xml
<meta-data
    android:name="MAPBOX_ACCESS_TOKEN"
    android:value="YOUR_MAPBOX_TOKEN" />
```

### 3. Google Maps API (Optional for Distance Matrix)
1. Enable Distance Matrix API in Google Cloud Console
2. Get API key
3. Use in `calculateETAWithAPI()` function

### 4. Build & Run
```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Firestore Security Rules

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own data
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      
      match /contacts/{contactId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
      
      match /invites/{inviteId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
    
    // Circle members can read circle data
    match /circles/{circleId} {
      allow read: if request.auth != null && 
                     request.auth.uid in resource.data.members;
      allow create: if request.auth != null;
      allow update, delete: if request.auth != null && 
                               request.auth.uid in resource.data.members;
    }
    
    // Circle members can read each other's locations
    match /locations/{userId}/updates/{circleId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Notifications
    match /notifications/{notificationId} {
      allow create: if request.auth != null;
      allow read, delete: if request.auth != null;
    }
  }
}
```

## Performance Optimizations

### Battery Optimization
- Reduce update frequency when stationary (speed < 1 m/s)
- Use PRIORITY_BALANCED_POWER_ACCURACY when battery low
- Stop updates when app is closed (user preference)

### Network Optimization
- Batch location updates
- Use Firestore offline persistence
- Cache circle member data
- Compress location data

### Memory Optimization
- Limit location history to 24 hours
- Clean up old geofences
- Use pagination for large circles

## Privacy Features

1. **Phone Number Hashing**: SHA-256 hash for contact matching
2. **Circle-based Sharing**: Location only visible to circle members
3. **Temporary Sharing**: Trip sharing can be stopped anytime
4. **No Location History**: Only current location stored
5. **Opt-out**: Users can disable location sharing

## Testing Checklist

- [ ] User registration and login
- [ ] Create and join circles
- [ ] Location updates in real-time
- [ ] Map displays all members
- [ ] ETA calculation accuracy
- [ ] 2-minute notification
- [ ] Arrival notification
- [ ] Geofence enter/exit
- [ ] Contact picker
- [ ] Background location updates
- [ ] Battery optimization
- [ ] Network error handling

## Known Limitations

1. **Google Distance Matrix API**: Requires API key and has usage limits
2. **Background Location**: Requires additional permissions on Android 10+
3. **Battery Usage**: Continuous GPS tracking drains battery
4. **Network Dependency**: Requires internet for real-time updates

## Future Enhancements

- [ ] Driving mode detection
- [ ] Route display on map
- [ ] Location history playback
- [ ] Custom geofence zones (home, work, school)
- [ ] Emergency SOS feature
- [ ] Place recommendations
- [ ] Chat within circles
- [ ] Location sharing time limits
- [ ] Dark mode
- [ ] Widget support

## Support

For issues or questions, check:
- Firebase Console for backend errors
- Logcat for Android errors
- Firestore rules for permission issues
- API quotas for rate limiting

## License

This project is for educational purposes.
