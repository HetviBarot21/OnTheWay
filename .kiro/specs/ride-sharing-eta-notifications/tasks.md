# Implementation Plan

- [x] 1. Set up core trip management infrastructure



  - Create TripService class with Firestore integration
  - Implement createTrip() method to create new ride shares
  - Implement getActiveTrips() to fetch active trips for a circle
  - Implement stopTrip() and completeTrip() methods
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 7.1, 7.2, 7.3, 7.4, 7.5, 9.1, 9.2, 9.4, 9.5_

- [x] 2. Implement online status tracking system



  - Create OnlineStatusManager class
  - Implement setOnlineStatus() to update user's online/offline state
  - Add heartbeat mechanism that updates lastSeen every 30 seconds
  - Implement observeOnlineStatus() to listen for status changes
  - Add lifecycle hooks to update status on app foreground/background
  - _Requirements: 2.1, 2.2, 2.3_

- [ ] 3. Build ETA calculation engine
  - Create ETACalculator class
  - Implement Haversine formula for distance calculation
  - Implement calculateETA() using distance and speed
  - Add default speed fallback (13.89 m/s) when speed is unavailable
  - Implement formatETA() to display "Arriving now" when ETA < 1 min
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 4. Enhance LocationService for trip tracking
  - Add startTripTracking() and stopTripTracking() methods
  - Implement updateActiveTrips() to update trip locations on each GPS update
  - Integrate TripService.updateTripLocation() calls
  - Add ETA calculation every 30 seconds for active trips
  - Update trip document in Firestore with current location and ETA
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 9.3_

- [ ] 5. Create proximity notification system
  - Create ProximityNotificationManager class
  - Implement checkProximity() to monitor ETA and distance thresholds
  - Implement send2MinuteNotification() triggered when ETA ≤ 2 min and distance > 100m
  - Implement sendArrivalNotification() triggered when distance ≤ 100m
  - Add notification deduplication logic (notified2Min, notifiedArrival flags)
  - Create NotificationPayload documents in Firestore for FCM delivery
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 6.4, 6.5_

- [ ] 6. Implement FCM push notification delivery
  - Update MyFirebaseMessagingService to handle trip notification types
  - Add notification handlers for "trip_started", "2_minutes", "arrived"
  - Implement notification tap action to open CircleDetailScreen
  - Ensure notifications work when app is in background
  - Add notification channel for trip notifications
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5_

- [ ] 7. Update data models for trip support
  - Enhance Trip model with currentLat, currentLng, currentSpeed, eta fields
  - Enhance CircleMember model with isOnline, isSharingTrip, activeTrip, eta fields
  - Create OnlineStatus model
  - Create NotificationPayload model
  - Update Models.kt file
  - _Requirements: 9.1, 9.2_

- [ ] 8. Enhance CircleService with trip queries
  - Add getCircleMembersWithTrips() to fetch members with their active trips
  - Implement isUserOnline() to check online status
  - Update getCircleMembers() to include online status and trip info
  - Add real-time listener for active trips in a circle
  - _Requirements: 2.4, 2.5, 10.1, 10.2_

- [ ] 9. Build Share Ride UI dialog
  - Create ShareRideDialog composable
  - Add destination picker (map or address input)
  - Add recipient selection (individual members or entire circle)
  - Implement "Start Sharing" button that calls TripService.createTrip()
  - Add validation for destination selection
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

- [ ] 10. Update CircleDetailScreen UI
  - Add online status indicators (green/gray dots) next to member names
  - Add "Share Ride" button in top app bar
  - Display active trip banner for ongoing rides
  - Show ETA next to member names when they're sharing trips
  - Add "Stop Sharing" button for user's own active trip
  - Implement real-time updates for trip progress
  - _Requirements: 2.4, 2.5, 7.1, 10.1, 10.2, 10.5_

- [ ] 11. Enhance map display for active trips
  - Update map markers to show different color for members sharing trips
  - Add ETA label below markers for active trips
  - Add destination marker for trip endpoints
  - Implement animated marker movement for real-time location updates
  - Display multiple active trips simultaneously on map
  - _Requirements: 3.3, 3.4, 10.1, 10.2, 10.3, 10.4_

- [ ] 12. Implement trip monitoring and cleanup
  - Add background job to check for stale trips (no updates > 30 min)
  - Implement automatic trip completion when destination reached
  - Add cleanup for completed trips older than 24 hours
  - Implement error handling for network failures during trip
  - Add retry logic for failed Firestore writes
  - _Requirements: 7.3, 7.4, 7.5, 9.4_

- [ ] 13. Update Firestore security rules
  - Add security rules for trips collection
  - Add security rules for online status documents
  - Add security rules for notification payloads
  - Ensure only trip owner can create/update/delete their trips
  - Ensure only user can update their own online status
  - _Requirements: 9.5_

- [ ] 14. Add error handling and edge cases
  - Handle location permission revoked during trip
  - Handle network disconnection during trip
  - Handle FCM token refresh
  - Add fallback for poor GPS accuracy
  - Implement retry logic for failed notifications
  - Show appropriate error messages to users
  - _Requirements: All requirements (error handling aspect)_

- [ ]* 15. Create integration tests
  - Test complete ride sharing flow (create, update, stop)
  - Test ETA calculation with various speeds and distances
  - Test proximity notification triggers
  - Test online status updates and heartbeat
  - Test multiple simultaneous trips
  - _Requirements: All requirements (testing aspect)_

- [ ]* 16. Perform manual testing
  - Test ride share to individual member
  - Test ride share to entire circle
  - Test 2-minute notification delivery
  - Test arrival notification delivery
  - Test online/offline status display
  - Test with app in background
  - Test with no internet connection
  - Test with multiple active trips
  - _Requirements: All requirements (validation aspect)_
