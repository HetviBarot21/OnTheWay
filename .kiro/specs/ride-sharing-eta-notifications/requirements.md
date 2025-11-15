# Requirements Document

## Introduction

This specification defines the ride sharing, ETA calculation, and notification features for the OnTheWay location sharing app. The system will enable users to share active trips with circle members, display real-time ETAs, show online/offline status, and send proximity-based push notifications when riders are approaching or have arrived at destinations.

## Glossary

- **OnTheWay System**: The Android mobile application for location sharing
- **Circle**: A group of users who share location data with each other
- **Circle Member**: A user who belongs to a specific Circle
- **Ride Share**: An active trip that a user shares with one or more Circle Members
- **ETA**: Estimated Time of Arrival calculated based on current location, destination, and speed
- **Online Status**: Indicator showing whether a Circle Member has an active internet connection
- **Push Notification**: A message sent via Firebase Cloud Messaging to a user's device
- **Proximity Notification**: A notification triggered when a rider is within a specific time or distance threshold
- **Firestore**: Firebase Cloud Firestore database used for real-time data synchronization
- **FCM**: Firebase Cloud Messaging service for push notifications

## Requirements

### Requirement 1: Ride Sharing Initiation

**User Story:** As a Circle Member, I want to share my active ride with specific members or my entire Circle, so that they can track my journey in real-time.

#### Acceptance Criteria

1. WHEN a Circle Member views another member's profile in the Circle, THE OnTheWay System SHALL display a "Share Ride" action button
2. WHEN a Circle Member taps the "Share Ride" button, THE OnTheWay System SHALL prompt the user to select a destination
3. WHEN a Circle Member selects a destination, THE OnTheWay System SHALL create a Ride Share document in Firestore with the user's current location and destination
4. WHERE a Circle Member is viewing the Circle list, THE OnTheWay System SHALL display a "Share Ride to Circle" button
5. WHEN a Circle Member taps "Share Ride to Circle", THE OnTheWay System SHALL share the ride with all members in that Circle

### Requirement 2: Online Status Display

**User Story:** As a Circle Member, I want to see which members are currently online, so that I know who has an active connection and can receive real-time updates.

#### Acceptance Criteria

1. WHEN a Circle Member opens the app with an active internet connection, THE OnTheWay System SHALL update the user's online status to "online" in Firestore
2. WHILE a Circle Member has an active internet connection, THE OnTheWay System SHALL maintain the online status as "online"
3. WHEN a Circle Member loses internet connection for more than 30 seconds, THE OnTheWay System SHALL update the user's online status to "offline"
4. WHEN a Circle Member views the Circle member list, THE OnTheWay System SHALL display an online indicator (green dot) next to each online member's name
5. WHEN a Circle Member views the Circle member list, THE OnTheWay System SHALL display an offline indicator (gray dot) next to each offline member's name

### Requirement 3: Real-time Location Updates During Ride Share

**User Story:** As a Circle Member who is sharing a ride, I want my location to be updated continuously, so that recipients can track my progress accurately.

#### Acceptance Criteria

1. WHILE a Ride Share is active, THE OnTheWay System SHALL update the sharer's location in Firestore every 10 seconds
2. WHILE a Ride Share is active, THE OnTheWay System SHALL include latitude, longitude, speed, and accuracy in each location update
3. WHEN a Circle Member receiving a shared ride views the map, THE OnTheWay System SHALL display the sharer's current location with a moving marker
4. WHEN the sharer's location is updated in Firestore, THE OnTheWay System SHALL update the map marker position within 2 seconds for all recipients

### Requirement 4: ETA Calculation

**User Story:** As a Circle Member receiving a shared ride, I want to see the estimated time of arrival, so that I know when the rider will reach their destination.

#### Acceptance Criteria

1. WHILE a Ride Share is active, THE OnTheWay System SHALL calculate ETA every 30 seconds using the Haversine formula for distance and current speed
2. WHEN the sharer's speed is greater than 0 meters per second, THE OnTheWay System SHALL calculate ETA by dividing remaining distance by current speed
3. WHEN the sharer's speed is 0 meters per second or unavailable, THE OnTheWay System SHALL calculate ETA using an assumed speed of 13.89 meters per second (50 km/h)
4. WHEN a Circle Member views a shared ride on the map, THE OnTheWay System SHALL display the ETA in minutes next to the sharer's name
5. WHEN the ETA is less than 1 minute, THE OnTheWay System SHALL display "Arriving now" instead of the minute value

### Requirement 5: Proximity Notifications - Two Minutes Away

**User Story:** As a Circle Member receiving a shared ride, I want to be notified when the rider is 2 minutes away, so that I can prepare for their arrival.

#### Acceptance Criteria

1. WHILE a Ride Share is active, THE OnTheWay System SHALL check if the calculated ETA is less than or equal to 2 minutes
2. WHEN the ETA reaches 2 minutes or less AND the distance is greater than 100 meters, THE OnTheWay System SHALL send a push notification to the destination recipient
3. THE OnTheWay System SHALL include the sharer's name and ETA in the notification message (e.g., "John is 2 minutes away")
4. THE OnTheWay System SHALL send the 2-minute notification only once per Ride Share
5. WHEN the 2-minute notification is sent, THE OnTheWay System SHALL update the Ride Share document to mark that the notification has been delivered

### Requirement 6: Proximity Notifications - Arrival

**User Story:** As a Circle Member receiving a shared ride, I want to be notified when the rider has arrived, so that I know they have reached the destination.

#### Acceptance Criteria

1. WHILE a Ride Share is active, THE OnTheWay System SHALL check if the sharer's location is within 100 meters of the destination
2. WHEN the sharer enters the 100-meter radius of the destination, THE OnTheWay System SHALL send an arrival push notification to the destination recipient
3. THE OnTheWay System SHALL include the sharer's name in the arrival notification message (e.g., "John has arrived")
4. THE OnTheWay System SHALL send the arrival notification only once per Ride Share
5. WHEN the arrival notification is sent, THE OnTheWay System SHALL mark the Ride Share as completed in Firestore

### Requirement 7: Ride Share Termination

**User Story:** As a Circle Member sharing a ride, I want to be able to stop sharing my ride at any time, so that I have control over my location privacy.

#### Acceptance Criteria

1. WHILE a Ride Share is active, THE OnTheWay System SHALL display a "Stop Sharing" button to the sharer
2. WHEN the sharer taps "Stop Sharing", THE OnTheWay System SHALL mark the Ride Share as inactive in Firestore
3. WHEN a Ride Share is marked as inactive, THE OnTheWay System SHALL stop sending location updates for that ride
4. WHEN a Ride Share is marked as inactive, THE OnTheWay System SHALL remove the ride display from recipients' maps within 5 seconds
5. WHEN a Ride Share is completed or stopped, THE OnTheWay System SHALL stop calculating ETA for that ride

### Requirement 8: Push Notification Delivery

**User Story:** As a Circle Member, I want to receive push notifications even when the app is in the background, so that I don't miss important ride updates.

#### Acceptance Criteria

1. WHEN a proximity notification is triggered, THE OnTheWay System SHALL send the notification via Firebase Cloud Messaging
2. THE OnTheWay System SHALL deliver push notifications to the recipient's device even when the app is not in the foreground
3. WHEN a push notification is received, THE OnTheWay System SHALL display the notification in the device's notification tray
4. WHEN a user taps a ride notification, THE OnTheWay System SHALL open the app and navigate to the Circle detail screen showing the shared ride
5. THE OnTheWay System SHALL include the notification type (2-minute or arrival) in the notification data payload

### Requirement 9: Ride Share Data Persistence

**User Story:** As a system administrator, I want ride share data to be stored reliably in Firestore, so that the feature works consistently across all devices.

#### Acceptance Criteria

1. WHEN a Ride Share is created, THE OnTheWay System SHALL store a document in the Firestore "trips" collection with a unique trip ID
2. THE OnTheWay System SHALL include userId, circleId, startLat, startLng, destinationLat, destinationLng, destinationName, startTime, isActive, and sharedWith fields in each trip document
3. WHEN a Ride Share location is updated, THE OnTheWay System SHALL update the corresponding location document in the "locations" collection
4. WHEN a Ride Share is completed or stopped, THE OnTheWay System SHALL update the trip document with endTime and set isActive to false
5. THE OnTheWay System SHALL allow only the ride sharer to create, update, or delete their own Ride Share documents

### Requirement 10: Multiple Simultaneous Ride Shares

**User Story:** As a Circle Member, I want to be able to view multiple active ride shares from different members simultaneously, so that I can track multiple people at once.

#### Acceptance Criteria

1. WHEN multiple Circle Members are sharing rides to the same Circle, THE OnTheWay System SHALL display all active rides on the map simultaneously
2. THE OnTheWay System SHALL display each sharer's marker with their name and ETA
3. THE OnTheWay System SHALL calculate and update ETAs independently for each active Ride Share
4. THE OnTheWay System SHALL send proximity notifications independently for each active Ride Share
5. WHEN a Circle Member views the Circle detail screen, THE OnTheWay System SHALL list all active ride shares with their respective ETAs
