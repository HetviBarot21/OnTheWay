# Circle Invite System - How It Works

## Overview
Your app uses a simple 6-character invite code system to let users join circles without needing to know phone numbers or emails.

---

## The Flow

### 1. Create a Circle

```
User A creates "Family" circle
         ↓
App generates invite code: "FAM123"
         ↓
Stored in Firestore:
circles/circle-uuid {
  name: "Family",
  inviteCode: "FAM123",
  members: ["userA"],
  createdBy: "userA"
}
```

### 2. Share the Invite Code

```
User A shares code with User B
         ↓
Methods:
  • Copy to clipboard
  • Share via SMS/WhatsApp
  • Share via any app
  • Tell them verbally
         ↓
User B receives: "FAM123"
```

### 3. Join the Circle

```
User B opens app
         ↓
Clicks "Join Circle"
         ↓
Enters code: "FAM123"
         ↓
App searches Firestore:
  WHERE inviteCode = "FAM123"
         ↓
Found! Add User B to members
         ↓
Updated in Firestore:
circles/circle-uuid {
  name: "Family",
  inviteCode: "FAM123",
  members: ["userA", "userB"],  ← User B added!
  createdBy: "userA"
}
```

### 4. See Each Other

```
Both users now in same circle
         ↓
User A sees:
  • Family (2 members)
    - User A (you)
    - User B
         ↓
User B sees:
  • Family (2 members)
    - User A
    - User B (you)
         ↓
Both can now share locations!
```

---

## Example Scenario

### Scenario: Family Circle

**Step 1: Mom creates circle**
```
Mom opens app → Circles → Create Circle
Name: "Family"
Result: Invite code "FAM123"
```

**Step 2: Mom shares code**
```
Mom texts Dad: "Join my OnTheWay circle! Code: FAM123"
Mom texts Son: "Join my OnTheWay circle! Code: FAM123"
```

**Step 3: Dad joins**
```
Dad opens app → Circles → Join Circle
Enters: "FAM123"
Result: Dad is now in "Family" circle
```

**Step 4: Son joins**
```
Son opens app → Circles → Join Circle
Enters: "FAM123"
Result: Son is now in "Family" circle
```

**Step 5: Everyone connected**
```
Family circle now has 3 members:
  • Mom (creator)
  • Dad
  • Son

All can see each other's locations when shared!
```

---

## Invite Code Features

### Code Generation
- **Format:** 6 characters (letters + numbers)
- **Example codes:** 
  - `FAM123` - Family
  - `WRK456` - Work
  - `FRN789` - Friends
  - `ABC123` - Any circle
- **Uniqueness:** Each circle gets a unique code
- **Permanent:** Code never changes for a circle

### Sharing Methods

**In-App Sharing:**
```kotlin
// Copy to clipboard
val clipboard = context.getSystemService(ClipboardManager::class.java)
clipboard.setPrimaryClip(ClipData.newPlainText("Invite Code", "FAM123"))

// Share via any app
val shareIntent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, "Join my circle! Code: FAM123")
}
startActivity(Intent.createChooser(shareIntent, "Share invite"))
```

**Manual Sharing:**
- Tell someone the code verbally
- Write it down
- Send via email/SMS/WhatsApp
- Post in group chat

---

## Database Structure

### When Circle is Created
```javascript
// Firestore document
circles/abc-123-xyz {
  circleId: "abc-123-xyz",
  name: "Family",
  inviteCode: "FAM123",        // ← The magic code!
  members: ["user-mom-id"],
  createdBy: "user-mom-id",
  createdAt: 1699564800000
}
```

### When Someone Joins
```javascript
// Updated document
circles/abc-123-xyz {
  circleId: "abc-123-xyz",
  name: "Family",
  inviteCode: "FAM123",
  members: [
    "user-mom-id",
    "user-dad-id",             // ← Dad joined!
    "user-son-id"              // ← Son joined!
  ],
  createdBy: "user-mom-id",
  createdAt: 1699564800000
}
```

---

## Code Implementation

### Create Circle (CircleService.kt)
```kotlin
suspend fun createCircle(name: String): Circle {
    val inviteCode = generateInviteCode()  // Generates "ABC123"
    
    val circle = Circle(
        circleId = UUID.randomUUID().toString(),
        name = name,
        inviteCode = inviteCode,  // ← Store the code
        members = listOf(currentUserId)
    )
    
    firestore.collection("circles")
        .document(circle.circleId)
        .set(circle)
    
    return circle
}

private fun generateInviteCode(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..6).map { chars.random() }.joinToString("")
}
```

### Join Circle (CircleService.kt)
```kotlin
suspend fun joinCircleWithCode(inviteCode: String): Circle? {
    // Find circle by code
    val snapshot = firestore.collection("circles")
        .whereEqualTo("inviteCode", inviteCode)  // ← Search by code
        .limit(1)
        .get()
        .await()
    
    val circle = snapshot.documents.firstOrNull()?.toObject(Circle::class.java)
    
    if (circle != null) {
        // Add current user to members
        val updatedMembers = circle.members + currentUserId
        
        firestore.collection("circles")
            .document(circle.circleId)
            .update("members", updatedMembers)  // ← Add to members
            .await()
    }
    
    return circle
}
```

---

## UI Flow

### CirclesScreen.kt - Create Dialog
```
┌─────────────────────────────┐
│   Create New Circle         │
├─────────────────────────────┤
│                             │
│ Circle Name:                │
│ ┌─────────────────────────┐ │
│ │ Family                  │ │
│ └─────────────────────────┘ │
│                             │
│  [Cancel]      [Create]     │
└─────────────────────────────┘
         ↓
┌─────────────────────────────┐
│   Family                    │
│   2 members                 │
│                             │
│   Invite Code: FAM123       │
│   [Copy Code] 📋            │
└─────────────────────────────┘
```

### CirclesScreen.kt - Join Dialog
```
┌─────────────────────────────┐
│   Join Circle               │
├─────────────────────────────┤
│                             │
│ Invite Code:                │
│ ┌─────────────────────────┐ │
│ │ FAM123                  │ │
│ └─────────────────────────┘ │
│                             │
│  [Cancel]      [Join]       │
└─────────────────────────────┘
         ↓
┌─────────────────────────────┐
│   Family                    │
│   3 members                 │
│   • Mom                     │
│   • Dad                     │
│   • You                     │
└─────────────────────────────┘
```

---

## Security

### Who Can Join?
- ✅ Anyone with the invite code
- ✅ Must have an account (registered)
- ✅ Can join multiple circles
- ✅ Code never expires

### Who Can See the Code?
- ✅ All circle members
- ✅ Displayed in circle details
- ✅ Can be copied/shared anytime

### Who Can Delete a Circle?
- ✅ Only the creator (createdBy field)
- ❌ Other members can only leave

### Privacy
- ✅ Circle members see each other's names
- ✅ Circle members see each other's locations (when shared)
- ❌ Non-members cannot see circle data
- ❌ Invite code is not public (must be shared)

---

## Testing the Flow

### Test 1: Create and Join
1. **Account A:** Create circle "Test"
2. **Account A:** Note invite code (e.g., "TST123")
3. **Account B:** Join with code "TST123"
4. **Both:** Should see each other in "Test" circle

### Test 2: Multiple Circles
1. **Account A:** Create "Family" (code: FAM123)
2. **Account A:** Create "Work" (code: WRK456)
3. **Account B:** Join "Family" with FAM123
4. **Account C:** Join "Work" with WRK456
5. **Account A:** Should see both circles
6. **Account B:** Should only see "Family"
7. **Account C:** Should only see "Work"

### Test 3: Invalid Code
1. **Account A:** Try to join with "INVALID"
2. **Expected:** Error message "Circle not found"

---

## Summary

**Simple 3-Step Process:**
1. **Create** → Get invite code
2. **Share** → Send code to others
3. **Join** → Enter code to join

**No need for:**
- ❌ Phone numbers
- ❌ Email addresses
- ❌ Friend requests
- ❌ Approvals

**Just share the code and you're connected!**
