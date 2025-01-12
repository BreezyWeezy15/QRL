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

    private lateinit var onPackageListener: OnPackageListener
    public fun interface  OnPackageListener {
        fun onPackageSelected(packages: String)
    }
    fun execute(onPackageListener: OnPackageListener){
        this.onPackageListener = onPackageListener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LockedAppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_item, parent, false)
        return LockedAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: LockedAppViewHolder, position: Int) {
        val lockedApp = lockedApps[position]

        holder.appNameTextView.text = lockedApp.appName
        val decodedByteArray: ByteArray = Base64.decode(lockedApp.appIcon, Base64.DEFAULT)
        val decodedBitmap: Bitmap = BitmapFactory.decodeByteArray(decodedByteArray, 0, decodedByteArray.size)
        holder.appIconImageView.setImageBitmap(decodedBitmap)

        holder.itemView.setOnClickListener {
           onPackageListener.onPackageSelected(lockedApp.packageName)
        }
    }

    override fun getItemCount(): Int {
        return lockedApps.size
    }

    class LockedAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIconImageView: ImageView = itemView.findViewById(R.id.appIcon)
        val appNameTextView: TextView = itemView.findViewById(R.id.appName)
    }


}
