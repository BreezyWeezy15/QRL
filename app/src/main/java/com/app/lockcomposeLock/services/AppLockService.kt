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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.app.lockcomposeLock.LockScreenActivity
import com.app.lockcomposeLock.MainActivity
import com.app.lockcomposeLock.R
import com.app.lockcomposeLock.models.LockedApp
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
        private const val NAME_KEY = "name"
        private const val ICON_KEY = "icon"
        private const val PROFILE_TYPE_KEY = "profile_type"
        private const val INTERVAL_KEY = "interval"
    }

    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val lockedApps = mutableListOf<LockedApp>()
    private val appPinCodes = mutableMapOf<String, Int>()
    private var currentProfile = ""
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
                if (dataSnapshot.exists()) {
                    updateLockedApps(dataSnapshot)
                }
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
            val name = childSnapshot.child(NAME_KEY).getValue(String::class.java) ?: ""
            val icon = childSnapshot.child(ICON_KEY).getValue(String::class.java) ?: ""
            val profileName = childSnapshot.child(PROFILE_TYPE_KEY).getValue(String::class.java) ?: ""
            val interval = childSnapshot.child(INTERVAL_KEY).getValue(String::class.java) ?: ""
            removeOverlayTemporarily(interval.toInt())
            currentProfile = profileName


            if (profileName in listOf("Child", "Teen", "Pre-K") && packageName.isNotEmpty()) {
                lockedApps.add(LockedApp(name,packageName,icon,profileName))
            } else {
                if (pinCode.isNotEmpty() && pinCode != "0") {
                    appPinCodes[packageName] = interval.toInt()
                }
            }
        }
    }
    private fun navigateToLockScreen() {
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putParcelableArrayListExtra("LOCKED_APPS", ArrayList(lockedApps))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(lockIntent)
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


            val excludedPackages = listOf("com.app.lockcomposeLock", "com.app.lockcomposeChild")

            if (currentApp != null && currentApp !in excludedPackages) {

                if (appPinCodes.isNotEmpty() && appPinCodes.containsKey(currentApp)) {
                    if (currentApp == "com.android.settings") {
                        navigateToLockScreen()
                    } else {
                        showLockScreen(currentApp)
                    }

                }

                if (appPinCodes.isEmpty() && lockedApps.isNotEmpty() && currentApp !in excludedPackages) {
                    val isLockedApp = lockedApps.any { it.packageName.trim().lowercase() == currentApp.trim().lowercase() }
                    if (!isLockedApp) {
                        if (currentApp == "com.android.settings") {
                            navigateToLockScreen()
                        } else {
                            navigateToLockScreen()
                        }
                    }
                }
            }

        }

    }

    private fun removeOverlayTemporarily(interval : Int) {
        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this,"Triggered",Toast.LENGTH_SHORT).show()
            updatePermission()
            updateLayout()
        }, (interval * 60 * 1000).toLong()) // 5 minutes in milliseconds
    }

    private fun updateLayout(){
        val firebaseDatabase = FirebaseDatabase.getInstance().reference
        firebaseDatabase
            .child("Permissions")
            .addValueEventListener(object  : ValueEventListener {
                override fun onDataChange(dataSnapShot: DataSnapshot) {
                    if (dataSnapShot.exists()) {
                        val data = dataSnapShot.child("answer").getValue(String::class.java)
                        if (!data.isNullOrEmpty()) {
                            if (data == "No") {
                                showLockScreen(getPackage())
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }
            })
    }


    private fun updatePermission(){

        val map = hashMapOf<String,Any>()
        map["answer"] = "No"

        val firebaseDatabase = FirebaseDatabase.getInstance().reference
        firebaseDatabase
            .child("Permissions")
            .setValue(map)
            .addOnSuccessListener {

            }
            .addOnFailureListener {
                Log.d("TAG","Failed to send permission")
            }
    }

    private fun showLockScreen(packageName: String) {
        savePackage(packageName)
        val lockIntent = Intent(this, LockScreenActivity::class.java).apply {
            putExtra("PACKAGE_NAME", packageName)
            putExtra("INTERVAL", appPinCodes[packageName])
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

    private fun savePackage(packageName : String){
        val prefs = getSharedPreferences("prefs",Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("package",packageName)
        editor.apply()
    }
    private fun getPackage() : String {
        val prefs = getSharedPreferences("prefs",Context.MODE_PRIVATE)
        return prefs.getString("package","")!!
    }
}
