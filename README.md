# OnTheWay - Location Sharing App

A real-time location sharing app similar to Life360, built with Kotlin, Jetpack Compose, Firebase, and Mapbox.

## 🚀 Features

### Core Features
- ✅ **User Authentication** - Firebase Auth with email/password, name, phone number
- ✅ **Circles/Groups** - Create and join circles with invite codes
- ✅ **Real-time Location Tracking** - GPS tracking with FusedLocationProvider
- ✅ **Live Map Display** - Mapbox integration showing all circle members
- ✅ **Geofencing** - Automatic notifications for arrivals and departures
- ✅ **ETA Calculation** - Real-time ETA updates every 30 seconds
- ✅ **Smart Notifications** - "2 minutes away" and "arrived" alerts
- ✅ **Contact Integration** - Pick contacts from phone
- ✅ **Privacy Controls** - Circle-based sharing with phone number hashing

### Advanced Features
- 🔋 **Battery Optimization** - Smart update frequency based on movement
- 📱 **Background Tracking** - Continues tracking when app is closed
- 🔔 **Push Notifications** - Firebase Cloud Messaging integration
- 🗺️ **Dual View** - Toggle between map and list view
- 📍 **Geofence Zones** - 100m radius with enter/exit detection
- ⚡ **Real-time Sync** - Firestore for instant updates

## 📱 Screenshots

[Add screenshots here]

## 🏗️ Architecture

### Tech Stack
- **Frontend**: Kotlin, Jetpack Compose, Material 3
- **Backend**: Firebase (Auth, Firestore, Cloud Messaging)
- **Maps**: Mapbox SDK
- **Location**: Google Play Services FusedLocationProvider
- **Notifications**: Firebase Cloud Messaging + Cloud Functions

### Project Structure
```
app/
├── models/                    # Data models
│   └── Models.kt
├── services/                  # Business logic
│   └── CircleService.kt
├── MainActivity.kt            # Navigation
├── LoginScreen.kt             # Authentication
├── SignUpScreen.kt            # Registration
├── HomeScreen.kt              # Main map view
├── CirclesScreen.kt           # Circle management
├── CircleDetailScreen.kt      # Circle members map
├── SettingsScreen.kt          # User settings
├── LocationService.kt         # Location & geofencing
└── NotificationService.kt     # FCM & notifications

firebase-functions/
└── index.js                   # Cloud Functions
```

## 🚦 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK 24+
- Firebase account
- Mapbox account
- Google Cloud account (optional, for Distance Matrix API)

### Setup

1. **Clone the repository**
```bash
git clone https://github.com/yourusername/ontheway.git
cd ontheway
```

2. **Firebase Setup**
   - Create a Firebase project at https://console.firebase.google.com
   - Enable Authentication (Email/Password)
   - Create Firestore database
   - Add Android app and download `google-services.json` to `app/`
   - Enable Cloud Messaging

3. **Mapbox Setup**
   - Create account at https://www.mapbox.com
   - Get access token
   - Update `AndroidManifest.xml` with your token:
   ```xml
   <meta-data
       android:name="MAPBOX_ACCESS_TOKEN"
       android:value="YOUR_MAPBOX_TOKEN" />
   ```

4. **Build and Run**
```bash
./gradlew assembleDebug
./gradlew installDebug
```

5. **Deploy Cloud Functions** (Optional but recommended)
```bash
cd firebase-functions
npm install
firebase deploy --only functions
```

## 📖 Usage

### Creating a Circle
1. Tap the Circles icon in the top bar
2. Tap the + button
3. Enter a circle name
4. Share the invite code with others

### Joining a Circle
1. Tap the Circles icon
2. Tap the join button
3. Enter the invite code

### Sharing Your Location
1. Grant location permission when prompted
2. Your location will automatically be shared with all your circles
3. View circle members on the map

### Getting Notifications
1. When someone is 2 minutes away, you'll get a notification
2. When someone arrives (within 100m), you'll get an arrival notification
3. Geofence notifications for entering/leaving locations

## 🔒 Privacy & Security

- **Phone Number Hashing**: SHA-256 hashing for privacy
- **Circle-based Sharing**: Location only visible to circle members
- **No Location History**: Only current location is stored
- **Firestore Security Rules**: Strict access control
- **Opt-out**: Users can disable location sharing anytime

## 🔧 Configuration

### Location Update Frequency
Edit `LocationService.kt`:
```kotlin
val locationRequest = LocationRequest.Builder(
    Priority.PRIORITY_HIGH_ACCURACY,
    10000L // Update every 10 seconds
).apply {
    setMinUpdateIntervalMillis(5000L) // Minimum 5 seconds
}.build()
```

### ETA Check Interval
```kotlin
private val ETA_CHECK_INTERVAL = 30000L // 30 seconds
```

### Geofence Radius
```kotlin
fun addGeofence(..., radius: Float = 100f) // 100 meters
```

## 📊 Firestore Structure

```
users/
  {userId}/
    - name, email, phoneNumber, phoneHash, fcmToken
    contacts/
      {contactEmail}/
        - destinationLat, destinationLng, notified2Min, notifiedArrived
    invites/
      {inviteId}/
        - circleId, circleName, invitedBy, status

circles/
  {circleId}/
    - name, createdBy, inviteCode, members[]

locations/
  {userId}/
    updates/
      {circleId}/
        - latitude, longitude, timestamp, speed, accuracy

notifications/
  {notificationId}/
    - token, from, type, eta, timestamp
```

## 🔥 Firebase Cloud Functions

### Deployed Functions
- `sendNotification` - Sends push notifications
- `cleanupOldLocations` - Removes old location data (runs daily)
- `cleanupFailedNotifications` - Removes failed notifications (runs hourly)
- `updateUserActivity` - Updates last active timestamp
- `onCircleJoin` - Notifies members when someone joins
- `updateETAs` - Calculates ETAs for active trips (runs every minute)

## 🧪 Testing

### Test Checklist
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

### Testing Tips
1. Use Android Emulator with mock locations
2. Test with multiple devices/emulators
3. Check Firebase Console for backend errors
4. Monitor Logcat for Android errors
5. Test battery optimization features

## 🐛 Troubleshooting

### Location not updating
- Check location permissions are granted
- Verify GPS is enabled
- Check Firestore security rules
- Ensure internet connection

### Notifications not working
- Verify FCM token is saved
- Check Cloud Functions are deployed
- Verify notification permissions
- Check Firebase Console logs

### Map not loading
- Verify Mapbox token is correct
- Check internet connection
- Ensure location permission is granted

## 📈 Performance Optimization

### Battery
- Reduce update frequency when stationary
- Use PRIORITY_BALANCED_POWER_ACCURACY when battery low
- Stop updates when app is closed (optional)

### Network
- Batch location updates
- Use Firestore offline persistence
- Cache circle member data

### Memory
- Limit location history to 24 hours
- Clean up old geofences
- Use pagination for large circles

## 🚀 Future Enhancements

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
- [ ] iOS version

## 📄 License

This project is for educational purposes.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📞 Support

For issues or questions:
- Check the [Implementation Guide](IMPLEMENTATION_GUIDE.md)
- Review Firebase Console for backend errors
- Check Logcat for Android errors
- Open an issue on GitHub

## 👏 Acknowledgments

- Firebase for backend infrastructure
- Mapbox for mapping services
- Google Play Services for location tracking
- Jetpack Compose for modern UI

---

Made with ❤️ using Kotlin and Jetpack Compose
