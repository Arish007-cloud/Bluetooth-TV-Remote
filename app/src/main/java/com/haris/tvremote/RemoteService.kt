package com.haris.tvremote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Keeps the Bluetooth remote alive while the app is in the background, so
 * the TV stays connected and reconnects by itself after standby or a power
 * cut. Shows a small ongoing notification with the connection state.
 */
class RemoteService : Service(), HidRemote.Listener {

    companion object {
        private const val CHANNEL = "remote"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.haris.tvremote.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RemoteService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    private lateinit var remote: HidRemote

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)!!
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "TV connection", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = buildNotification("Starting…")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        remote = HidRemote.get(this)
        remote.addListener(this)
        remote.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            remote.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        remote.start()
        return START_STICKY
    }

    override fun onDestroy() {
        remote.removeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStatus(status: HidRemote.Status, device: BluetoothDevice?) {
        val name = device?.let { remote.deviceName(it) } ?: "TV"
        val text = when (status) {
            HidRemote.Status.CONNECTED -> "Connected to $name"
            HidRemote.Status.CONNECTING -> "Waiting for $name to turn on…"
            HidRemote.Status.BLUETOOTH_OFF -> "Bluetooth is off"
            HidRemote.Status.UNSUPPORTED -> "Bluetooth remote mode not available"
            else -> "Not connected"
        }
        getSystemService(NotificationManager::class.java)!!.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RemoteService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("TV Remote")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }
}
