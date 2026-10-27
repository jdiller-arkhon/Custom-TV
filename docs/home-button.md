# Making Glass TV the home screen

**Recommendation: use the AccessibilityService (option 1).** It changes no system package, survives firmware updates, and turning it off restores stock behaviour instantly. Every command is mirrored with its undo in [recovery.md](recovery.md).

## Before anything: check the firmware

```sh
adb connect <tv-ip>:5555
adb shell getprop ro.build.version.sdk     # API level
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Recent TCL Google TV models (QM6K, T7 and others) typically run Android TV 12 (API 31) or 14 (API 34), depending on model year and updates. Glass TV supports API 26+, so it runs on either. **I could not check your specific TV from here.** Run the commands above and record the output in `recovery.md`.

## Option 1: AccessibilityService (built in, preferred)

How it works (`a11y/HomeAccessibilityService.kt`):
1. **Key filter.** If the firmware passes `KEYCODE_HOME` to accessibility services, Glass TV consumes it and opens itself. Many Google TV builds handle Home in the system window manager before accessibility sees it, so this path may never fire on yours.
2. **Window watcher (the reliable path, same idea as Projectivy Launcher).** When the stock **home activity** comes to the front, Glass TV immediately brings itself forward. It matches the *exact* HOME component that `PackageManager` resolves, not just the `launcherx` package, so the Google TV settings side panel, inputs and other `launcherx` screens keep working. Expect a brief flash of the stock home on some presses. That is the cost of not modifying the system.

Enable it on the TV: **Settings → System → Accessibility → Glass TV Home button → On**. If the toggle is greyed out ("restricted setting"), open **Settings → Apps → Glass TV → ⋮ → Allow restricted settings**, or use the ADB commands in recovery.md §2.

Disable it: same switch → Off, or switch off **Glass TV → Settings → Home button → Open Glass TV on Home**.

The service only listens for window changes and key events, and it reads the focused element only to carry out phone-remote commands. It never stores or sends screen content.

## Option 2: Default HOME activity

Glass TV declares `CATEGORY_HOME`, so any firmware that offers a home-app picker will list it. Stock Google TV has no picker. You can try ADB:

```sh
adb shell cmd package set-home-activity dev.glasstv.launcher/.MainActivity
```

Results vary on Google TV: some builds honour it until reboot, and some re-assert `launcherx`. Undo is in recovery.md §3. Use this only if option 1's flash bothers you.

## Option 3: Disable the stock launcher (last resort)

`pm disable-user` on `com.google.android.apps.tv.launcherx` works, but it removes Google TV features that live in that package (settings panel, inputs, profiles). Follow the pre-checks in recovery.md §4 exactly. Not recommended.

## Getting back to stock things from Glass TV

Glass TV → Settings → **System** has buttons for *Android TV settings* and *Inputs & HDMI*, so nothing is lost when Glass TV is your home.
