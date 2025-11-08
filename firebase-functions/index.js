const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

/**
 * Send push notification when a notification document is created
 * Triggered by: Firestore onCreate in /notifications collection
 */
exports.sendNotification = functions.firestore
    .document('notifications/{notificationId}')
    .onCreate(async (snap, context) => {
        const data = snap.data();
        
        // Validate required fields
        if (!data.token || !data.type || !data.from) {
            console.error('Missing required fields in notification');
            await snap.ref.delete();
            return;
        }
        
        const message = {
            token: data.token,
            notification: {
                title: getNotificationTitle(data.type, data.from),
                body: getNotificationBody(data.type, data.from, data.eta || 0)
            },
            data: {
                type: data.type,
                from: data.from,
                eta: String(data.eta || 0),
                timestamp: String(data.timestamp || Date.now())
            },
            android: {
                priority: 'high',
                notification: {
                    sound: 'default',
                    channelId: 'ontheway_notifications'
                }
            }
        };
        
        try {
            const response = await admin.messaging().send(message);
            console.log('Successfully sent notification:', response);
            
            // Delete notification after sending
            await snap.ref.delete();
        } catch (error) {
            console.error('Error sending notification:', error);
            
            // Mark as failed instead of deleting
            await snap.ref.update({
                status: 'failed',
                error: error.message,
                failedAt: admin.firestore.FieldValue.serverTimestamp()
            });
        }
    });

/**
 * Get notification title based on type
 */
function getNotificationTitle(type, from) {
    switch (type) {
        case '2_minutes':
            return 'Almost There! 🚗';
        case 'arrived':
            return 'Arrived! 📍';
        case 'left':
            return 'Left Location';
        case 'entered':
            return 'Entered Location';
        default:
            return 'Location Update';
    }
}

/**
 * Get notification body based on type
 */
function getNotificationBody(type, from, eta) {
    switch (type) {
        case '2_minutes':
            return `${from} is 2 minutes away (ETA: ${eta} min)`;
        case 'arrived':
            return `${from} has arrived at the destination`;
        case 'left':
            return `${from} has left the location`;
        case 'entered':
            return `${from} has entered the location`;
        default:
            return `Location update from ${from}`;
    }
}

/**
 * Clean up old location updates
 * Runs daily to remove location data older than 7 days
 */
exports.cleanupOldLocations = functions.pubsub
    .schedule('every 24 hours')
    .timeZone('America/New_York')
    .onRun(async (context) => {
        const db = admin.firestore();
        const cutoffTime = Date.now() - (7 * 24 * 60 * 60 * 1000); // 7 days ago
        
        try {
            // Get all old location updates across all users
            const snapshot = await db.collectionGroup('updates')
                .where('timestamp', '<', cutoffTime)
                .limit(500) // Process in batches
                .get();
            
            if (snapshot.empty) {
                console.log('No old locations to clean up');
                return null;
            }
            
            // Delete in batch
            const batch = db.batch();
            snapshot.docs.forEach(doc => {
                batch.delete(doc.ref);
            });
            
            await batch.commit();
            console.log(`Successfully deleted ${snapshot.size} old location updates`);
            
            return null;
        } catch (error) {
            console.error('Error cleaning up old locations:', error);
            return null;
        }
    });

/**
 * Clean up failed notifications
 * Runs every hour to remove failed notifications older than 1 hour
 */
exports.cleanupFailedNotifications = functions.pubsub
    .schedule('every 1 hours')
    .onRun(async (context) => {
        const db = admin.firestore();
        const cutoffTime = Date.now() - (60 * 60 * 1000); // 1 hour ago
        
        try {
            const snapshot = await db.collection('notifications')
                .where('status', '==', 'failed')
                .where('failedAt', '<', cutoffTime)
                .limit(100)
                .get();
            
            if (snapshot.empty) {
                console.log('No failed notifications to clean up');
                return null;
            }
            
            const batch = db.batch();
            snapshot.docs.forEach(doc => {
                batch.delete(doc.ref);
            });
            
            await batch.commit();
            console.log(`Successfully deleted ${snapshot.size} failed notifications`);
            
            return null;
        } catch (error) {
            console.error('Error cleaning up failed notifications:', error);
            return null;
        }
    });

/**
 * Update user's last active timestamp when they update location
 */
exports.updateUserActivity = functions.firestore
    .document('locations/{userId}')
    .onWrite(async (change, context) => {
        const userId = context.params.userId;
        
        try {
            await admin.firestore()
                .collection('users')
                .document(userId)
                .update({
                    lastActive: admin.firestore.FieldValue.serverTimestamp()
                });
            
            return null;
        } catch (error) {
            console.error('Error updating user activity:', error);
            return null;
        }
    });

/**
 * Send welcome notification when user joins a circle
 */
exports.onCircleJoin = functions.firestore
    .document('circles/{circleId}')
    .onUpdate(async (change, context) => {
        const before = change.before.data();
        const after = change.after.data();
        
        // Check if new members were added
        const newMembers = after.members.filter(m => !before.members.includes(m));
        
        if (newMembers.length === 0) {
            return null;
        }
        
        const db = admin.firestore();
        const circleName = after.name;
        
        // Send notification to all existing members
        for (const memberId of before.members) {
            try {
                const userDoc = await db.collection('users').doc(memberId).get();
                const fcmToken = userDoc.data()?.fcmToken;
                
                if (fcmToken) {
                    const newMemberNames = [];
                    for (const newMemberId of newMembers) {
                        const newMemberDoc = await db.collection('users').doc(newMemberId).get();
                        newMemberNames.push(newMemberDoc.data()?.name || 'Someone');
                    }
                    
                    const message = {
                        token: fcmToken,
                        notification: {
                            title: `New Member in ${circleName}`,
                            body: `${newMemberNames.join(', ')} joined the circle`
                        },
                        data: {
                            type: 'circle_join',
                            circleId: context.params.circleId,
                            circleName: circleName
                        }
                    };
                    
                    await admin.messaging().send(message);
                }
            } catch (error) {
                console.error('Error sending circle join notification:', error);
            }
        }
        
        return null;
    });

/**
 * Calculate and update ETA for active trips
 * Runs every minute
 */
exports.updateETAs = functions.pubsub
    .schedule('every 1 minutes')
    .onRun(async (context) => {
        const db = admin.firestore();
        
        try {
            // Get all active trips
            const tripsSnapshot = await db.collection('trips')
                .where('isActive', '==', true)
                .get();
            
            if (tripsSnapshot.empty) {
                return null;
            }
            
            const batch = db.batch();
            
            for (const tripDoc of tripsSnapshot.docs) {
                const trip = tripDoc.data();
                
                // Get user's current location
                const locationDoc = await db.collection('locations')
                    .doc(trip.userId)
                    .get();
                
                if (!locationDoc.exists) continue;
                
                const location = locationDoc.data();
                
                // Calculate distance
                const distance = calculateDistance(
                    location.latitude,
                    location.longitude,
                    trip.destinationLat,
                    trip.destinationLng
                );
                
                // Calculate ETA (assuming average speed of 50 km/h)
                const eta = Math.round(distance / 13.89 / 60); // minutes
                
                // Update trip with new ETA
                batch.update(tripDoc.ref, {
                    currentETA: eta,
                    lastETAUpdate: admin.firestore.FieldValue.serverTimestamp()
                });
            }
            
            await batch.commit();
            console.log(`Updated ETAs for ${tripsSnapshot.size} trips`);
            
            return null;
        } catch (error) {
            console.error('Error updating ETAs:', error);
            return null;
        }
    });

/**
 * Calculate distance between two coordinates using Haversine formula
 */
function calculateDistance(lat1, lon1, lat2, lon2) {
    const R = 6371000; // Earth's radius in meters
    const dLat = toRadians(lat2 - lat1);
    const dLon = toRadians(lon2 - lon1);
    
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
              Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) *
              Math.sin(dLon / 2) * Math.sin(dLon / 2);
    
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    
    return R * c; // Distance in meters
}

function toRadians(degrees) {
    return degrees * (Math.PI / 180);
}
