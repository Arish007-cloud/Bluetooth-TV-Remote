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
4. After that the app stays connected and reconnects to the TV by itself (see below).

## YouTube and Tamasha buttons: one-time TV setup

Google TV has no standard Bluetooth code that opens a specific app. The app buttons on Haier's own remote use Haier-only codes that other devices can't send. So the app's buttons send spare keys the TV normally ignores (YouTube = F12, Tamasha = F11), and a free app on the TV turns them into "open YouTube" and "open Tamasha". After this one-time setup they work every time, WiFi on or off.

1. On the TV, open Google Play and install **Button Mapper** (by flar2). This step needs internet once.
2. Open Button Mapper and turn on its accessibility service when it asks.
3. Choose **Add buttons**, then press **YouTube** in the phone app. It shows up as F12. Set its **Single tap** to **Applications › YouTube** and turn the mapping on.
4. Choose **Add buttons** again, press **Tamasha** in the phone app (shows as F11), and set its **Single tap** to **Applications › Tamasha**.

The YouTube button also sends Android's built-in "open YouTube" shortcut (Meta+Y), which some TVs follow even without Button Mapper.

## Staying connected

Once the TV is connected, the app remembers it and keeps the link in the background (you'll see a small "TV Remote" notification). When the TV goes to standby or is unplugged and plugged back in, the app keeps retrying and reconnects by itself as soon as the TV's Bluetooth is back. It also starts by itself after the phone restarts. Tap **Change › Disconnect** in the app to stop this.

On Oppo/ColorOS, so the phone doesn't stop the app in the background: Settings › Apps › TV Remote › Battery usage › turn on **Allow background activity**.

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
| YouTube | F12 (+ Meta+Y) | Opens YouTube after the setup above |
| Tamasha | F11 | Opens Tamasha after the setup above |

Netflix and Prime Video buttons can be added the same way as YouTube if you want them.

## Code

- `HidKeys.kt`: the Bluetooth HID report descriptor and key codes
- `HidRemote.kt`: registers the phone as a Bluetooth remote, connects, sends keys
- `RemoteViews.kt`: the remote keys and the round D-pad, drawn in code
- `MainActivity.kt`: the screen, pairing, permissions
