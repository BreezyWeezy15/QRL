package com.app.lockcomposeLock



import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.lockcomposeLock.models.LockedApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

import com.google.firebase.database.*

class LockScreenActivity : AppCompatActivity() {

    private lateinit var lockUi: LinearLayout
    private lateinit var askPermissionBtn: Button
    private var correctPinCode: String? = null
    private lateinit var firebaseDatabase: DatabaseReference
    private var excludedApps: List<LockedApp> = listOf()
    private lateinit var lockedAppsRecyclerView: RecyclerView
    private lateinit var lockedAppsAdapter: LockedAppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        lockUi = findViewById(R.id.lockUi)
        askPermissionBtn = findViewById(R.id.askPermission)
        lockedAppsRecyclerView = findViewById(R.id.recyclerView)


        correctPinCode = intent.getStringExtra("PIN_CODE")
        excludedApps = intent.getParcelableArrayListExtra("LOCKED_APPS") ?: mutableListOf()


        lockedAppsAdapter = LockedAppsAdapter(excludedApps)
        lockedAppsRecyclerView.layoutManager = LinearLayoutManager(this)
        lockedAppsRecyclerView.adapter = lockedAppsAdapter


        if (excludedApps.isNotEmpty()) {
            Toast.makeText(this,"Size is " + excludedApps.size,Toast.LENGTH_LONG).show()
            showExcludedAppsUI()
        } else {
            // Show PIN input UI
            showPassCodeUi()
        }
    }

    private fun showExcludedAppsUI() {
        lockedAppsRecyclerView.visibility = View.VISIBLE
        lockUi.visibility = View.GONE
        askPermissionBtn.visibility = View.GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showPassCodeUi() {
        lockUi.visibility = View.VISIBLE
        lockedAppsRecyclerView.visibility = View.GONE
        askPermissionBtn.visibility = View.GONE

        // Set up buttons for the passcode UI
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
                finishAffinity()
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
        edit.setOnTouchListener { v, event ->
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
