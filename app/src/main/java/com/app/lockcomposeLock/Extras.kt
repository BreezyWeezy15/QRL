package com.app.lockcomposeLock

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object Extras {

    @SuppressLint("HardwareIds")
    fun generateDeviceID(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }
}