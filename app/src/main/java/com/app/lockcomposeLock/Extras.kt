package com.app.lockcomposeLock

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object Extras {

    @SuppressLint("HardwareIds")
    fun generateDeviceID(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    fun savePackage(context: Context,packageName : String){
        val prefs = context.getSharedPreferences("prefs",Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("package",packageName)
        editor.apply()
    }

    fun getPackage(context: Context) : String {
        val prefs = context.getSharedPreferences("prefs",Context.MODE_PRIVATE)
        return prefs.getString("package","")!!
    }
}