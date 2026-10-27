package com.haris.tvremote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Starts the remote after the phone restarts, so it reconnects to the TV without opening the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val saved = context.getSharedPreferences("remote", Context.MODE_PRIVATE).getString("device", null)
        if (saved != null) runCatching { RemoteService.start(context) }
    }
}
