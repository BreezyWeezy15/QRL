package com.app.lockcomposeLock


import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

object AppLockHelper {

    private lateinit var database: DatabaseReference
    private val lockedApps = mutableMapOf<String, String>()

    fun initialize() {
        database = FirebaseDatabase.getInstance().reference
        fetchLockedPackages()
    }

    // Fetch locked apps and their pin codes from Firebase
    private fun fetchLockedPackages() {
        database.child("lockedApps").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                lockedApps.clear() // Clear previous data

                for (childSnapshot in dataSnapshot.children) {
                    val packageName = childSnapshot.child("package_name").getValue(String::class.java) ?: ""
                    val pinCode = childSnapshot.child("pin_code").getValue(String::class.java) ?: ""

                    if (packageName.isNotEmpty() && pinCode.isNotEmpty()) {
                        lockedApps[packageName] = pinCode // Store package name and PIN code
                    }
                }
                Log.d("AppLockHelper", "Updated locked apps: $lockedApps")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseError", "Error fetching data: ${databaseError.message}")
            }
        })
    }


    fun shouldLockApp(packageName: String): Boolean {
        return lockedApps.containsKey(packageName)
    }


    fun getPinCodeForApp(packageName: String): String? {
        return lockedApps[packageName]
    }
}