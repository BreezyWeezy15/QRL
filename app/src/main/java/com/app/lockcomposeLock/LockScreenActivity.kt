package com.app.lockcomposeLock

import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.lockcomposeLock.models.LockedApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.atomic.AtomicBoolean

class LockScreenActivity : AppCompatActivity() {

    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private var overlayViewAdded = AtomicBoolean(true) // To track overlay stat
    private lateinit var lockUi: LinearLayout
    private lateinit var askPermissionBtn: Button
    private var correctPinCode: String? = null
    private var excludedApps: List<LockedApp> = listOf()
    private lateinit var lockedAppsAdapter: LockedAppsAdapter
    private lateinit var bgImage: ImageView
    private lateinit var windowManager: WindowManager
    private lateinit var windowParams: WindowManager.LayoutParams
    private lateinit var overlayView: View
    private lateinit var closeLayoutIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        correctPinCode = intent.getStringExtra("PIN_CODE")
        excludedApps = intent.getParcelableArrayListExtra("LOCKED_APPS") ?: mutableListOf()

        setOverlayLayout()

    }

    private fun setOverlayLayout() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Inflate appropriate layout
        overlayView = LayoutInflater.from(this).inflate(
            if (excludedApps.isEmpty()) R.layout.widget_layout else R.layout.profile_layout, null
        )

        // Configure window parameters
        windowParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        // Add the overlay view
        windowManager.addView(overlayView, windowParams)

        if (excludedApps.isEmpty()) {
            setContentView(R.layout.widget_layout)
            lockUi = findViewById(R.id.lockUi)
            askPermissionBtn = findViewById(R.id.askPermission)
            showPassCodeUi()
        } else {
            // Initialize and show profile layout
            setContentView(R.layout.profile_layout)
            setupProfileLayout()
        }
    }


    private fun setupProfileLayout() {
        val recyclerView = overlayView.findViewById<RecyclerView>(R.id.recyclerView)
        val bgImg = overlayView.findViewById<ImageView>(R.id.bgImg)

        // Set overlay background
        when (excludedApps.getOrNull(0)?.profile) {
            "Child" -> bgImg.setBackgroundResource(R.drawable.image1)
            "Teen" -> bgImg.setBackgroundResource(R.drawable.image2)
            "Pre-K" -> bgImg.setBackgroundResource(R.drawable.image3)
        }

        // Configure RecyclerView
        recyclerView.layoutManager = GridLayoutManager(this, excludedApps.size)
        lockedAppsAdapter = LockedAppsAdapter(excludedApps)
        recyclerView.adapter = lockedAppsAdapter

        lockedAppsAdapter.execute { packageName ->
            openApp(packageName)
            monitorAppUsage(packageName)
        }
    }

    private fun openApp(packageName: String) {
        // Remove overlay before opening the app
        if (overlayView.isAttachedToWindow) {
            windowManager.removeView(overlayView)
        }

        // Launch the app
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            startActivity(intent)

            // Delay to ensure the app opens fully before reattaching the overlay
            Handler(Looper.getMainLooper()).postDelayed({
                monitorAppUsage(packageName)
            }, 1000) // Adjust delay as needed
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
                    // Check if overlay is already attached
                    if (!overlayView.isAttachedToWindow) {
                        windowManager.addView(overlayView, windowParams)
                    }
                    handler?.removeCallbacks(this) // Stop monitoring after reattaching the overlay
                } else {
                    handler?.postDelayed(this, 1000) // Check again in 1 second
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
        try {
            if (::overlayView.isInitialized) {
                windowManager.removeView(overlayView)
            }
        } catch (e: Exception) {
            Log.e("WindowManager", "Error removing overlay: ${e.message}")
        }
    }



    @SuppressLint("ClickableViewAccessibility")
    private fun showPassCodeUi() {
        lockUi.visibility = View.VISIBLE
        askPermissionBtn.visibility = View.GONE

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
            if (enteredPasscode == correctPinCode) {
                edit.text.clear()
                removePackageFromFirebase(intent.getStringExtra("PACKAGE_NAME") ?: "")
                removeOverlay()
            } else {
                Toast.makeText(this, "Passcode is incorrect", Toast.LENGTH_LONG).show()
            }
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
