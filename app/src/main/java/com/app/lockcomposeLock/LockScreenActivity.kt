package com.app.lockcomposeLock

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.lockcomposeLock.models.LockedApp
import com.app.lockcomposeLock.services.AppLockService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LockScreenActivity : AppCompatActivity() {

    private var shuffledID = 0L
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var lockUi: FrameLayout
    private lateinit var windowParams: WindowManager.LayoutParams
    private var excludedApps: List<LockedApp> = listOf()
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private lateinit var lockedAppsAdapter: LockedAppsAdapter
    private var isFirstTime = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        excludedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("LOCKED_APPS", LockedApp::class.java) ?: mutableListOf()
        } else {
            intent.getParcelableArrayListExtra("LOCKED_APPS") ?: mutableListOf()
        }

        triggerDatabase()
        setOverlayLayout()
        openChildApp()
    }


    private fun openChildApp() {
        // Get a reference to the Firebase database
        FirebaseDatabase.getInstance().getReference("Profiles")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {

                        val randomID = snapshot.child("randomID").getValue(Long::class.java) ?: 0L

                        if (shuffledID != randomID) {
                            openApp("com.app.lockcomposeChild")
                            monitorAppUsage("com.app.lockcomposeChild")
                            shuffledID = randomID
                            updateProfile()
                        }
                    } else {
                        Log.w("openChildApp", "No data found in 'Profiles'.")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("openChildApp", "Database error: ${error.message}")
                }
            })
    }


    private fun updateProfile(){
        FirebaseDatabase.getInstance().getReference()
            .child("Profiles")
            .removeValue()
    }


    private fun setOverlayLayout() {

        overlayView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(overlayView)
                overlayView = null
            }
        }


        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        overlayView = LayoutInflater.from(this).inflate(
            if (excludedApps.isEmpty()) R.layout.widget_layout else R.layout.profile_layout, null
        )

        windowParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        if (excludedApps.isEmpty()) {
            lockUi = overlayView!!.findViewById(R.id.lockUi)
            unlockScreen()
        } else {
            setupProfileLayout()
            addOverlayView()
        }

    }

    private fun unlockScreen() {
        val firebaseDatabase = FirebaseDatabase.getInstance().reference
        firebaseDatabase
            .child("Permissions")
            .child(Extras.generateDeviceID(this))
            .addListenerForSingleValueEvent(object  : ValueEventListener {
                override fun onDataChange(dataSnapShot: DataSnapshot) {
                    if (dataSnapShot.exists()) {
                        val data = dataSnapShot.child("answer").getValue(String::class.java)
                        if (!data.isNullOrEmpty()) {
                            if (data == "Yes") {
                                Toast.makeText(this@LockScreenActivity,"REMOVE OVERLAY VIEW",Toast.LENGTH_SHORT).show()
                                removeOverlayTemporarily()
                            } else {
                                Toast.makeText(this@LockScreenActivity,"SHOWING PASS CODE",Toast.LENGTH_SHORT).show()
                                addOverlayView()
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {

                }

            })
    }

    private fun addOverlayView() {
        if(!overlayView!!.isAttachedToWindow){
            windowManager.addView(overlayView, windowParams)
        }
    }

    private fun removeOverlayTemporarily() {
        windowManager.removeView(overlayView)
    }

    private fun setupProfileLayout() {

        val recyclerView = overlayView!!.findViewById<RecyclerView>(R.id.recyclerView)
        val bgImg = overlayView!!.findViewById<ImageView>(R.id.bgImg)

        when (excludedApps.getOrNull(0)?.profile) {
            "Child" -> bgImg.setBackgroundResource(R.drawable.image1)
            "Teen" -> bgImg.setBackgroundResource(R.drawable.image2)
            "Pre-K" -> bgImg.setBackgroundResource(R.drawable.image3)
        }

        recyclerView.layoutManager = GridLayoutManager(this, excludedApps.size)
        lockedAppsAdapter = LockedAppsAdapter(excludedApps)
        recyclerView.adapter = lockedAppsAdapter

        lockedAppsAdapter.execute { packageName ->
            openApp(packageName)
            monitorAppUsage(packageName)
        }
    }

    private fun triggerDatabase() {
        FirebaseDatabase.getInstance().reference.child("childApp")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    if (dataSnapshot.exists()) {
                        if (isFirstTime) {
                            isFirstTime = false
                        } else {
                            if(overlayView!!.isAttachedToWindow){
                                windowManager.removeView(overlayView)
                            }
                            val serviceIntent = Intent(this@LockScreenActivity, AppLockService::class.java)
                            stopService(serviceIntent)
                            startService(serviceIntent)
                            onBackPressedDispatcher.onBackPressed()
                            if(intent.getStringExtra("PACKAGE_NAME") != null){
                                closeApp(intent.getStringExtra("PACKAGE_NAME")!!)
                            }
                        }
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e("FirebaseError", "Error fetching data: ${databaseError.message}")
                }
            })
    }

    private fun closeApp(packageName: String) {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.killBackgroundProcesses(packageName)
    }

    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            if (overlayView!!.isAttachedToWindow) {
                windowManager.removeView(overlayView)
            }
            startActivity(intent)
            monitorAppUsage(packageName)
        } else {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun monitorAppUsage(packageName: String) {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                val currentApp = getForegroundApp()
                if (currentApp == packageName) {
                    val serviceIntent = Intent(this@LockScreenActivity, AppLockService::class.java)
                    stopService(serviceIntent)
                    startService(serviceIntent)
                    finish()
                } else {
                    handler?.postDelayed(this, 300)
                }
            }
        }
        handler?.post(runnable!!)
    }


    private fun getForegroundApp(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            currentTime - 1000 * 60,
            currentTime
        )
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }



    override fun onDestroy() {
        super.onDestroy()
        handler?.removeCallbacks(runnable!!)
    }
}
