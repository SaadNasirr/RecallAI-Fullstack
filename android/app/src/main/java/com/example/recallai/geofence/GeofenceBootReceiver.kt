package com.example.recallai.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.recallai.data.AuthManager

class GeofenceBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (AuthManager.userRole != "patient") return
        GeofenceRegistrationHelper.reregisterFromDisk(context.applicationContext)
    }
}
