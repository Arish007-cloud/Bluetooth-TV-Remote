package com.haris.tvremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Makes the phone a Bluetooth keyboard + media remote (HID device profile).
 * Needs only Bluetooth: WiFi on or off makes no difference.
 *
 * Once a TV has been connected, it is remembered and the remote keeps trying
 * to reconnect whenever the link drops (TV in standby, unplugged and plugged
 * back in, out of range), until the user disconnects on purpose.
 *
 * One shared instance ([get]) is kept alive by [RemoteService], so the link
 * survives the app going to the background. Every call that touches
 * Bluetooth assumes the runtime permissions were already granted.
 */
@SuppressLint("MissingPermission")
class HidRemote private constructor(context: Context) {

    interface Listener {
        fun onStatus(status: Status, device: BluetoothDevice?)
    }

    enum class Status { UNSUPPORTED, BLUETOOTH_OFF, STARTING, READY, CONNECTING, CONNECTED }

    companion object {
        /** Seconds between reconnect attempts while the TV is away: quick at first, then every 15 s. */
        private val RETRY_DELAYS_MS = longArrayOf(2_000, 4_000, 8_000, 15_000)

        @Volatile private var instance: HidRemote? = null

        fun get(context: Context): HidRemote =
            instance ?: synchronized(this) {
                instance ?: HidRemote(context).also { instance = it }
            }
    }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val prefs = appContext.getSharedPreferences("remote", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val listeners = mutableSetOf<Listener>()

    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var pendingConnect: BluetoothDevice? = null
    private var retryCount = 0
    private var stateReceiverRegistered = false

    var status = Status.STARTING
        private set
    var statusDevice: BluetoothDevice? = null
        private set

    var connectedDevice: BluetoothDevice? = null
        private set

    val isConnected get() = connectedDevice != null

    /** The TV to stay connected to. Cleared only when the user disconnects. */
    private var savedAddress: String?
        get() = prefs.getString("device", null)
        set(value) = prefs.edit().putString("device", value).apply()

    val savedDevice: BluetoothDevice?
        get() = savedAddress?.let { addr -> runCatching { adapter?.getRemoteDevice(addr) }.getOrNull() }

    fun addListener(l: Listener) {
        listeners.add(l)
        l.onStatus(status, statusDevice)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    fun start() {
        watchBluetoothState()
        val bt = adapter
        if (bt == null) return post(Status.UNSUPPORTED)
        if (!bt.isEnabled) return post(Status.BLUETOOTH_OFF)
        if (hid != null) {
            if (registered && !isConnected) reconnectNow()
            return
        }
        post(Status.STARTING)
        val ok = bt.getProfileProxy(appContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                hid = proxy as BluetoothHidDevice
                register()
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null
                registered = false
                connectedDevice = null
                post(Status.STARTING)
            }
        }, BluetoothProfile.HID_DEVICE)
        if (!ok) post(Status.UNSUPPORTED)
    }

    fun stop() {
        cancelRetry()
        hid?.let {
            connectedDevice?.let { d -> it.disconnect(d) }
            it.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hid = null
        registered = false
        connectedDevice = null
        if (stateReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(bluetoothStateReceiver) }
            stateReceiverRegistered = false
        }
    }

    private fun register() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "TV Remote",
            "Phone TV remote",
            "Haris",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            HidKeys.DESCRIPTOR
        )
        val ok = hid?.registerApp(sdp, null, null, executor, callback) ?: false
        if (!ok) post(Status.UNSUPPORTED)
    }

    /** Connect to a TV and remember it. If it isn't paired yet, Android pairs it first. */
    fun connect(device: BluetoothDevice) {
        savedAddress = device.address
        retryCount = 0
        cancelRetry()
        adapter?.cancelDiscovery()
        val h = hid
        if (h == null || !registered) {
            pendingConnect = device
            start()
            return
        }
        connectedDevice?.takeIf { it.address != device.address }?.let { h.disconnect(it) }
        post(Status.CONNECTING, device)
        h.connect(device)
    }

    /** Disconnect on purpose: forget the TV so the remote stops reconnecting. */
    fun disconnect() {
        savedAddress = null
        cancelRetry()
        connectedDevice?.let { hid?.disconnect(it) }
    }

    fun bondedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()

    fun deviceName(device: BluetoothDevice): String = device.name ?: device.address

    // --- Staying connected ---

    private val retryRunnable = Runnable { reconnectNow() }

    private fun reconnectNow() {
        val h = hid ?: return
        val target = savedDevice ?: return
        if (!registered || isConnected) return
        post(Status.CONNECTING, target)
        h.connect(target)
        // If this attempt doesn't connect, try again later.
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (savedAddress == null) return
        main.removeCallbacks(retryRunnable)
        val delay = RETRY_DELAYS_MS[minOf(retryCount, RETRY_DELAYS_MS.size - 1)]
        retryCount++
        main.postDelayed(retryRunnable, delay)
    }

    private fun cancelRetry() {
        main.removeCallbacks(retryRunnable)
    }

    /** Restart cleanly when the phone's Bluetooth is turned off and on again. */
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_ON -> {
                    hid = null
                    registered = false
                    start()
                }
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                    cancelRetry()
                    connectedDevice = null
                    post(Status.BLUETOOTH_OFF)
                }
            }
        }
    }

    private fun watchBluetoothState() {
        if (stateReceiverRegistered) return
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(bluetoothStateReceiver, filter)
        }
        stateReceiverRegistered = true
    }

    // --- Sending keys ---

    fun keyboardDown(usage: Int, modifiers: Int = 0) =
        send(HidKeys.REPORT_KEYBOARD, byteArrayOf(modifiers.toByte(), 0, usage.toByte(), 0, 0, 0, 0, 0))

    fun keyboardUp() = send(HidKeys.REPORT_KEYBOARD, ByteArray(8))

    fun consumerDown(usage: Int) =
        send(HidKeys.REPORT_CONSUMER, byteArrayOf((usage and 0xFF).toByte(), (usage shr 8).toByte()))

    fun consumerUp() = send(HidKeys.REPORT_CONSUMER, ByteArray(2))

    /** Types text on the TV, one key press per character. */
    fun type(text: String) {
        executor.execute {
            for (c in text) {
                val (usage, mod) = HidKeys.charToKey(c) ?: continue
                keyboardDown(usage, mod)
                Thread.sleep(15)
                keyboardUp()
                Thread.sleep(15)
            }
        }
    }

    fun tapKeyboard(usage: Int, modifiers: Int = 0) {
        executor.execute {
            keyboardDown(usage, modifiers)
            Thread.sleep(15)
            keyboardUp()
        }
    }

    private fun send(reportId: Int, data: ByteArray): Boolean {
        val d = connectedDevice ?: return false
        return hid?.sendReport(d, reportId, data) ?: false
    }

    // --- Callbacks from the Bluetooth stack (on our executor thread) ---

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            this@HidRemote.registered = registered
            if (!registered) {
                connectedDevice = null
                post(Status.STARTING)
                return
            }
            post(Status.READY)
            val target = pendingConnect ?: pluggedDevice ?: savedDevice
            pendingConnect = null
            if (target != null) {
                main.post {
                    savedAddress = target.address
                    retryCount = 0
                    reconnectNow()
                }
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    savedAddress = device.address
                    main.post {
                        retryCount = 0
                        cancelRetry()
                    }
                    post(Status.CONNECTED, device)
                }
                BluetoothProfile.STATE_CONNECTING -> post(Status.CONNECTING, device)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address || connectedDevice == null) {
                        connectedDevice = null
                        if (savedAddress == device.address) {
                            // The TV went away (standby, power cut): keep trying until it's back.
                            post(Status.CONNECTING, device)
                            main.post {
                                retryCount = 0
                                scheduleRetry()
                            }
                        } else {
                            post(Status.READY)
                        }
                    }
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = if (id.toInt() == HidKeys.REPORT_CONSUMER) 2 else 8
            hid?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }
    }

    private fun post(status: Status, device: BluetoothDevice? = null) {
        main.post {
            this.status = status
            this.statusDevice = device
            listeners.toList().forEach { it.onStatus(status, device) }
        }
    }
}
