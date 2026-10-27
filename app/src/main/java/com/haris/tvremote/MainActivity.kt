package com.haris.tvremote

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The remote screen. Layout follows the Haier Google TV remote; every key is
 * sent over Bluetooth, so the remote works the same with WiFi on or off.
 */
@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : Activity(), HidRemote.Listener {

    private lateinit var remote: HidRemote
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var connectButton: Button
    private lateinit var typingPanel: LinearLayout
    private lateinit var typingInput: EditText
    private var status = HidRemote.Status.STARTING
    private var lastNotConnectedToast = 0L

    private val bluetoothAdapter: BluetoothAdapter?
        get() = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = BACKGROUND
        window.navigationBarColor = BACKGROUND
        remote = HidRemote(this, this)
        setContentView(buildScreen())
        startWhenAllowed()
    }

    override fun onDestroy() {
        remote.stop()
        super.onDestroy()
    }

    // --- Permissions and Bluetooth on/off ---

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPermissions() = neededPermissions().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startWhenAllowed() {
        if (!hasPermissions()) {
            requestPermissions(neededPermissions(), REQUEST_PERMISSIONS)
            return
        }
        val bt = bluetoothAdapter
        if (bt != null && !bt.isEnabled) {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
            return
        }
        remote.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != REQUEST_PERMISSIONS) return
        if (hasPermissions()) {
            startWhenAllowed()
        } else {
            showStatus(false, "Allow Nearby devices permission so the remote can use Bluetooth.")
            connectButton.text = "Allow"
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (bluetoothAdapter?.isEnabled == true) remote.start() else onStatus(HidRemote.Status.BLUETOOTH_OFF, null)
        }
    }

    // --- Status ---

    override fun onStatus(status: HidRemote.Status, device: BluetoothDevice?) {
        this.status = status
        val name = device?.let { remote.deviceName(it) } ?: "TV"
        when (status) {
            HidRemote.Status.UNSUPPORTED ->
                showStatus(false, "This phone doesn't allow Bluetooth remote mode.")
            HidRemote.Status.BLUETOOTH_OFF -> showStatus(false, "Bluetooth is off")
            HidRemote.Status.STARTING -> showStatus(false, "Starting Bluetooth…")
            HidRemote.Status.READY -> showStatus(false, "Not connected")
            HidRemote.Status.CONNECTING -> showStatus(false, "Connecting to $name…")
            HidRemote.Status.CONNECTED -> showStatus(true, "Connected to $name")
        }
        connectButton.text = when (status) {
            HidRemote.Status.BLUETOOTH_OFF -> "Turn on"
            HidRemote.Status.CONNECTED -> "Change"
            else -> "Connect"
        }
        connectButton.isEnabled = status != HidRemote.Status.UNSUPPORTED
    }

    private fun showStatus(ok: Boolean, text: String) {
        statusText.text = text
        (statusDot.background as GradientDrawable).setColor(if (ok) Color.rgb(0x34, 0xC7, 0x59) else Color.rgb(0x8E, 0x8E, 0x93))
    }

    private fun onConnectClicked() {
        when {
            !hasPermissions() -> startWhenAllowed()
            status == HidRemote.Status.BLUETOOTH_OFF -> startWhenAllowed()
            else -> showDevicePicker()
        }
    }

    private fun checkConnected(): Boolean {
        if (remote.isConnected) return true
        val now = System.currentTimeMillis()
        if (now - lastNotConnectedToast > 2000) {
            lastNotConnectedToast = now
            Toast.makeText(this, "Not connected. Tap Connect first.", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    // --- Choosing the TV ---

    private fun showDevicePicker() {
        val bt = bluetoothAdapter ?: return
        val devices = remote.bondedDevices().toMutableList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, devices.map { label(it) }.toMutableList())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                } ?: return
                if (device.name == null || devices.any { it.address == device.address }) return
                devices.add(device)
                adapter.add(label(device))
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, filter)
        bt.startDiscovery()

        val title = TextView(this).apply {
            text = "Choose your TV\n\nOn the TV, open Settings › Remotes & Accessories › Pair accessory. " +
                "Then pick the TV below. New TVs appear as they're found."
            setPadding(dp(24), dp(20), dp(24), dp(8))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }

        val builder = AlertDialog.Builder(this)
            .setCustomTitle(title)
            .setAdapter(adapter) { _, which -> remote.connect(devices[which]) }
            .setNeutralButton("Make phone visible") { _, _ -> makeDiscoverable() }
        if (remote.isConnected) {
            builder.setNegativeButton("Disconnect") { _, _ -> remote.disconnect() }
        } else {
            builder.setNegativeButton("Cancel", null)
        }
        builder.setOnDismissListener {
            bt.cancelDiscovery()
            runCatching { unregisterReceiver(receiver) }
        }
        builder.show()
    }

    private fun label(device: BluetoothDevice): String {
        val paired = device.bondState == BluetoothDevice.BOND_BONDED
        return remote.deviceName(device) + if (paired) "" else "   (new)"
    }

    /** Lets the TV find the phone from its own "Pair accessory" screen. */
    private fun makeDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_DISCOVERABLE)
        Toast.makeText(this, "Now pick this phone on the TV's Pair accessory screen.", Toast.LENGTH_LONG).show()
    }

    // --- Phone volume buttons control the TV volume while connected ---

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (remote.isConnected && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            if (event.repeatCount == 0) {
                remote.consumerDown(if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) HidKeys.VOLUME_UP else HidKeys.VOLUME_DOWN)
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (remote.isConnected && (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            remote.consumerUp()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // --- Screen layout ---

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BACKGROUND)
            fitsSystemWindows = true
        }
        root.addView(buildStatusBar())

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val holder = FrameLayout(this).apply { setPadding(0, dp(8), 0, dp(24)) }
        holder.addView(buildRemoteBody(), FrameLayout.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
        scroll.addView(holder)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        typingPanel = buildTypingPanel().apply { visibility = View.GONE }
        root.addView(typingPanel)
        return root
    }

    private fun buildStatusBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(16), dp(10))
        }
        statusDot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.GRAY) }
        }
        bar.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { rightMargin = dp(10) })
        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            text = "Starting Bluetooth…"
        }
        bar.addView(statusText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        connectButton = Button(this).apply {
            text = "Connect"
            isAllCaps = false
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply { cornerRadius = dp(18).toFloat(); setColor(Color.WHITE) }
            setPadding(dp(18), 0, dp(18), 0)
            minHeight = 0
            minimumHeight = 0
            setOnClickListener { onConnectClicked() }
        }
        bar.addView(connectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)))
        return bar
    }

    private fun buildRemoteBody(): View {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(26), dp(18), dp(30))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(0xE4, 0xE5, 0xE9), Palette.BODY)
            ).apply { cornerRadius = dp(56).toFloat() }
        }

        body.addView(row(consumer(Icon.POWER, HidKeys.POWER, dark = true)))
        body.addView(row(
            consumer(Icon.ASSISTANT, HidKeys.ASSISTANT),
            consumer(Icon.MENU, HidKeys.MENU),
            consumer(Icon.TEXT, HidKeys.AUDIO_TRACK, label = "AUDIO")
        ))
        body.addView(row(
            consumer(Icon.PROFILE, HidKeys.PROFILE),
            consumer(Icon.MIC, HidKeys.VOICE, dark = true),
            consumer(Icon.SETTINGS, HidKeys.SETTINGS)
        ))

        val dpad = DPadView(this).apply {
            onPress = { zone ->
                if (checkConnected()) {
                    remote.keyboardDown(
                        when (zone) {
                            DPadView.Zone.UP -> HidKeys.KEY_UP
                            DPadView.Zone.DOWN -> HidKeys.KEY_DOWN
                            DPadView.Zone.LEFT -> HidKeys.KEY_LEFT
                            DPadView.Zone.RIGHT -> HidKeys.KEY_RIGHT
                            DPadView.Zone.OK -> HidKeys.KEY_ENTER
                        }
                    )
                }
            }
            onRelease = { remote.keyboardUp() }
        }
        body.addView(dpad, LinearLayout.LayoutParams(dp(210), dp(210)).apply {
            topMargin = dp(10); bottomMargin = dp(10)
        })

        body.addView(row(
            consumer(Icon.BACK, HidKeys.BACK),
            consumer(Icon.HOME, HidKeys.HOME, dark = true),
            consumer(Icon.LIVE_TV, HidKeys.LIVE_TV)
        ))
        body.addView(pill(
            consumer(Icon.VOLUME_DOWN, HidKeys.VOLUME_DOWN, flat = true),
            consumer(Icon.MUTE, HidKeys.MUTE, flat = true),
            consumer(Icon.VOLUME_UP, HidKeys.VOLUME_UP, flat = true)
        ))
        body.addView(pill(
            consumer(Icon.CHANNEL_UP, HidKeys.CHANNEL_UP, flat = true),
            consumer(Icon.TEXT, HidKeys.GUIDE, label = "GUIDE", flat = true),
            consumer(Icon.CHANNEL_DOWN, HidKeys.CHANNEL_DOWN, flat = true)
        ))
        body.addView(row(
            consumer(Icon.REWIND, HidKeys.REWIND),
            consumer(Icon.PLAY_PAUSE, HidKeys.PLAY_PAUSE),
            consumer(Icon.FAST_FORWARD, HidKeys.FAST_FORWARD)
        ))
        body.addView(row(
            consumer(Icon.APPS, HidKeys.ALL_APPS),
            RemoteKey(this, Icon.KEYBOARD, label = "Keyboard").apply {
                onPress = { toggleTyping() }
            }
        ))
        body.addView(pill(
            RemoteKey(this, Icon.YOUTUBE, label = "YouTube", flat = true).apply {
                onPress = { if (checkConnected()) remote.keyboardDown(HidKeys.KEY_YOUTUBE) }
                onRelease = { remote.keyboardUp() }
            }
        ))
        return body
    }

    private fun consumer(
        icon: Icon, usage: Int, label: String = "", dark: Boolean = false, flat: Boolean = false
    ) = RemoteKey(this, icon, label, dark, flat).apply {
        onPress = { if (checkConnected()) remote.consumerDown(usage) }
        onRelease = { remote.consumerUp() }
    }

    private fun row(vararg keys: View): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val gap = if (keys.size == 1) 0 else dp(14)
        keys.forEach { row.addView(it, LinearLayout.LayoutParams(dp(64), dp(64)).apply { leftMargin = gap; rightMargin = gap }) }
        return row.withSpacing()
    }

    private fun pill(vararg keys: View): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dp(30).toFloat(); setColor(Palette.KEY) }
            elevation = dp(1).toFloat()
        }
        keys.forEach { row.addView(it, LinearLayout.LayoutParams(0, dp(58), 1f)) }
        val wrap = FrameLayout(this)
        wrap.addView(row, FrameLayout.LayoutParams(dp(248), dp(58), Gravity.CENTER))
        return wrap.withSpacing()
    }

    private fun View.withSpacing(): View {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(7); bottomMargin = dp(7)
        }
        return this
    }

    // --- Typing on the TV (search boxes, WiFi passwords and so on) ---

    private fun buildTypingPanel(): LinearLayout {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(Color.rgb(0x1C, 0x1C, 0x1E))
        }
        typingInput = EditText(this).apply {
            hint = "Type to send to the TV"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
            }
        }
        panel.addView(typingInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        panel.addView(smallButton("⌫") { if (checkConnected()) remote.tapKeyboard(HidKeys.KEY_BACKSPACE) })
        panel.addView(smallButton("Send") { sendTyped() })
        return panel
    }

    private fun smallButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        setOnClickListener { action() }
    }

    private fun sendTyped() {
        if (!checkConnected()) return
        val text = typingInput.text.toString()
        if (text.isEmpty()) remote.tapKeyboard(HidKeys.KEY_ENTER) else remote.type(text)
        typingInput.setText("")
    }

    private fun toggleTyping() {
        val show = typingPanel.visibility != View.VISIBLE
        typingPanel.visibility = if (show) View.VISIBLE else View.GONE
        val ime = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) {
            typingInput.requestFocus()
            ime.showSoftInput(typingInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            ime.hideSoftInputFromWindow(typingInput.windowToken, 0)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_PERMISSIONS = 1
        private const val REQUEST_ENABLE_BT = 2
        private const val REQUEST_DISCOVERABLE = 3
        private val BACKGROUND = Color.rgb(0x12, 0x12, 0x14)
    }
}
