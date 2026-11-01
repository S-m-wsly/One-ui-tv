# One UI TV

A Samsung One UI inspired launcher (home screen) for an Android projector.

- Greeting, status chip (Wi-Fi, weather, clock) and three widgets: Resume, Weather, Sources (HDMI / AV)
- Floating bottom nav: Home, Movies, Music, Browsers, Apps. The selected tab expands into a white pill and the app row below changes with it
- One UI motion: springy focus with a growing white outline, press squish, staggered entrances, a circle that grows out of the icon when an app opens
- Vertical quick panel (press the status chip or the MENU key): Wi-Fi, Bluetooth, Screen mirror, Auto keystone, plus Settings (Sources, Wallpaper, Picture & Sound, Focus, All system settings)
- Soft drifting aurora wallpaper with 6 palettes (auto-change every 30 s or pick one)

`design/OneUI_TV_preview.html` is the interactive web design this app follows (open it in a browser: arrows, Enter, Esc).

## Build
Every push builds the APK with GitHub Actions: open the **Actions** tab, open the latest run, download the **oneuitv-apk** artifact and unzip it.

The project is signed with the committed debug key `app/debug.keystore`, so each new build updates the previous one with `adb install -r`.
(That key is for debug builds only. It is not a secret and not for publishing.)

## Install on the projector
```
adb connect <projector-ip>:5555
adb install -r app-debug.apk
adb shell am start -n com.wes.oneuitv/.MainActivity
```
The app id is `com.wes.oneuitv`, so it installs next to the old Projector Home. Press Home on the remote and choose **One UI TV** (always) to make it the launcher.

If it crashes: `adb logcat -d AndroidRuntime:E *:S`

## Layout of the code (app/src/main/java/com/wes/oneuitv)
- `MainActivity.kt` home screen, nav, widgets, app launch animation, system actions
- `QuickPanel.kt` the vertical quick panel and its pages
- `Ui.kt` 1280x720 grid scaling, focus outline, press animation
- `AuroraView.kt` animated wallpaper
- `Apps.kt` loads installed apps and sorts them into categories by package name
