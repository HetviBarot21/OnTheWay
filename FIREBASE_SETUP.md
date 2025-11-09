# Firebase Setup Guide for OnTheWay

## Step 1: Enable Firebase Authentication

1. Go to [Firebase Console](https://console.firebase.google.com/project/ontheway-vinit)
2. Click **Authentication** in the left menu
3. Click **Get Started**
4. Enable **Email/Password** sign-in method:
   - Click on **Email/Password**
   - Toggle **Enable**
   - Click **Save**

## Step 2: Enable Cloud Firestore

1. In Firebase Console, click **Firestore Database**
2. Click **Create database**
3. Choose **Start in test mode** (for development)
4. Select a location (e.g., `us-central1`, `europe-west1`, `asia-southeast1`)
   - **Important:** This cannot be changed later!
5. Click **Enable**
6. Wait 2-3 minutes for provisioning

## Step 3: Deploy Firestore Security Rules

After Firestore is enabled, deploy the security rules:

```bash
# Install Firebase CLI if you haven't
npm install -g firebase-tools

# Login to Firebase
firebase login

# Initialize Firebase in your project
firebase init firestore

# Deploy the rules
firebase deploy --only firestore:rules

# Deploy the indexes
firebase deploy --only firestore:indexes
```

## Step 4: Verify Setup

Your app will automatically create these Firestore collections:

### Collections Structure:

#### 1. **users** (User Profiles)
```
users/{userId}
  - userId: string
  - name: string
  - email: string
  - phoneNumber: string
  - phoneHash: string (SHA-256 hashed)
  - fcmToken: string
  - createdAt: timestamp
  - updatedAt: timestamp
  - profileImageUrl: string
  
  Subcollection: invites/{inviteId}
    - inviteId: string
    - circleId: string
    - circleName: string
    - invitedBy: string
    - invitedByName: string
    - inviteCode: string
    - createdAt: timestamp
    - expiresAt: timestamp
    - status: string (pending/accepted/declined)
```

#### 2. **circles** (Friend/Family Circles)
```
circles/{circleId}
  - circleId: string (UUID)
  - name: string
  - createdBy: string (userId)
  - createdAt: timestamp
  - inviteCode: string (6-char code like "ABC123")
  - members: array of userIds
```

#### 3. **circle_members** (Membership Tracking)
```
circle_members/{circleId}_{userId}
  - circleId: string
  - userId: string
  - joinedAt: timestamp
```

#### 4. **locations** (Real-time Location Data)
```
locations/{userId}
  
  Subcollection: updates/{circleId}
    - userId: string
    - circleId: string
    - latitude: number
    - longitude: number
    - timestamp: timestamp
    - speed: number
    - accuracy: number
```

#### 5. **pending_invites** (For Unregistered Users)
```
pending_invites/{phoneHash}
  
  Subcollection: invites/{inviteId}
    - inviteId: string
    - circleId: string
    - circleName: string
    - invitedBy: string
    - invitedByName: string
    - inviteCode: string
    - createdAt: timestamp
    - expiresAt: timestamp
```

#### 6. **trips** (Active Trip Tracking)
```
trips/{tripId}
  - tripId: string
  - userId: string
  - circleId: string
  - startLat: number
  - startLng: number
  - destinationLat: number
  - destinationLng: number
  - destinationName: string
  - startTime: timestamp
  - endTime: timestamp (nullable)
  - isActive: boolean
  - sharedWith: array of userIds
```

#### 7. **geofences** (Location-based Notifications)
```
geofences/{geofenceId}
  - geofenceId: string
  - userId: string
  - name: string
  - latitude: number
  - longitude: number
  - radius: number (meters)
  - type: string (home/work/school/custom)
  - createdAt: timestamp
```

## Step 5: Test Your Setup

### Test Authentication:
1. Run your app
2. Click "Get Started"
3. Click "Sign Up"
4. Enter:
   - Name: Test User
   - Phone: +1234567890
   - Email: test@example.com
   - Password: test123
5. Click "Sign Up"
6. Check Firebase Console > Authentication to see the new user

### Test Firestore:
1. After signing up, go to "Circles"
2. Click "Create Circle"
3. Enter a circle name (e.g., "Family")
4. Check Firebase Console > Firestore Database
5. You should see:
   - `users/{userId}` document created
   - `circles/{circleId}` document created
   - Circle has a 6-character invite code

### Test Circle Invites:
1. In the circle, note the invite code (e.g., "ABC123")
2. Sign out and create another account
3. Click "Join Circle"
4. Enter the invite code
5. Both users should now see each other in the circle

## Security Features

✅ **Authentication Required**: All database operations require user login
✅ **User Privacy**: Phone numbers are hashed (SHA-256)
✅ **Circle Privacy**: Only circle members can see circle data
✅ **Location Privacy**: Only circle members can see locations
✅ **Ownership**: Users can only modify their own data
✅ **Creator Rights**: Only circle creators can delete circles

## Troubleshooting

### "Permission Denied" Errors
- Make sure Firestore is enabled
- Deploy security rules: `firebase deploy --only firestore:rules`
- Check that user is logged in

### "Index Required" Errors
- Deploy indexes: `firebase deploy --only firestore:indexes`
- Or click the link in the error to create index in console

### Authentication Errors
- Make sure Email/Password is enabled in Firebase Console
- Check internet connection
- Verify google-services.json is up to date

## Next Steps

1. ✅ Enable Firebase Authentication (Email/Password)
2. ✅ Enable Cloud Firestore
3. ✅ Deploy security rules
4. ✅ Deploy indexes
5. ✅ Test user registration
6. ✅ Test circle creation
7. ✅ Test circle invites
8. 🔄 Enable Firebase Cloud Messaging (for notifications)
9. 🔄 Set up location tracking service
10. 🔄 Configure Mapbox for map display

## Support

If you encounter any issues:
1. Check Firebase Console for error messages
2. Check Android Logcat for detailed errors
3. Verify all Firebase services are enabled
4. Ensure security rules are deployed
