package com.example.ontheway.utils

import android.content.Context
import android.net.Uri

fun getContactEmail(context: Context, contactUri: Uri): String? {
    val projection = arrayOf(
        android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS
    )
    
    try {
        context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            "${android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ?",
            arrayOf(contactUri.lastPathSegment),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val emailIndex = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS
                )
                if (emailIndex >= 0) {
                    return cursor.getString(emailIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return null
}

fun getContactPhone(context: Context, contactUri: Uri): String? {
    val projection = arrayOf(
        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
    )
    
    try {
        context.contentResolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            "${android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactUri.lastPathSegment),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val phoneIndex = cursor.getColumnIndex(
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                if (phoneIndex >= 0) {
                    return cursor.getString(phoneIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return null
}
