package com.app.lockcomposeLock

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.app.lockcomposeLock.models.LockedApp

class LockedAppsAdapter(private val lockedApps: List<LockedApp>) : RecyclerView.Adapter<LockedAppsAdapter.LockedAppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LockedAppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false)
        return LockedAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: LockedAppViewHolder, position: Int) {
        val lockedApp = lockedApps[position]

        holder.appNameTextView.text = lockedApp.appName

        Log.d("TAGZ","VALUE " + lockedApp.appName)

        val decodedByteArray: ByteArray = Base64.decode(lockedApp.appIcon, Base64.DEFAULT)
        val decodedBitmap: Bitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.size)
        holder.appIconImageView.setImageBitmap(decodedBitmap)

        holder.itemView.setOnClickListener {
            openApp(holder.itemView.context,lockedApp.packageName)
        }
    }

    override fun getItemCount(): Int {
        return lockedApps.size
    }

    private fun openApp(context: Context,packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
        }
    }

    class LockedAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIconImageView: ImageView = itemView.findViewById(R.id.appIcon)
        val appNameTextView: TextView = itemView.findViewById(R.id.appName)
    }
}
