package com.haris.tvremote

/**
 * HID report layout and key codes.
 *
 * Report 1 is a standard keyboard (D-pad, Enter, typing). Report 2 is a
 * Consumer Control usage, which Android/Google TV maps to media and TV keys
 * (Home, Back, volume, channels, Assistant and so on).
 */
object HidKeys {
    const val REPORT_KEYBOARD = 1
    const val REPORT_CONSUMER = 2

    val DESCRIPTOR = byteArrayOf(
        // Keyboard
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x06,             // Usage (Keyboard)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), 0x01,    //   Report ID (1)
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0xE0.toByte(),    //   Usage Minimum (Left Ctrl)
        0x29, 0xE7.toByte(),    //   Usage Maximum (Right GUI)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x01,             //   Logical Maximum (1)
        0x75, 0x01,             //   Report Size (1)
        0x95.toByte(), 0x08,    //   Report Count (8)
        0x81.toByte(), 0x02,    //   Input (Data, Variable, Absolute) - modifiers
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x81.toByte(), 0x01,    //   Input (Constant) - reserved byte
        0x75, 0x08,             //   Report Size (8)
        0x95.toByte(), 0x06,    //   Report Count (6)
        0x15, 0x00,             //   Logical Minimum (0)
        0x25, 0x65,             //   Logical Maximum (101)
        0x05, 0x07,             //   Usage Page (Key Codes)
        0x19, 0x00,             //   Usage Minimum (0)
        0x29, 0x65,             //   Usage Maximum (101)
        0x81.toByte(), 0x00,    //   Input (Data, Array) - key array
        0xC0.toByte(),          // End Collection

        // Consumer Control
        0x05, 0x0C,             // Usage Page (Consumer)
        0x09, 0x01,             // Usage (Consumer Control)
        0xA1.toByte(), 0x01,    // Collection (Application)
        0x85.toByte(), 0x02,    //   Report ID (2)
        0x15, 0x00,             //   Logical Minimum (0)
        0x26, 0xFF.toByte(), 0x03,          //   Logical Maximum (0x3FF)
        0x19, 0x00,                         //   Usage Minimum (0)
        0x2A, 0xFF.toByte(), 0x03,          //   Usage Maximum (0x3FF)
        0x75, 0x10,             //   Report Size (16)
        0x95.toByte(), 0x01,    //   Report Count (1)
        0x81.toByte(), 0x00,    //   Input (Data, Array)
        0xC0.toByte()           // End Collection
    )

    // Keyboard usages
    const val KEY_ENTER = 0x28
    const val KEY_BACKSPACE = 0x2A
    const val KEY_RIGHT = 0x4F
    const val KEY_LEFT = 0x50
    const val KEY_DOWN = 0x51
    const val KEY_UP = 0x52
    const val MOD_LEFT_SHIFT = 0x02

    /**
     * Google TV has no standard key that opens YouTube (the Haier remote's
     * button is a Haier-only code). The YouTube key sends F12, which the TV
     * ignores by default, and a key-mapping app on the TV turns it into
     * "open YouTube". See README.
     */
    const val KEY_YOUTUBE = 0x45 // F12

    // Consumer usages, with the Android key each one becomes on the TV
    const val POWER = 0x030          // POWER
    const val MENU = 0x040           // MENU
    const val LIVE_TV = 0x089        // TV
    const val GUIDE = 0x08D          // GUIDE
    const val CHANNEL_UP = 0x09C     // CHANNEL_UP
    const val CHANNEL_DOWN = 0x09D   // CHANNEL_DOWN
    const val FAST_FORWARD = 0x0B3   // MEDIA_FAST_FORWARD
    const val REWIND = 0x0B4         // MEDIA_REWIND
    const val PLAY_PAUSE = 0x0CD     // MEDIA_PLAY_PAUSE
    const val VOICE = 0x0CF          // VOICE_ASSIST
    const val MUTE = 0x0E2           // VOLUME_MUTE
    const val VOLUME_UP = 0x0E9      // VOLUME_UP
    const val VOLUME_DOWN = 0x0EA    // VOLUME_DOWN
    const val AUDIO_TRACK = 0x173    // MEDIA_AUDIO_TRACK
    const val PROFILE = 0x19C        // PROFILE_SWITCH
    const val SETTINGS = 0x19F       // SETTINGS
    const val ALL_APPS = 0x1A2       // ALL_APPS
    const val ASSISTANT = 0x221      // SEARCH
    const val HOME = 0x223           // HOME
    const val BACK = 0x224           // BACK

    /** Keyboard usage and modifier for a character, or null if it can't be typed. */
    fun charToKey(c: Char): Pair<Int, Int>? {
        return when (c) {
            in 'a'..'z' -> Pair(0x04 + (c - 'a'), 0)
            in 'A'..'Z' -> Pair(0x04 + (c - 'A'), MOD_LEFT_SHIFT)
            in '1'..'9' -> Pair(0x1E + (c - '1'), 0)
            '0' -> Pair(0x27, 0)
            ' ' -> Pair(0x2C, 0)
            '\n' -> Pair(KEY_ENTER, 0)
            else -> SYMBOLS[c]
        }
    }

    private val SYMBOLS = mapOf(
        '-' to Pair(0x2D, 0), '_' to Pair(0x2D, MOD_LEFT_SHIFT),
        '=' to Pair(0x2E, 0), '+' to Pair(0x2E, MOD_LEFT_SHIFT),
        '[' to Pair(0x2F, 0), '{' to Pair(0x2F, MOD_LEFT_SHIFT),
        ']' to Pair(0x30, 0), '}' to Pair(0x30, MOD_LEFT_SHIFT),
        '\\' to Pair(0x31, 0), '|' to Pair(0x31, MOD_LEFT_SHIFT),
        ';' to Pair(0x33, 0), ':' to Pair(0x33, MOD_LEFT_SHIFT),
        '\'' to Pair(0x34, 0), '"' to Pair(0x34, MOD_LEFT_SHIFT),
        '`' to Pair(0x35, 0), '~' to Pair(0x35, MOD_LEFT_SHIFT),
        ',' to Pair(0x36, 0), '<' to Pair(0x36, MOD_LEFT_SHIFT),
        '.' to Pair(0x37, 0), '>' to Pair(0x37, MOD_LEFT_SHIFT),
        '/' to Pair(0x38, 0), '?' to Pair(0x38, MOD_LEFT_SHIFT),
        '!' to Pair(0x1E, MOD_LEFT_SHIFT), '@' to Pair(0x1F, MOD_LEFT_SHIFT),
        '#' to Pair(0x20, MOD_LEFT_SHIFT), '$' to Pair(0x21, MOD_LEFT_SHIFT),
        '%' to Pair(0x22, MOD_LEFT_SHIFT), '^' to Pair(0x23, MOD_LEFT_SHIFT),
        '&' to Pair(0x24, MOD_LEFT_SHIFT), '*' to Pair(0x25, MOD_LEFT_SHIFT),
        '(' to Pair(0x26, MOD_LEFT_SHIFT), ')' to Pair(0x27, MOD_LEFT_SHIFT)
    )
}
