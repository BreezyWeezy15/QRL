package com.app.lockcomposeLock

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.lockcomposeLock.models.LockedApp
import com.app.lockcomposeLock.services.AppLockService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class LockScreenActivity : AppCompatActivity() {

    private var isFirstTime = true
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private lateinit var lockUi: FrameLayout
    private var excludedApps: List<LockedApp> = listOf()
    private lateinit var lockedAppsAdapter: LockedAppsAdapter
    private lateinit var windowManager: WindowManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var overlayView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        excludedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("LOCKED_APPS",LockedApp::class.java) ?: mutableListOf()
        } else {
            intent.getParcelableArrayListExtra("LOCKED_APPS") ?: mutableListOf()
        }

        triggerDatabase()
        setOverlayLayout()

    }


    private fun setOverlayLayout() {

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
        }

        if (excludedApps.isEmpty()) {
            setContentView(R.layout.widget_layout)
            lockUi = findViewById(R.id.lockUi)
            unlockScreen()
        } else {
            addOverlayView()
            setContentView(R.layout.profile_layout)
            setupProfileLayout()
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
        windowManager.addView(overlayView, windowParams)
    }

    private fun removeOverlayTemporarily() {
        windowManager.removeView(overlayView)
    }

    private fun setupProfileLayout() {
        val recyclerView = overlayView.findViewById<RecyclerView>(R.id.recyclerView)
        val bgImg = overlayView.findViewById<ImageView>(R.id.bgImg)

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
                            windowManager.removeView(overlayView)
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
        if (overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)
            Handler(Looper.getMainLooper()).postDelayed({
                monitorAppUsage(packageName)
            }, 1000)
        } else {
            Toast.makeText(this, "App not found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun monitorAppUsage(packageName: String) {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                val currentApp = getForegroundApp()
                if (currentApp != packageName) {
                    if (!overlayView.isAttachedToWindow) {
                        windowManager.addView(overlayView, windowParams)
                    }
                    handler?.removeCallbacks(this)
                } else {
                    handler?.postDelayed(this, 1000)
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
















///////////////////////////
//private fun addRemoveIcon(edit: EditText) {
//    val greenColor = ContextCompat.getColor(this, R.color.greenColor)
//    val colorFilter = PorterDuffColorFilter(greenColor, PorterDuff.Mode.SRC_IN)
//    edit.compoundDrawablesRelative[2]?.colorFilter = colorFilter
//}
//
//private fun removePackageFromFirebase(packageName: String) {
//    val firebaseDatabase = FirebaseDatabase.getInstance().reference
//    fun removeFromNode(nodeName: String) {
//        val nodeReference = firebaseDatabase.child(nodeName)
//        val query = nodeReference.orderByChild("package_name").equalTo(packageName)
//
//        query.addListenerForSingleValueEvent(object : ValueEventListener {
//            override fun onDataChange(snapshot: DataSnapshot) {
//                for (appSnapshot in snapshot.children) {
//                    appSnapshot.ref.removeValue()
//                    Log.d("Firebase", "Package removed: $packageName from $nodeName")
//                }
//                removeOverlay()
//            }
//
//            override fun onCancelled(error: DatabaseError) {
//                Log.e("FirebaseError", "Error removing package from $nodeName: ${error.message}")
//            }
//        })
//    }
//    removeFromNode("childApp")
//    removeFromNode("Apps")
//}