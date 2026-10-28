# Phone remote

Scan the QR code on the TV (first launch, dock → *Phone remote*, or Settings). It opens `http://<tv-ip>:8765/` in the phone's browser, with no app to install.

## Pairing and security

- A phone that isn't paired triggers a **4-digit code** on the TV. Typing it on the phone pairs it.
- Each code lasts 2 minutes and allows 5 guesses, then it is discarded. Wrong guesses also add up across codes: after 15, pairing locks for 10 minutes, so nobody on the Wi-Fi can brute-force the code by asking for fresh ones.
- A successful pairing issues a random 256-bit token, stored in the phone's browser. The TV stores only its SHA-256 hash (DataStore). Every WebSocket message is checked against the current list, so **Settings → Phone remote → Forget** cuts a phone off immediately.
- WebSocket connections from other origins (a random web page open on the phone) are rejected.
- Traffic is plain HTTP on your LAN, with no TLS, because a TV can't hold a trusted certificate for a local IP. Anyone who can sniff your Wi-Fi could observe button presses and the token. That's acceptable on a WPA2/3 home network. Don't use it on public Wi-Fi.

## Tabs

- **Remote:** quick actions (Assistant, Home, Ambient, Settings), D-pad + OK, Back/Home, media keys, volume.
- **Shortcuts:** your Glass TV favorites, then other TV apps, with their real icons sent by the TV. Tap to launch. Works even while another app is open (with the accessibility service on).
- **Touchpad**, **Type**: see below.

## What works where

| Action | Inside Glass TV | Inside other apps (Netflix, YouTube…) |
|---|---|---|
| D-pad, OK | ✅ Real key events, identical to the TV remote | ⚠️ Best effort, see below |
| Back | ✅ | ✅ needs the accessibility service on (`GLOBAL_ACTION_BACK`) |
| Home | ✅ | ✅ needs the accessibility service on |
| Play/pause, rewind, fast-forward | ✅ | ✅ media keys go to the active media session, no special permission |
| Volume up/down/mute | ✅ | ✅ `AudioManager` (with HDMI-CEC/ARC the TV forwards it to the soundbar if the firmware supports it) |
| Keyboard / dictation | ✅ goes into the focused text field, otherwise into the assistant | ⚠️ sets text in a focused standard text field, if the app exposes one |
| App shortcuts / quick actions | ✅ | ✅ needs the accessibility service on (Android blocks background app launches otherwise) |
| Touchpad | ✅ swipes map to D-pad, tap = OK, two-finger tap = Back | same limits as D-pad |

### Why D-pad in other apps is limited

Android never lets a regular app, including an accessibility service, **inject key events** into another app. That needs the `INJECT_EVENTS` signature permission, which only system apps have. The closest available tool is accessibility focus search (`AccessibilityNodeInfo.focusSearch` + `ACTION_FOCUS` / `ACTION_CLICK`). Glass TV uses it, but it only works in apps built from standard Android views. Netflix, YouTube, Disney+, Max, Prime Video and most players draw their own UI and don't expose focusable nodes, so D-pad presses from the phone do nothing there.

**Workaround:** Google's official **Google TV app** (iOS/Android) has a full remote that works in every app, because it uses a privileged system channel. Use Glass TV's phone remote for the launcher, the assistant and typing, and the Google TV app for in-app navigation. Media keys, Back, Home and volume from Glass TV's remote work everywhere.

### Voice-to-text

Browsers only allow the Web Speech API on HTTPS pages, and this page is HTTP on your LAN. Use your phone keyboard's microphone button in the *Type* tab instead (Gboard and the iOS keyboard both support it). It works offline on most phones and needs nothing from the TV.

## Connection behaviour

WebSocket with automatic reconnect (exponential backoff, 0.5–8 s) and a 10-second keep-alive ping. Reconnects as soon as the page becomes visible again. The server runs in a foreground service, so it stays up while other apps are open, and it starts on boot.
