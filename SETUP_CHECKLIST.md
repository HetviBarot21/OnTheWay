# Firebase Setup Checklist

## Pre-Setup (Already Done ✅)

- [x] Firebase project created (`ontheway-fbe72`)
- [x] `google-services.json` added to project
- [x] Firebase dependencies added to `build.gradle.kts`
- [x] Authentication code implemented
- [x] Database structure designed
- [x] Security rules created (`firestore.rules`)
- [x] Database indexes created (`firestore.indexes.json`)
- [x] Circle invite system implemented

---

## Your Setup Tasks (Do These Now)

### 1. Enable Firebase Authentication
**Time: 2 minutes**

- [ ] Open: https://console.firebase.google.com/project/ontheway-fbe72/authentication
- [ ] Click "Get Started"
- [ ] Click "Email/Password" provider
- [ ] Toggle "Enable" switch
- [ ] Click "Save"

**Verify:** You should see "Email/Password" with a green checkmark

---

### 2. Enable Cloud Firestore
**Time: 3 minutes**

- [ ] Open: https://console.firebase.google.com/project/ontheway-fbe72/firestore
- [ ] Click "Create database"
- [ ] Select "Start in test mode"
- [ ] Choose location (e.g., `us-central1`)
- [ ] Click "Enable"
- [ ] Wait 2-3 minutes for provisioning

**Verify:** You should see "Cloud Firestore" page with empty database

---

### 3. Install Firebase CLI
**Time: 2 minutes**

Open terminal and run:
```bash
npm install -g firebase-tools
```

**Verify:** Run `firebase --version` (should show version number)

---

### 4. Login to Firebase
**Time: 1 minute**

```bash
firebase login
```

- [ ] Browser opens
- [ ] Select your Google account
- [ ] Grant permissions
- [ ] See "Success!" message

**Verify:** Terminal shows "✔ Success! Logged in as your@email.com"

---

### 5. Initialize Firebase in Project
**Time: 2 minutes**

In your project folder (`C:\Users\vinit\AndroidStudioProjects\OnTheWay`):

```bash
firebase init firestore
```

Answer the prompts:
- [ ] "Use an existing project" → Select `ontheway-fbe72`
- [ ] "What file should be used for Firestore Rules?" → Press Enter (use `firestore.rules`)
- [ ] "What file should be used for Firestore indexes?" → Press Enter (use `firestore.indexes.json`)

**Verify:** You should see "✔ Firestore initialization complete!"

---

### 6. Deploy Security Rules and Indexes
**Time: 1 minute**

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

- [ ] Wait for deployment
- [ ] See "✔ Deploy complete!"

**Verify:** 
- Open: https://console.firebase.google.com/project/ontheway-fbe72/firestore/rules
- You should see your security rules

---

### 7. Test User Registration
**Time: 3 minutes**

- [ ] Run app in Android Studio
- [ ] Click "Get Started"
- [ ] Click "Sign Up"
- [ ] Fill in:
  - Name: Test User
  - Phone: +1234567890
  - Email: test@example.com
  - Password: test123
- [ ] Click "Sign Up"
- [ ] Should navigate to home screen

**Verify in Firebase Console:**
- [ ] Authentication → Users → See your test user
- [ ] Firestore → users → See user document with your data

---

### 8. Test Circle Creation
**Time: 2 minutes**

- [ ] In app, click "Circles" button
- [ ] Click "Create Circle"
- [ ] Enter name: "Test Circle"
- [ ] Click "Create"
- [ ] Should see circle with invite code (e.g., "ABC123")
- [ ] Click on circle to expand
- [ ] Note the invite code

**Verify in Firebase Console:**
- [ ] Firestore → circles → See circle document
- [ ] Check `inviteCode` field (should be 6 characters)
- [ ] Check `members` array (should contain your userId)

---

### 9. Test Circle Invite
**Time: 3 minutes**

- [ ] In app, click back to home
- [ ] Click "Logout"
- [ ] Click "Sign Up" (create second account)
- [ ] Fill in different email: test2@example.com
- [ ] After signup, go to "Circles"
- [ ] Click "Join Circle"
- [ ] Enter the invite code from step 8
- [ ] Click "Join"
- [ ] Should see the circle with 2 members

**Verify in Firebase Console:**
- [ ] Firestore → circles → Open your circle
- [ ] Check `members` array (should have 2 userIds)

**Verify in App:**
- [ ] Expand the circle
- [ ] Should see both members listed

---

## Troubleshooting Checklist

### If "Permission Denied" Error:
- [ ] Check Firestore is enabled in console
- [ ] Run: `firebase deploy --only firestore:rules`
- [ ] Wait 1 minute and try again
- [ ] Check you're logged in (Firebase Auth)

### If "Index Required" Error:
- [ ] Run: `firebase deploy --only firestore:indexes`
- [ ] Or click the link in error to create in console
- [ ] Wait 2-3 minutes for index to build

### If "Email already in use":
- [ ] Use different email
- [ ] Or delete user in Firebase Console → Authentication

### If Firebase CLI not found:
- [ ] Install Node.js first: https://nodejs.org
- [ ] Then run: `npm install -g firebase-tools`
- [ ] Restart terminal

### If App crashes on startup:
- [ ] Check `google-services.json` is in `app/` folder
- [ ] Sync Gradle files
- [ ] Clean and rebuild project
- [ ] Check Logcat for errors

---

## Success Criteria

You're done when:
- [x] ✅ Can register new users
- [x] ✅ Can login with existing users
- [x] ✅ Can create circles
- [x] ✅ Can see invite codes
- [x] ✅ Can join circles with codes
- [x] ✅ Can see circle members
- [x] ✅ Firebase Console shows all data

---

## What Works After Setup

### Authentication ✅
- Email/password registration
- Login/logout
- User profiles in Firestore
- Password validation

### Circles ✅
- Create circles with auto-generated codes
- Join circles with invite codes
- See circle members
- Leave circles
- Delete circles (creator only)

### Database ✅
- User profiles stored
- Circle data stored
- Invite codes stored
- Membership tracked
- Security rules enforced

### Ready for Next Steps 🔄
- Location sharing (needs permissions)
- Real-time updates (needs location service)
- Push notifications (needs FCM setup)
- Map display (needs Mapbox config)

---

## Time Estimate

| Task | Time |
|------|------|
| Enable Authentication | 2 min |
| Enable Firestore | 3 min |
| Install Firebase CLI | 2 min |
| Login to Firebase | 1 min |
| Initialize project | 2 min |
| Deploy rules | 1 min |
| Test registration | 3 min |
| Test circles | 2 min |
| Test invites | 3 min |
| **Total** | **~20 min** |

---

## Quick Commands Reference

```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login
firebase login

# Initialize Firestore
firebase init firestore

# Deploy everything
firebase deploy --only firestore:rules,firestore:indexes

# Deploy only rules
firebase deploy --only firestore:rules

# Deploy only indexes
firebase deploy --only firestore:indexes

# Check current project
firebase projects:list

# Switch project
firebase use ontheway-fbe72
```

---

## Support Links

- **Firebase Console:** https://console.firebase.google.com/project/ontheway-fbe72
- **Authentication:** https://console.firebase.google.com/project/ontheway-fbe72/authentication
- **Firestore:** https://console.firebase.google.com/project/ontheway-fbe72/firestore
- **Firebase CLI Docs:** https://firebase.google.com/docs/cli

---

## Next Steps After Setup

1. **Test location sharing:**
   - Grant location permissions
   - Share location in a circle
   - See members on map

2. **Enable Cloud Messaging:**
   - For push notifications
   - When members arrive/leave

3. **Configure Mapbox:**
   - For better map display
   - Custom styling

4. **Deploy to device:**
   - Test on real Android phone
   - Location works better on device

---

## Files to Reference

- `QUICK_START.md` - Quick setup guide
- `FIREBASE_SETUP.md` - Detailed setup instructions
- `DATABASE_STRUCTURE.md` - Database documentation
- `CIRCLE_INVITE_FLOW.md` - How invite codes work
- `firestore.rules` - Security rules
- `firestore.indexes.json` - Database indexes

---

**Ready? Start with Step 1! 🚀**
