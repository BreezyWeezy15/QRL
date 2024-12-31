package com.app.lockcomposeLock

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.lockcomposeLock.models.LockedApp
import com.google.firebase.database.*

class LockScreenActivity : AppCompatActivity() {

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
        overlayView = LayoutInflater.from(this).inflate(
            if (excludedApps.isEmpty()) R.layout.widget_layout else R.layout.profile_layout, null
        )

        windowParams = WindowManager.LayoutParams()
        windowParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        windowParams.format = PixelFormat.TRANSLUCENT
        windowParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParams.height = WindowManager.LayoutParams.MATCH_PARENT


        if (excludedApps.isEmpty()) {
            setContentView(R.layout.widget_layout)
            lockUi = findViewById(R.id.lockUi)
            askPermissionBtn = findViewById(R.id.askPermission)
            showPassCodeUi()
        } else {
            setContentView(R.layout.profile_layout)
            bgImage = findViewById(R.id.bgImg)
            showProfileLayout()
        }
    }

    private fun dismissOverlay() {
        try {
            removeOverlay()
            finishAffinity()
        } catch (e: Exception) {
            Log.e("Overlay", "Error dismissing overlay: ${e.message}")
        }
    }

    private fun removeOverlay() {
        try {
            windowManager.removeView(overlayView)
        } catch (e: Exception) {
            Log.e("WindowManager", "Error removing overlay: ${e.message}")
        }
    }

    private fun showProfileLayout() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        val bgImg = findViewById<ImageView>(R.id.bgImg)

        when (excludedApps.getOrNull(0)?.profile) {
            "Child" -> bgImg.setImageResource(R.drawable.image1)
            "Teen" -> bgImg.setImageResource(R.drawable.image2)
            "Pre-K" -> bgImg.setImageResource(R.drawable.image3)
        }

        lockedAppsAdapter = LockedAppsAdapter(excludedApps)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = lockedAppsAdapter

        lockedAppsAdapter.notifyDataSetChanged()
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
                dismissOverlay()
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
