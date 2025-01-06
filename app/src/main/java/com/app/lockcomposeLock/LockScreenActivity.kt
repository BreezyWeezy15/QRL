package com.app.lockcomposeLock

import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.lockcomposeLock.models.LockedApp
import com.app.lockcomposeLock.services.AppLockService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicBoolean

class LockScreenActivity : AppCompatActivity() {

    private var isFirstTime = true
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private lateinit var lockUi: LinearLayout
    private var correctPinCode: Int? = null
    private var excludedApps: List<LockedApp> = listOf()
    private lateinit var lockedAppsAdapter: LockedAppsAdapter
    private lateinit var windowManager: WindowManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var overlayView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        correctPinCode = intent.getIntExtra("INTERVAL",0)
        excludedApps = intent.getParcelableArrayListExtra("LOCKED_APPS") ?: mutableListOf()

        triggerDatabase()
        setOverlayLayout()

    }

    @SuppressLint("ClickableViewAccessibility")
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
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        if (excludedApps.isEmpty()) {
            setContentView(R.layout.widget_layout)
            lockUi = findViewById(R.id.lockUi)

            val firebaseDatabase = FirebaseDatabase.getInstance().reference
            firebaseDatabase
                .child("Permissions")
                .addValueEventListener(object  : ValueEventListener {
                    override fun onDataChange(dataSnapShot: DataSnapshot) {
                        if (dataSnapShot.exists()) {
                            val data = dataSnapShot.child("answer").getValue(String::class.java)
                            if (!data.isNullOrEmpty()) {
                                if (data == "Yes") {
                                    removeOverlayTemporarily(correctPinCode!!)
                                } else {
                                    addOverlayView()
                                    showPassCodeUi()
                                }
                            } else {
                                addOverlayView()
                                showPassCodeUi()
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {

                    }

                })
        } else {
            setContentView(R.layout.profile_layout)
            setupProfileLayout()
        }
    }

    private fun addOverlayView() {
        if (overlayView.parent == null) {
            windowManager.addView(overlayView, windowParams)
        }
    }

    private fun removeOverlayTemporarily(interval : Int) {
        windowManager.removeView(overlayView)

        Handler(Looper.getMainLooper()).postDelayed({
            Toast.makeText(this,"Triggered",Toast.LENGTH_SHORT).show()
            updatePermission()
            addOverlayView()
        }, (interval * 60 * 1000).toLong()) // 5 minutes in milliseconds
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
    @SuppressLint("ClickableViewAccessibility")
    private fun showPassCodeUi() {

        val btn0 = findViewById<TextView>(R.id.btn0)
        val btn1 = findViewById<TextView>(R.id.btn1)
        val btn2 = findViewById<TextView>(R.id.btn2)
        val btn3 = findViewById<TextView>(R.id.btn3)
        val btn4 = findViewById<TextView>(R.id.btn4)
        val btn5 = findViewById<TextView>(R.id.btn5)
        val btn6 = findViewById<TextView>(R.id.btn6)
        val btn7 = findViewById<TextView>(R.id.btn7)
        val btn8 = findViewById<TextView>(R.id.btn8)
        val btn9 = findViewById<TextView>(R.id.btn9)
        val tick = findViewById<ImageView>(R.id.tick)
        val edit = findViewById<EditText>(R.id.passCodeEdit)

        val passcodeBuilder = StringBuilder()
        val numberButtons = listOf(btn0, btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9)

        tick.setOnClickListener {
            val enteredPasscode = passcodeBuilder.toString()
//            if (enteredPasscode == correctPinCode) {
//                edit.text.clear()
//                removePackageFromFirebase(intent.getStringExtra("PACKAGE_NAME") ?: "")
//
//            } else {
//                Toast.makeText(this, "Passcode is incorrect", Toast.LENGTH_LONG).show()
//            }
        }

        numberButtons.forEach { button ->
            button.setOnClickListener {
                passcodeBuilder.append(button.text)
                edit.setText(passcodeBuilder.toString())
            }
        }

        addRemoveIcon(edit)
        edit.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val drawableEnd = edit.compoundDrawablesRelative[2]
                if (drawableEnd != null && event.rawX >= edit.right - drawableEnd.bounds.width()) {
                    if (passcodeBuilder.isNotEmpty()) {
                        passcodeBuilder.deleteCharAt(passcodeBuilder.length - 1)
                        edit.setText(passcodeBuilder.toString())
                    }
                    return@setOnTouchListener true
                }
            }
            false
        }
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

        // Launch the app
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
    private fun removeOverlay() {
        windowManager.removeView(overlayView)
    }

    private fun addRemoveIcon(edit: EditText) {
        val greenColor = ContextCompat.getColor(this, R.color.greenColor)
        val colorFilter = PorterDuffColorFilter(greenColor, PorterDuff.Mode.SRC_IN)
        edit.compoundDrawablesRelative[2]?.colorFilter = colorFilter
    }

    private fun removePackageFromFirebase(packageName: String) {
        val firebaseDatabase = FirebaseDatabase.getInstance().reference
        fun removeFromNode(nodeName: String) {
            val nodeReference = firebaseDatabase.child(nodeName)
            val query = nodeReference.orderByChild("package_name").equalTo(packageName)

            query.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (appSnapshot in snapshot.children) {
                        appSnapshot.ref.removeValue()
                        Log.d("Firebase", "Package removed: $packageName from $nodeName")
                    }
                    removeOverlay()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseError", "Error removing package from $nodeName: ${error.message}")
                }
            })
        }
        removeFromNode("childApp")
        removeFromNode("Apps")
    }

}
