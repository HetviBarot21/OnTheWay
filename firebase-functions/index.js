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
                body: getNotificationBody(data.type, data.from, data.eta || 0, data.latitude, data.longitude)
            },
            data: {
                type: data.type,
                from: data.from,
                eta: String(data.eta || 0),
                timestamp: String(data.timestamp || Date.now()),
                latitude: String(data.latitude || ''),
                longitude: String(data.longitude || '')
            },
            android: {
                priority: data.type === 'sos' ? 'max' : 'high',
                notification: {
                    sound: 'default',
                    channelId: data.type === 'sos' ? 'ontheway_sos' : 'ontheway_notifications'
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
        case 'sos':
            return '🚨 EMERGENCY SOS';
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
function getNotificationBody(type, from, eta, latitude, longitude) {
    switch (type) {
        case 'sos':
            const hasLocation = latitude && longitude;
            return `${from} needs help! ${hasLocation ? 'Tap to see location.' : ''}`;
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
 * Send email notification when ride is shared
 * Triggered when activeRides document is created
 */
exports.sendRideShareEmail = functions.firestore
    .document('activeRides/{rideId}')
    .onCreate(async (snap, context) => {
        const ride = snap.data();
        const db = admin.firestore();
        
        try {
            // Get recipient user details
            const recipientDoc = await db.collection('users').doc(ride.recipientId).get();
            const recipientData = recipientDoc.data();
            
            if (!recipientData || !recipientData.email) {
                console.log('Recipient email not found');
                return null;
            }
            
            // Get sender user details
            const senderDoc = await db.collection('users').doc(ride.senderId).get();
            const senderData = senderDoc.data();
            const senderName = senderData?.name || ride.senderEmail;
            
            // Create email notification document
            await db.collection('mail').add({
                to: recipientData.email,
                message: {
                    subject: `${senderName} is on the way to you!`,
                    html: `
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                            <h2 style="color: #6200EE;">🚗 Someone is coming to you!</h2>
                            <p style="font-size: 16px;">
                                <strong>${senderName}</strong> has started sharing their ride with you.
                            </p>
                            <p style="font-size: 14px; color: #666;">
                                You'll receive updates as they get closer to your location.
                            </p>
                            <div style="background-color: #f5f5f5; padding: 15px; border-radius: 8px; margin: 20px 0;">
                                <p style="margin: 5px 0;"><strong>From:</strong> ${senderName}</p>
                                <p style="margin: 5px 0;"><strong>Started:</strong> ${new Date(ride.startTime).toLocaleString()}</p>
                            </div>
                            <p style="font-size: 12px; color: #999; margin-top: 30px;">
                                Open the OnTheWay app to see real-time location and ETA.
                            </p>
                        </div>
                    `
                }
            });
            
            console.log(`Ride share email sent to ${recipientData.email}`);
            return null;
        } catch (error) {
            console.error('Error sending ride share email:', error);
            return null;
        }
    });

/**
 * Send email when ETA reaches 5 minutes
 * Monitors activeRides and sends email notification
 */
exports.sendETAEmailNotifications = functions.pubsub
    .schedule('every 1 minutes')
    .onRun(async (context) => {
        const db = admin.firestore();
        
        try {
            // Get all active rides
            const ridesSnapshot = await db.collection('activeRides')
                .where('active', '==', true)
                .get();
            
            if (ridesSnapshot.empty) {
                return null;
            }
            
            for (const rideDoc of ridesSnapshot.docs) {
                const ride = rideDoc.data();
                
                // Get sender's current location
                const senderCircles = await db.collection('circles')
                    .where('members', 'array-contains', ride.senderId)
                    .limit(1)
                    .get();
                
                if (senderCircles.empty) continue;
                
                const circleId = senderCircles.docs[0].id;
                const memberDoc = await db.collection('circles')
                    .doc(circleId)
                    .collection('members')
                    .doc(ride.senderId)
                    .get();
                
                if (!memberDoc.exists) continue;
                
                const senderLocation = memberDoc.data();
                
                // Calculate distance to recipient
                const distance = calculateDistance(
                    senderLocation.latitude,
                    senderLocation.longitude,
                    ride.destinationLat,
                    ride.destinationLng
                );
                
                // Calculate ETA in minutes
                const eta = Math.round(distance / 13.89 / 60);
                
                // Send email at 5 minutes if not already sent
                if (eta <= 5 && eta > 0 && !ride.email5MinSent) {
                    await sendETAEmail(db, ride, eta, '5 minutes');
                    await rideDoc.ref.update({ email5MinSent: true });
                }
                
                // Send email at 2 minutes if not already sent
                if (eta <= 2 && eta > 0 && !ride.email2MinSent) {
                    await sendETAEmail(db, ride, eta, '2 minutes');
                    await rideDoc.ref.update({ email2MinSent: true });
                }
                
                // Send arrival email if arrived
                if (distance < 100 && !ride.emailArrivedSent) {
                    await sendArrivalEmail(db, ride);
                    await rideDoc.ref.update({ 
                        emailArrivedSent: true,
                        active: false 
                    });
                }
            }
            
            return null;
        } catch (error) {
            console.error('Error sending ETA emails:', error);
            return null;
        }
    });

/**
 * Helper function to send ETA email
 */
async function sendETAEmail(db, ride, eta, milestone) {
    try {
        const recipientDoc = await db.collection('users').doc(ride.recipientId).get();
        const recipientData = recipientDoc.data();
        
        if (!recipientData || !recipientData.email) return;
        
        const senderDoc = await db.collection('users').doc(ride.senderId).get();
        const senderData = senderDoc.data();
        const senderName = senderData?.name || ride.senderEmail;
        
        await db.collection('mail').add({
            to: recipientData.email,
            message: {
                subject: `${senderName} is ${milestone} away!`,
                html: `
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                        <h2 style="color: #6200EE;">🚗 Almost There!</h2>
                        <p style="font-size: 18px; font-weight: bold; color: #333;">
                            ${senderName} is approximately <span style="color: #6200EE;">${eta} minute${eta !== 1 ? 's' : ''}</span> away!
                        </p>
                        <div style="background-color: #f5f5f5; padding: 20px; border-radius: 8px; margin: 20px 0; text-align: center;">
                            <p style="font-size: 48px; margin: 0; color: #6200EE;">${eta}</p>
                            <p style="font-size: 16px; margin: 5px 0; color: #666;">minute${eta !== 1 ? 's' : ''} away</p>
                        </div>
                        <p style="font-size: 14px; color: #666;">
                            Get ready! They'll be arriving soon.
                        </p>
                        <p style="font-size: 12px; color: #999; margin-top: 30px;">
                            Open the OnTheWay app to see their exact location.
                        </p>
                    </div>
                `
            }
        });
        
        console.log(`ETA email (${milestone}) sent to ${recipientData.email}`);
    } catch (error) {
        console.error('Error sending ETA email:', error);
    }
}

/**
 * Helper function to send arrival email
 */
async function sendArrivalEmail(db, ride) {
    try {
        const recipientDoc = await db.collection('users').doc(ride.recipientId).get();
        const recipientData = recipientDoc.data();
        
        if (!recipientData || !recipientData.email) return;
        
        const senderDoc = await db.collection('users').doc(ride.senderId).get();
        const senderData = senderDoc.data();
        const senderName = senderData?.name || ride.senderEmail;
        
        await db.collection('mail').add({
            to: recipientData.email,
            message: {
                subject: `${senderName} has arrived!`,
                html: `
                    <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                        <h2 style="color: #4CAF50;">📍 Arrived!</h2>
                        <p style="font-size: 18px; font-weight: bold; color: #333;">
                            ${senderName} has arrived at your location!
                        </p>
                        <div style="background-color: #E8F5E9; padding: 20px; border-radius: 8px; margin: 20px 0; text-align: center;">
                            <p style="font-size: 48px; margin: 0;">✓</p>
                            <p style="font-size: 16px; margin: 5px 0; color: #4CAF50;">Arrived</p>
                        </div>
                        <p style="font-size: 14px; color: #666;">
                            The ride tracking has ended.
                        </p>
                    </div>
                `
            }
        });
        
        console.log(`Arrival email sent to ${recipientData.email}`);
    } catch (error) {
        console.error('Error sending arrival email:', error);
    }
}

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
