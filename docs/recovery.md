# Recovery: every system change and how to undo it

Glass TV does not need root and does not modify system files. These are the only commands in this project that change TV state. Each has its reverse. **Run the "record first" step before every change** and paste its output into the log at the bottom, so the exact original value can be restored.

> Golden rule: never disable or change the stock launcher unless (1) Glass TV is installed and opens normally, and (2) `adb connect <tv-ip>` still works **after a reboot**. On Android 11+ firmware, *Wireless debugging* switches itself off on reboot. Make sure plain network ADB on port 5555 (TCL: Developer options → *USB debugging*, often also *ADB over network*) survives a reboot first.

In every command, `TV=192.168.1.42:5555` is your TV. Run `adb connect $TV` first.

---

## 0. Check the firmware (no change)

```sh
adb -s $TV shell getprop ro.build.version.sdk        # API level, e.g. 30 = Android 11, 34 = Android 14
adb -s $TV shell getprop ro.build.version.release
adb -s $TV shell getprop ro.product.model
adb -s $TV shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

The last command prints the current HOME activity, usually `com.google.android.apps.tv.launcherx/.home.HomeActivity` (the exact class can differ by firmware). **Save it. It is the value you restore in §3.**

## 1. Install / uninstall Glass TV

| Do | Undo |
|---|---|
| `scripts/deploy.sh <tv-ip>` (or `adb -s $TV install -r app-release.apk`) | `adb -s $TV uninstall dev.glasstv.launcher` |

Uninstalling removes the accessibility service and every Glass TV setting. The stock home comes back on the next Home press.

## 2. Home button via AccessibilityService (preferred; fully reversible)

Normal way: on the TV, go to **Settings → System → Accessibility → Glass TV Home button → On**. To undo, switch it off. You can also switch off **Settings → Home button → Open Glass TV on Home** inside Glass TV, which keeps the service but stops the redirect.

ADB way (only if the TV menu greys the toggle out):

```sh
# Record first:
adb -s $TV shell settings get secure enabled_accessibility_services
adb -s $TV shell settings get secure accessibility_enabled

# Do: APPEND our service (":"-separated). Never overwrite other services.
PREV=$(adb -s $TV shell settings get secure enabled_accessibility_services | tr -d '\r')
SVC=dev.glasstv.launcher/dev.glasstv.launcher.a11y.HomeAccessibilityService
if [ -z "$PREV" ] || [ "$PREV" = "null" ]; then NEW=$SVC; else NEW="$PREV:$SVC"; fi
adb -s $TV shell settings put secure enabled_accessibility_services "$NEW"
adb -s $TV shell settings put secure accessibility_enabled 1
```

Undo: put back the exact recorded values:

```sh
adb -s $TV shell settings put secure enabled_accessibility_services "<recorded value>"
adb -s $TV shell settings put secure accessibility_enabled <recorded value>
# If the recorded value was "null" (nothing was enabled):
adb -s $TV shell settings delete secure enabled_accessibility_services
```

## 3. Default HOME activity (option 2; only if the service approach is not enough)

```sh
# Record first: see §0 (resolve-activity). Then:
adb -s $TV shell cmd package set-home-activity dev.glasstv.launcher/.MainActivity
```

Undo, using the exact component you recorded in §0:

```sh
adb -s $TV shell cmd package set-home-activity <recorded component, e.g. com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.home.HomeActivity>
```

Uninstalling Glass TV also reverts this automatically, because the system falls back to the remaining HOME app.

## 4. Disable the stock launcher (option 3; last resort, not recommended)

On Google TV, `launcherx` also provides the settings side panel, input switching, profiles and "For you" recommendations. Disabling it can break those. Only do this if §2 and §3 both fail and you accept that.

```sh
# Pre-checks (all must pass):
adb -s $TV shell pm list packages | grep dev.glasstv.launcher         # Glass TV installed
adb -s $TV reboot; sleep 90; adb connect $TV                           # ADB survives a reboot
# Do:
adb -s $TV shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
```

Undo:

```sh
adb -s $TV shell pm enable com.google.android.apps.tv.launcherx
```

If the TV ever boots to a blank "Android is starting…" or a black screen and ADB still connects, run the undo command above, then `adb -s $TV reboot`. If ADB does **not** connect, a factory reset restores the stock state. Use the recovery-mode procedure for your exact model from TCL support. It erases all data. Because of this risk, verify the pre-checks.

## 5. Phone remote foreground service

No system change. It starts with Glass TV and on boot. To stop it, uninstall the app or use `adb -s $TV shell am force-stop dev.glasstv.launcher` (it restarts the next time Glass TV opens).

---

## Change log (fill in as you go)

| Date | Command run | Recorded original value | Reverted? |
|---|---|---|---|
| | | | |
