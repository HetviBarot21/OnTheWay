# OnTheWay - Firestore Database Structure

## Overview
This document describes the complete Firestore database structure for the OnTheWay app.

---

## Collections

### 1. `users` Collection
**Purpose:** Store user profile information

**Document ID:** `{userId}` (Firebase Auth UID)

**Fields:**
```javascript
{
  userId: string,           // Firebase Auth UID
  name: string,             // User's display name
  email: string,            // User's email address
  phoneNumber: string,      // User's phone number (e.g., "+1234567890")
  phoneHash: string,        // SHA-256 hash of phone number (for privacy)
  fcmToken: string,         // Firebase Cloud Messaging token (for notifications)
  createdAt: number,        // Timestamp (milliseconds)
  updatedAt: number,        // Timestamp (milliseconds)
  profileImageUrl: string   // URL to profile image (optional)
}
```

**Subcollections:**
- `invites/{inviteId}` - Pending circle invitations for this user

**Example:**
```javascript
users/abc123xyz {
  userId: "abc123xyz",
  name: "John Doe",
  email: "john@example.com",
  phoneNumber: "+1234567890",
  phoneHash: "a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3",
  fcmToken: "fcm_token_here",
  createdAt: 1699564800000,
  updatedAt: 1699564800000,
  profileImageUrl: ""
}
```

---

### 2. `circles` Collection
**Purpose:** Store circle (group) information with invite codes

**Document ID:** `{circleId}` (UUID)

**Fields:**
```javascript
{
  circleId: string,         // Unique circle ID (UUID)
  name: string,             // Circle name (e.g., "Family", "Friends")
  createdBy: string,        // userId of creator
  createdAt: number,        // Timestamp (milliseconds)
  inviteCode: string,       // 6-character invite code (e.g., "ABC123")
  members: string[]         // Array of userIds in this circle
}
```

**Example:**
```javascript
circles/circle-uuid-123 {
  circleId: "circle-uuid-123",
  name: "Family",
  createdBy: "abc123xyz",
  createdAt: 1699564800000,
  inviteCode: "FAM123",
  members: ["abc123xyz", "def456uvw", "ghi789rst"]
}
```

**How Invite Codes Work:**
- Automatically generated 6-character alphanumeric code
- Used to join circles without knowing member phone numbers
- Unique per circle
- Example codes: "ABC123", "XYZ789", "FAM001"

---

### 3. `circle_members` Collection
**Purpose:** Track circle membership (for quick lookups)

**Document ID:** `{circleId}_{userId}`

**Fields:**
```javascript
{
  circleId: string,         // Circle ID
  userId: string,           // User ID
  joinedAt: number          // Timestamp when user joined
}
```

**Example:**
```javascript
circle_members/circle-uuid-123_abc123xyz {
  circleId: "circle-uuid-123",
  userId: "abc123xyz",
  joinedAt: 1699564800000
}
```

---

### 4. `locations` Collection
**Purpose:** Store real-time location updates for users

**Document ID:** `{userId}`

**Subcollections:**
- `updates/{circleId}` - Location updates per circle

**Subcollection Fields:**
```javascript
{
  userId: string,           // User ID
  circleId: string,         // Circle ID this location is shared with
  latitude: number,         // Latitude coordinate
  longitude: number,        // Longitude coordinate
  timestamp: number,        // Timestamp (milliseconds)
  speed: number,            // Speed in m/s
  accuracy: number          // Accuracy in meters
}
```

**Example:**
```javascript
locations/abc123xyz/updates/circle-uuid-123 {
  userId: "abc123xyz",
  circleId: "circle-uuid-123",
  latitude: 37.7749,
  longitude: -122.4194,
  timestamp: 1699564800000,
  speed: 5.5,
  accuracy: 10.0
}
```

---

### 5. `pending_invites` Collection
**Purpose:** Store invites for users who haven't registered yet

**Document ID:** `{phoneHash}` (SHA-256 hash of phone number)

**Subcollections:**
- `invites/{inviteId}` - Pending invitations

**Subcollection Fields:**
```javascript
{
  inviteId: string,         // Unique invite ID
  circleId: string,         // Circle ID
  circleName: string,       // Circle name
  invitedBy: string,        // userId of inviter
  invitedByName: string,    // Name of inviter
  inviteCode: string,       // Circle invite code
  createdAt: number,        // Timestamp (milliseconds)
  expiresAt: number,        // Expiration timestamp (7 days)
  status: string            // "pending", "accepted", "declined"
}
```

**Example:**
```javascript
pending_invites/phone_hash_123/invites/invite-uuid-456 {
  inviteId: "invite-uuid-456",
  circleId: "circle-uuid-123",
  circleName: "Family",
  invitedBy: "abc123xyz",
  invitedByName: "John Doe",
  inviteCode: "FAM123",
  createdAt: 1699564800000,
  expiresAt: 1700169600000,
  status: "pending"
}
```

---

### 6. `trips` Collection
**Purpose:** Track active trips with ETA information

**Document ID:** `{tripId}` (UUID)

**Fields:**
```javascript
{
  tripId: string,           // Unique trip ID
  userId: string,           // User taking the trip
  circleId: string,         // Circle this trip is shared with
  startLat: number,         // Starting latitude
  startLng: number,         // Starting longitude
  destinationLat: number,   // Destination latitude
  destinationLng: number,   // Destination longitude
  destinationName: string,  // Destination name (e.g., "Home")
  startTime: number,        // Trip start timestamp
  endTime: number | null,   // Trip end timestamp (null if active)
  isActive: boolean,        // Whether trip is currently active
  sharedWith: string[]      // Array of userIds who can see this trip
}
```

**Example:**
```javascript
trips/trip-uuid-789 {
  tripId: "trip-uuid-789",
  userId: "abc123xyz",
  circleId: "circle-uuid-123",
  startLat: 37.7749,
  startLng: -122.4194,
  destinationLat: 37.8044,
  destinationLng: -122.2712,
  destinationName: "Home",
  startTime: 1699564800000,
  endTime: null,
  isActive: true,
  sharedWith: ["def456uvw", "ghi789rst"]
}
```

---

### 7. `geofences` Collection
**Purpose:** Store location-based notification zones

**Document ID:** `{geofenceId}` (UUID)

**Fields:**
```javascript
{
  geofenceId: string,       // Unique geofence ID
  userId: string,           // User who created this geofence
  name: string,             // Geofence name (e.g., "Home", "Work")
  latitude: number,         // Center latitude
  longitude: number,        // Center longitude
  radius: number,           // Radius in meters (default: 100)
  type: string,             // "home", "work", "school", "custom"
  createdAt: number         // Timestamp (milliseconds)
}
```

**Example:**
```javascript
geofences/geofence-uuid-321 {
  geofenceId: "geofence-uuid-321",
  userId: "abc123xyz",
  name: "Home",
  latitude: 37.7749,
  longitude: -122.4194,
  radius: 100,
  type: "home",
  createdAt: 1699564800000
}
```

---

## Common Queries

### Get User's Circles
```kotlin
firestore.collection("circles")
    .whereArrayContains("members", userId)
    .get()
```

### Get Circle Members
```kotlin
// First get circle
val circle = firestore.collection("circles")
    .document(circleId)
    .get()

// Then get each member's profile
for (memberId in circle.members) {
    val user = firestore.collection("users")
        .document(memberId)
        .get()
}
```

### Get Latest Location for User in Circle
```kotlin
firestore.collection("locations")
    .document(userId)
    .collection("updates")
    .document(circleId)
    .get()
```

### Join Circle with Invite Code
```kotlin
// Find circle by invite code
val circle = firestore.collection("circles")
    .whereEqualTo("inviteCode", inviteCode)
    .limit(1)
    .get()

// Add user to members array
firestore.collection("circles")
    .document(circleId)
    .update("members", FieldValue.arrayUnion(userId))
```

---

## Security Rules Summary

- ✅ All operations require authentication
- ✅ Users can only read/write their own profile
- ✅ Circle members can read circle data
- ✅ Only circle creator can delete circle
- ✅ Users can only write their own location
- ✅ Phone numbers are hashed for privacy
- ✅ Circle members can see each other's locations

---

## Data Flow Examples

### User Registration Flow
1. User signs up with email/password → Firebase Auth creates user
2. App creates user profile → `users/{userId}` document
3. App checks for pending invites → `pending_invites/{phoneHash}/invites`
4. If invites exist, move to user's invites → `users/{userId}/invites`

### Create Circle Flow
1. User creates circle → `circles/{circleId}` document
2. Generate 6-char invite code → stored in circle
3. Add creator to members array
4. Create membership record → `circle_members/{circleId}_{userId}`

### Join Circle Flow
1. User enters invite code
2. Query circles by invite code
3. Add user to members array
4. Create membership record
5. User can now see circle and members

### Location Sharing Flow
1. App gets user's location (GPS)
2. For each circle user is in:
   - Update `locations/{userId}/updates/{circleId}`
3. Other circle members query this location
4. Display on map with member's name

---

## Indexes Required

These indexes are defined in `firestore.indexes.json`:

1. **Circle membership query:**
   - Collection: `circles`
   - Fields: `members` (array-contains), `createdAt` (descending)

2. **Location updates query:**
   - Collection: `updates` (collection group)
   - Fields: `circleId` (ascending), `timestamp` (descending)

Deploy with: `firebase deploy --only firestore:indexes`
