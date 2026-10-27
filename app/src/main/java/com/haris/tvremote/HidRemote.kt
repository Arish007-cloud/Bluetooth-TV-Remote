package com.haris.tvremote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Makes the phone a Bluetooth keyboard + media remote (HID device profile).
 * Needs only Bluetooth: WiFi on or off makes no difference.
 *
 * Every call that touches Bluetooth assumes the runtime permissions were
 * already granted by MainActivity.
 */
@SuppressLint("MissingPermission")
class HidRemote(context: Context, private val listener: Listener) {

    interface Listener {
        fun onStatus(status: Status, device: BluetoothDevice?)
    }

    enum class Status { UNSUPPORTED, BLUETOOTH_OFF, STARTING, READY, CONNECTING, CONNECTED }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val prefs = appContext.getSharedPreferences("remote", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var hid: BluetoothHidDevice? = null
    private var registered = false
    private var pendingConnect: BluetoothDevice? = null

    var connectedDevice: BluetoothDevice? = null
        private set

    val isConnected get() = connectedDevice != null

    /** The last TV we were connected to, so the app reconnects by itself. */
    private var savedAddress: String?
        get() = prefs.getString("device", null)
        set(value) = prefs.edit().putString("device", value).apply()

    fun start() {
        val bt = adapter
        if (bt == null) return post(Status.UNSUPPORTED)
        if (!bt.isEnabled) return post(Status.BLUETOOTH_OFF)
        if (hid != null) return
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
        hid?.let {
            connectedDevice?.let { d -> it.disconnect(d) }
            it.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hid = null
        registered = false
        connectedDevice = null
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

    /** Connect to a TV. If it isn't paired yet, Android pairs it first. */
    fun connect(device: BluetoothDevice) {
        savedAddress = device.address
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

    fun disconnect() {
        savedAddress = null
        connectedDevice?.let { hid?.disconnect(it) }
    }

    fun bondedDevices(): List<BluetoothDevice> =
        adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()

    fun deviceName(device: BluetoothDevice): String = device.name ?: device.address

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

    fun tapKeyboard(usage: Int) {
        executor.execute {
            keyboardDown(usage)
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
            val target = pendingConnect ?: pluggedDevice
                ?: savedAddress?.let { addr -> bondedDevices().firstOrNull { it.address == addr } }
            pendingConnect = null
            if (target != null) {
                post(Status.CONNECTING, target)
                hid?.connect(target)
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedDevice = device
                    savedAddress = device.address
                    post(Status.CONNECTED, device)
                }
                BluetoothProfile.STATE_CONNECTING -> post(Status.CONNECTING, device)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedDevice?.address == device.address || connectedDevice == null) {
                        connectedDevice = null
                        post(Status.READY)
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
        main.post { listener.onStatus(status, device) }
    }
}
