# Design Document

## Overview

This design document outlines the implementation of ride sharing, ETA calculation, online status tracking, and proximity-based push notifications for the OnTheWay app. The solution integrates with the existing Firebase architecture and leverages Firestore for real-time data synchronization and Firebase Cloud Messaging for push notifications.

## Architecture

### High-Level Architecture

```
┌─────────────────┐         ┌──────────────────┐         ┌─────────────────┐
│  Android App    │◄────────┤   Firestore      │────────►│  FCM Server     │
│  (Kotlin)       │         │   (Real-time DB) │         │  (Push Notifs)  │
└────────┬────────┘         └──────────────────┘         └─────────────────┘
         │                           │
         │                           │
         ▼                           ▼
┌─────────────────┐         ┌──────────────────┐
│  Location       │         │  Cloud Functions │
│  Service        │         │  (Optional)      │
└─────────────────┘         └──────────────────┘
```

### Component Interaction Flow

1. **Ride Sharing Initiation**: User taps "Share Ride" → App creates Trip document in Firestore
2. **Location Updates**: LocationService updates location every 10 seconds → Firestore syncs to all recipients
3. **ETA Calculation**: App calculates ETA every 30 seconds using Haversine formula
4. **Proximity Detection**: When ETA ≤ 2 min or distance ≤ 100m → Trigger notification
5. **Push Notification**: Create notification document → FCM sends push to recipient device

## Components and Interfaces

### 1. TripService (New Component)

**Purpose**: Manage ride sharing lifecycle including creation, updates, and termination.

**Key Methods**:
```kotlin
class TripService {
    // Create a new ride share
    suspend fun createTrip(
        circleId: String,
        destinationLat: Double,
        destinationLng: Double,
        destinationName: String,
        sharedWith: List<String>
    ): Trip
    
    // Get active trips for a circle
    suspend fun getActiveTrips(circleId: String): List<Trip>
    
    // Get active trips shared with current user
    suspend fun getTripsSharedWithMe(): List<Trip>
    
    // Stop an active trip
    suspend fun stopTrip(tripId: String): Boolean
    
    // Update trip location (called by LocationService)
    suspend fun updateTripLocation(
        tripId: String,
        latitude: Double,
        longitude: Double,
        speed: Float
    )
    
    // Mark trip as completed
    suspend fun completeTrip(tripId: String)
}
```

**Firestore Integration**:
- Collection: `trips/{tripId}`
- Real-time listeners for active trips
- Automatic cleanup on completion

### 2. OnlineStatusManager (New Component)

**Purpose**: Track and display online/offline status for circle members.

**Key Methods**:
```kotlin
class OnlineStatusManager {
    // Update current user's online status
    suspend fun setOnlineStatus(isOnline: Boolean)
    
    // Listen to online status changes for circle members
    fun observeOnlineStatus(
        circleId: String,
        onStatusChange: (userId: String, isOnline: Boolean) -> Unit
    )
    
    // Get online status for a specific user
    suspend fun getUserOnlineStatus(userId: String): Boolean
    
    // Start heartbeat to maintain online status
    fun startHeartbeat()
    
    // Stop heartbeat
    fun stopHeartbeat()
}
```

**Implementation Strategy**:
- Use Firestore presence system with `lastSeen` timestamp
- Heartbeat every 30 seconds to update `lastSeen`
- Consider user online if `lastSeen` < 60 seconds ago
- Update status on app lifecycle changes (foreground/background)

### 3. ETACalculator (New Component)

**Purpose**: Calculate estimated time of arrival using distance and speed.

**Key Methods**:
```kotlin
class ETACalculator {
    // Calculate distance using Haversine formula
    fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double
    
    // Calculate ETA in minutes
    fun calculateETA(
        distanceMeters: Double,
        speedMps: Float
    ): Int
    
    // Calculate ETA for a trip
    suspend fun calculateTripETA(
        currentLat: Double,
        currentLng: Double,
        destinationLat: Double,
        destinationLng: Double,
        speed: Float
    ): Int
    
    // Format ETA for display
    fun formatETA(minutes: Int): String
}
```

**Algorithm**:
- Haversine formula for great-circle distance
- ETA = distance / speed (if speed > 0)
- Default speed: 13.89 m/s (50 km/h) if speed unavailable
- Update every 30 seconds

### 4. ProximityNotificationManager (New Component)

**Purpose**: Monitor trips and trigger notifications based on proximity thresholds.

**Key Methods**:
```kotlin
class ProximityNotificationManager {
    // Start monitoring a trip for proximity
    fun startMonitoring(tripId: String)
    
    // Stop monitoring a trip
    fun stopMonitoring(tripId: String)
    
    // Check proximity and send notifications
    suspend fun checkProximity(
        tripId: String,
        currentLat: Double,
        currentLng: Double,
        speed: Float
    )
    
    // Send 2-minute notification
    private suspend fun send2MinuteNotification(
        trip: Trip,
        eta: Int
    )
    
    // Send arrival notification
    private suspend fun sendArrivalNotification(trip: Trip)
}
```

**Notification Triggers**:
- **2-Minute Alert**: ETA ≤ 2 min AND distance > 100m
- **Arrival Alert**: Distance ≤ 100m
- Each notification sent only once per trip

### 5. Enhanced LocationService

**Purpose**: Extend existing LocationService to support trip tracking.

**New Methods**:
```kotlin
// Existing LocationService with additions:
class LocationService {
    // ... existing methods ...
    
    // Start tracking for a specific trip
    fun startTripTracking(tripId: String)
    
    // Stop tracking for a specific trip
    fun stopTripTracking(tripId: String)
    
    // Update location for active trips
    private suspend fun updateActiveTrips(location: Location)
}
```

**Integration Points**:
- Call `TripService.updateTripLocation()` on each location update
- Call `ProximityNotificationManager.checkProximity()` every 30 seconds
- Update online status via `OnlineStatusManager`

### 6. Enhanced CircleService

**Purpose**: Add trip-related queries to existing CircleService.

**New Methods**:
```kotlin
// Existing CircleService with additions:
class CircleService {
    // ... existing methods ...
    
    // Get circle members with trip info
    suspend fun getCircleMembersWithTrips(circleId: String): List<CircleMember>
    
    // Check if user is online
    suspend fun isUserOnline(userId: String): Boolean
}
```

## Data Models

### Enhanced Trip Model

```kotlin
data class Trip(
    val tripId: String = "",
    val userId: String = "",
    val userName: String = "",
    val circleId: String = "",
    val startLat: Double = 0.0,
    val startLng: Double = 0.0,
    val destinationLat: Double = 0.0,
    val destinationLng: Double = 0.0,
    val destinationName: String = "",
    val startTime: Long = 0L,
    val endTime: Long? = null,
    val isActive: Boolean = true,
    val sharedWith: List<String> = emptyList(),
    val currentLat: Double = 0.0,
    val currentLng: Double = 0.0,
    val currentSpeed: Float = 0f,
    val lastUpdated: Long = 0L,
    val notified2Min: Boolean = false,
    val notifiedArrival: Boolean = false,
    val eta: Int? = null
)
```

### Enhanced CircleMember Model

```kotlin
data class CircleMember(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastUpdated: Long = 0L,
    val isActive: Boolean = false,
    val isOnline: Boolean = false,  // NEW
    val isSharingTrip: Boolean = false,  // NEW
    val activeTrip: Trip? = null,  // NEW
    val eta: Int? = null  // NEW
)
```

### OnlineStatus Model

```kotlin
data class OnlineStatus(
    val userId: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0L,
    val connectionType: String = "" // "wifi", "cellular", "offline"
)
```

### NotificationPayload Model

```kotlin
data class NotificationPayload(
    val notificationId: String = "",
    val recipientUserId: String = "",
    val fcmToken: String = "",
    val type: String = "", // "2_minutes", "arrived", "trip_started"
    val fromUserId: String = "",
    val fromUserName: String = "",
    val tripId: String = "",
    val eta: Int? = null,
    val timestamp: Long = 0L,
    val sent: Boolean = false
)
```

## User Interface Changes

### 1. CircleDetailScreen Enhancements

**New UI Elements**:
- Online status indicator (green/gray dot) next to each member name
- "Share Ride" button in top bar
- Active trip banner showing ongoing rides
- ETA display next to member names when sharing trip

**Layout**:
```
┌─────────────────────────────────────┐
│  Family Circle              [Share] │
├─────────────────────────────────────┤
│  🟢 John (You)                      │
│  📍 Sharing ride to Home - 5 min    │
├─────────────────────────────────────┤
│  🟢 Mom                             │
│  📍 2 min away                      │
├─────────────────────────────────────┤
│  ⚫ Dad                             │
│  Last seen 10 min ago               │
└─────────────────────────────────────┘
```

### 2. Share Ride Dialog (New)

**Purpose**: Allow user to select destination and recipients.

**UI Elements**:
- Destination input (with map picker or address search)
- Recipient selection (individual members or entire circle)
- "Start Sharing" button
- "Cancel" button

**Flow**:
1. User taps "Share Ride"
2. Dialog appears with destination picker
3. User selects destination on map or enters address
4. User selects recipients (default: entire circle)
5. User taps "Start Sharing"
6. Trip created and tracking begins

### 3. Active Trip Card (New Component)

**Purpose**: Display active trip information on map and list views.

**UI Elements**:
- User avatar/name
- Destination name
- ETA display
- Distance display
- "Stop Sharing" button (for trip owner)
- Progress indicator

### 4. Map Markers Enhancement

**Existing**: Static markers for circle members
**New**: 
- Animated markers for members sharing trips
- ETA label below marker
- Different marker color for active trips (e.g., blue instead of red)
- Destination marker for trip endpoint

## Error Handling

### Network Errors

**Scenario**: User loses internet connection during trip
**Handling**:
- Cache last known location locally
- Queue location updates for when connection restored
- Show "Offline" status to other users
- Display warning banner to trip sharer
- Resume updates when connection restored

### Location Permission Errors

**Scenario**: User revokes location permission during trip
**Handling**:
- Stop location updates immediately
- Mark trip as inactive
- Notify recipients that trip sharing stopped
- Show permission request dialog

### Firestore Write Failures

**Scenario**: Firestore write operation fails
**Handling**:
- Retry up to 3 times with exponential backoff
- Log error for debugging
- Show error message to user if all retries fail
- Don't block UI operations

### FCM Token Errors

**Scenario**: FCM token is invalid or expired
**Handling**:
- Refresh FCM token automatically
- Update token in Firestore
- Retry notification send with new token
- Fall back to in-app notification if FCM fails

### ETA Calculation Errors

**Scenario**: Speed data unavailable or GPS accuracy poor
**Handling**:
- Use default speed (50 km/h) for ETA calculation
- Show "Estimated" label to indicate uncertainty
- Don't send notifications if accuracy < 50m
- Log warning for debugging

## Testing Strategy

### Unit Tests

**TripService**:
- Test trip creation with valid data
- Test trip creation with invalid data (missing fields)
- Test getting active trips for circle
- Test stopping trip
- Test completing trip

**ETACalculator**:
- Test Haversine distance calculation with known coordinates
- Test ETA calculation with various speeds
- Test ETA calculation with zero speed (default)
- Test ETA formatting (minutes, "Arriving now")

**OnlineStatusManager**:
- Test online status update
- Test offline detection after timeout
- Test heartbeat mechanism
- Test status observer

**ProximityNotificationManager**:
- Test 2-minute notification trigger
- Test arrival notification trigger
- Test notification deduplication (only once per trip)
- Test notification not sent if already notified

### Integration Tests

**Ride Sharing Flow**:
1. User A creates trip
2. Verify trip document created in Firestore
3. User B sees trip in circle
4. Verify User B receives location updates
5. User A stops trip
6. Verify trip marked inactive

**ETA and Notifications**:
1. User A shares trip with User B
2. Simulate location updates approaching destination
3. Verify ETA decreases correctly
4. Verify 2-minute notification sent at correct time
5. Verify arrival notification sent when within 100m

**Online Status**:
1. User A opens app
2. Verify online status set to true
3. User A closes app
4. Wait 60 seconds
5. Verify online status set to false

### Manual Testing Checklist

- [ ] Create ride share to individual member
- [ ] Create ride share to entire circle
- [ ] View active trips on map
- [ ] View ETA updates in real-time
- [ ] Receive 2-minute notification
- [ ] Receive arrival notification
- [ ] Stop ride share manually
- [ ] View online/offline status indicators
- [ ] Test with multiple simultaneous trips
- [ ] Test with poor GPS signal
- [ ] Test with no internet connection
- [ ] Test notification when app in background
- [ ] Test notification when app is closed

## Performance Considerations

### Location Update Frequency

**Current**: 10 seconds
**Optimization**: 
- Reduce to 30 seconds when speed < 1 m/s (stationary)
- Keep at 10 seconds when moving
- Reduce to 5 seconds when ETA < 5 minutes (approaching)

### Firestore Read/Write Optimization

**Reads**:
- Use real-time listeners instead of polling
- Limit queries to active trips only
- Cache circle member data locally

**Writes**:
- Batch location updates when possible
- Only update changed fields
- Use Firestore offline persistence

### Memory Management

**Concerns**:
- Multiple active trip listeners
- Location history accumulation
- Notification queue buildup

**Solutions**:
- Remove listeners when trips complete
- Limit location history to last 24 hours
- Clean up old notification documents
- Use pagination for large circles

### Battery Optimization

**Strategies**:
- Use PRIORITY_BALANCED_POWER_ACCURACY when battery < 20%
- Reduce update frequency when stationary
- Stop location updates when no active trips
- Use geofencing instead of continuous polling for arrival detection

## Security Considerations

### Data Access Control

**Firestore Rules**:
```javascript
// Only trip owner can create/update/delete their trips
match /trips/{tripId} {
  allow read: if request.auth != null;
  allow create: if request.auth != null && 
                   request.resource.data.userId == request.auth.uid;
  allow update, delete: if request.auth != null && 
                           resource.data.userId == request.auth.uid;
}

// Only user can update their own online status
match /users/{userId}/status/online {
  allow read: if request.auth != null;
  allow write: if request.auth != null && userId == request.auth.uid;
}
```

### Privacy Protection

- Trip data only visible to circle members
- Location data encrypted in transit (HTTPS)
- FCM tokens stored securely
- No location history stored beyond 24 hours
- User can stop sharing at any time

### Notification Security

- Validate FCM tokens before sending
- Include sender verification in notification payload
- Prevent notification spoofing
- Rate limit notifications (max 10 per minute per user)

## Deployment Plan

### Phase 1: Core Infrastructure (Week 1)
- Implement TripService
- Implement ETACalculator
- Add trip data models
- Update Firestore security rules

### Phase 2: Location Tracking (Week 1-2)
- Enhance LocationService for trip tracking
- Implement ProximityNotificationManager
- Add trip location updates

### Phase 3: Online Status (Week 2)
- Implement OnlineStatusManager
- Add heartbeat mechanism
- Update UI to show online indicators

### Phase 4: UI Implementation (Week 2-3)
- Create Share Ride dialog
- Add active trip cards
- Enhance CircleDetailScreen
- Update map markers

### Phase 5: Notifications (Week 3)
- Implement FCM notification sending
- Add notification handlers
- Test background notifications

### Phase 6: Testing & Polish (Week 3-4)
- Unit tests
- Integration tests
- Manual testing
- Bug fixes
- Performance optimization

## Future Enhancements

- Route display on map (polyline from start to destination)
- Traffic-aware ETA using Google Maps API
- Multiple destination support
- Scheduled trips (share trip at specific time)
- Trip history and analytics
- Custom notification sounds
- Widget for active trips
- Apple Watch support
