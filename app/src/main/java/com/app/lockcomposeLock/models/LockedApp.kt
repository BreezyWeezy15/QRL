package com.app.lockcomposeLock.models

import android.os.Parcel
import android.os.Parcelable

data class LockedApp(
    val appName: String,
    val packageName: String,
    val appIcon: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString().toString(),
        parcel.readString().toString(),
        parcel.readString().toString()
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(appName)
        parcel.writeString(packageName)
        parcel.writeString(appIcon)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LockedApp> {
        override fun createFromParcel(parcel: Parcel): LockedApp {
            return LockedApp(parcel)
        }

        override fun newArray(size: Int): Array<LockedApp?> {
            return arrayOfNulls(size)
        }
    }

}