package com.app.lockcomposeLock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.app.lockcomposeLock.LockScreenActivity

import com.app.lockcomposeLock.MainActivity
import com.app.lockcomposeLock.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class AppLockService : Service() {
    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "AppLockServiceChannel"
        private const val NOTIFICATION_ID = 1
        private const val PACKAGE_NAME_KEY = "package_name"
        private const val PIN_CODE_KEY = "pin_code"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val lockedApps = mutableSetOf<String>()
    private val appPinCodes = mutableMapOf<String, String>()
    private lateinit var database: DatabaseReference

    private val runnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("AppLockPrefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        database = FirebaseDatabase.getInstance().reference
        fetchLockedPackages()

        handler.post(runnable)
    }


    private fun fetchLockedPackages() {
        database.child("childApp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                updateLockedApps(dataSnapshot)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("FirebaseError", "Error fetching data: ${databaseError.message}")
            }
        })
    }

    private fun updateLockedApps(dataSnapshot: DataSnapshot) {
        appPinCodes.clear()
        lockedApps.clear()

        for (childSnapshot in dataSnapshot.children) {
            val packageName = childSnapshot.child(PACKAGE_NAME_KEY).getValue(String::class.java) ?: ""
            val pinCode = childSnapshot.child(PIN_CODE_KEY).getValue(String::class.java) ?: ""

            if (packageName.isNotEmpty() && pinCode.isNotEmpty()) {
                appPinCodes[packageName] = pinCode
                lockedApps.add(packageName)
            }
        }
        Log.d("AppLockService", "Updated locked apps: $lockedApps")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(runnable)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun checkForegroundApp() {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 1000, now)

        if (stats.isNotEmpty()) {
            val sortedStats = stats.sortedByDescending { it.lastTimeUsed }
            val currentApp = sortedStats.firstOrNull()?.packageName
            Log.d("AppLockService", "Current top app: $currentApp")

            if (currentApp != null && shouldLockApp(currentApp)) {
                showLockScreen(currentApp)
            }
        } else {
            Log.d("AppLockService", "No usage stats available.")
        }
    }

    private fun shouldLockApp(packageName: String): Boolean {
        return lockedApps.contains(packageName)
    }


    private fun showLockScreen(packageName: String) {
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra("PACKAGE_NAME", packageName)
            putExtra("PIN_CODE", appPinCodes[packageName]) // Pass the correct PIN code
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(lockIntent)
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App Lock Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("App Lock Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.baseline_lock_24)
            .setContentIntent(pendingIntent)
            .build()
    }
}