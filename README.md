# TV Remote

An Android app that turns your phone into a Bluetooth remote for a Google TV (built for a Haier Google TV). The phone pairs with the TV as a Bluetooth keyboard + media remote, so it works the same with WiFi on or off. No extra hardware.

Requires Android 9 or newer, and a phone that allows Bluetooth "HID device" mode (most do; the app tells you if yours doesn't).

## Build the APK

**Option 1: Android Studio (on a computer)**
1. Install Android Studio and open this folder.
2. Let it sync, then choose Build › Build App Bundle(s) / APK(s) › Build APK(s).
3. The file is `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the phone and open it.

**Option 2: GitHub, no computer setup**
1. Create a new GitHub repository and upload this folder's contents.
2. GitHub Actions builds the app automatically (`.github/workflows/build.yml`).
3. Open the Actions tab, open the latest run, and download `TvRemote-debug-apk`. Unzip it on the phone and install.

On Oppo/ColorOS, allow "Install unknown apps" for your browser or file manager when asked.

## First-time pairing

1. Open the app and allow the Nearby devices permission.
2. On the TV: Settings › Remotes & Accessories › Pair accessory.
3. In the app tap **Connect** and pick the TV. If the TV doesn't show up, tap **Make phone visible** and pick the phone on the TV's screen instead.
4. After that the app reconnects to the TV by itself each time you open it.

## YouTube button: one-time TV setup

Google TV has no standard Bluetooth code that opens YouTube. The YouTube button on Haier's own remote uses a Haier-only code that other devices can't send. So the app's YouTube button sends F12, a key the TV normally ignores, and a free app on the TV turns F12 into "open YouTube". After this one-time setup it works every time, WiFi on or off.

1. On the TV, open Google Play and install **Button Mapper** (by flar2). This step needs internet once.
2. Open Button Mapper and turn on its accessibility service when it asks.
3. Choose **Add buttons**, then press the **YouTube** button in this app. It shows up as F12.
4. Tap the new F12 entry, set **Single tap** to **Applications › YouTube**, and turn the mapping on.

## Keys

| Button | Sends | Notes |
| --- | --- | --- |
| Power | Power | Turns the TV off. Turning it back on over Bluetooth depends on the TV. |
| Assistant (4 dots) | Search | Opens Google search/Assistant on the TV |
| Mic | Voice assist | The TV listens, if it has its own mic |
| Menu, Settings, Profile | Menu, Settings, Profile switch | |
| D-pad, OK, Back, Home | Arrows, Enter, Back, Home | |
| Live TV | TV | |
| Channel up/down, Guide | Channel up/down, Guide | |
| Vol−, Mute, Vol+ | Volume | On-screen volume bar; hold to keep changing |
| Play/Pause, Rewind, Fast-forward | Media keys | |
| Apps grid | All apps | |
| AUDIO | Audio track | |
| Keyboard | Opens a text box | Type searches or passwords and tap Send |
| YouTube | F12 | Opens YouTube after the setup above |

Netflix and Prime Video buttons can be added the same way as YouTube if you want them.

## Code

- `HidKeys.kt`: the Bluetooth HID report descriptor and key codes
- `HidRemote.kt`: registers the phone as a Bluetooth remote, connects, sends keys
- `RemoteViews.kt`: the remote keys and the round D-pad, drawn in code
- `MainActivity.kt`: the screen, pairing, permissions
