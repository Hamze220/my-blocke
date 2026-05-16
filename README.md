# Blocker

**A no-nonsense Android app that actually keeps you off your phone.**

Blocker lets you set hard limits on apps and websites — with deliberate friction to make undoing them feel like a conscious choice, not a reflex. It runs entirely on-device with no account, no cloud sync, no tracking.

---

## Features

### 📱 App Blocking
Block any installed app after a **daily usage quota** (e.g. Instagram after 30 minutes). The app monitors foreground usage via Android's `UsageStatsManager` and triggers a blocking overlay the moment the quota is hit.

### 🌐 Website Blocking
Block websites across **all browsers** — Chrome, Firefox, Safari, DuckDuckGo, anything — using a local VPN that intercepts DNS queries. Blocked domains return `NXDOMAIN`; all other traffic passes through unmodified. Supports **subdomain wildcarding** (blocking `reddit.com` also blocks `old.reddit.com`, `www.reddit.com`, etc.) and optional **daily quotas** per domain.

### 🗓 Schedule Blocking
Block apps during specific **time windows** and **days of the week** — independently of any daily quota. Useful for keeping work apps off during evenings, or social media off during mornings.

### ⏱ Quick Block (Focus Mode)
Hit **Start Blocking** and everything is blocked for a chosen duration (15m, 30m, 1h, 2h, or custom). Optionally whitelist specific apps (e.g. Maps, Phone) as exceptions. A persistent notification counts down the remaining time. An **Emergency Stop** requires a 10-second countdown confirmation so it can't be tapped away by accident.

### 🔒 Friction on Disable
Disabling any active rule — whether a daily quota, a schedule, or a site block — shows a **5-second countdown confirmation dialog**. You can cancel during the countdown. This small amount of friction is enough to break the "disable and forget" reflex.

### 🔔 Site-Blocked Notifications
When the VPN intercepts a DNS query for a blocked site, a system notification appears: _"reddit.com is blocked by Blocker"_. Rate-limited to once per domain per 30 seconds to avoid noise. Lets you know the block is working rather than leaving you staring at a cryptic browser error.

---

## Screenshots

> _Add screenshots here once the app is running on device._

---

## Architecture

Blocker is a pure native Android app — no cross-platform framework, no Compose, no external analytics or crash reporting.

```
┌─────────────────────────────────────────────────────┐
│                     UI Layer                        │
│  Apps · Sites · Schedule · Focus  (Fragments/MVVM)  │
└────────────────────┬────────────────────────────────┘
                     │ StateFlow / LiveData
┌────────────────────▼────────────────────────────────┐
│                  Data Layer                         │
│  Room DB  ·  AppRule  ·  SiteRule  ·  SiteUsage    │
│  BlockerRepository  (single source of truth)        │
└──────────┬──────────────────────────┬───────────────┘
           │ Flow<List<AppRule>>       │ Flow<List<SiteRule>>
┌──────────▼──────────┐   ┌───────────▼───────────────┐
│ AccessibilityService│   │     VPN Service            │
│                     │   │                            │
│ Detects foreground  │   │ TUN interface intercepts   │
│ app changes via     │   │ DNS (UDP port 53).         │
│ TYPE_WINDOW_STATE_  │   │ Blocked domains → NXDOMAIN │
│ CHANGED events.     │   │ Others → forwarded to      │
│                     │   │ 8.8.8.8 via protected      │
│ Checks quota +      │   │ DatagramSocket.            │
│ schedule + Quick    │   │                            │
│ Block state.        │   │ Quota domains tracked by   │
│                     │   │ minute-of-day stamps.      │
│ Shows WindowManager │   │                            │
│ overlay on block.   │   │ All other traffic          │
└─────────────────────┘   │ (HTTP/HTTPS) is untouched. │
                          └────────────────────────────┘
```

**Key design decisions:**
- Rules are observed via `Flow` — both services always have the latest state without polling or manual refresh.
- The VPN only routes a `/24` subnet containing our fake DNS server through the tunnel. All other traffic (actual HTTP/HTTPS) uses the real network.
- DNS forwarding is async — each packet is handled in its own coroutine so one slow upstream lookup doesn't block others.
- Blocking uses a full-screen `WindowManager` overlay (`TYPE_APPLICATION_OVERLAY`) rather than launching an activity, so the blocked app's back-stack is untouched.

**Tech stack:**
- Kotlin · Coroutines · StateFlow
- Room (SQLite) · DAO pattern
- AccessibilityService · VpnService
- ViewBinding · Fragment + ViewModel
- Material 3 · iOS-inspired glass aesthetic
- Robolectric · Espresso (tests)

---

## Permissions

| Permission | Why |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Detect which app is in the foreground |
| `PACKAGE_USAGE_STATS` | Read per-app daily usage time |
| `BIND_VPN_SERVICE` | Create the local DNS-intercept tunnel |
| `SYSTEM_ALERT_WINDOW` | Show the blocking overlay above all apps |
| `POST_NOTIFICATIONS` | Site-blocked and Quick Block countdown notifications |
| `FOREGROUND_SERVICE` | Keep the VPN and Quick Block services alive |
| `RECEIVE_BOOT_COMPLETED` | Restart services after reboot |

No internet permission is used by the app itself. The VPN service forwards non-blocked DNS queries to `8.8.8.8` using an OS-protected socket that bypasses the tunnel.

---

## Known Limitations

**DNS over HTTPS (DoH):** If Chrome's _"Use secure DNS"_ setting is on, DNS queries are sent over HTTPS and bypass our VPN tunnel entirely. Website blocking will silently fail for those requests. Fix: in Chrome go to `Settings → Privacy and security → Security → Use secure DNS → Off`. Also set Android's Private DNS (`Settings → Network → Private DNS`) to **Off**.

**HTTPS block pages:** Because we block at the DNS level (not HTTP), the browser shows its built-in "DNS_PROBE_FINISHED_NXDOMAIN" error rather than a custom "blocked" page. A system notification appears instead to confirm the block is active.

**App blocking requires accessibility:** Android disables the accessibility service on every reinstall (a system security measure). After each update you need to re-enable it once in `Settings → Accessibility → Blocker App Monitor`.

---

## Installing on Your Own Phone

### Option A — Build from source (Android Studio)

1. Clone this repository:
   ```bash
   git clone https://github.com/cylldby/blocker.git
   cd blocker
   ```
2. Open in **Android Studio** (Ladybug or later recommended).
3. Connect your Android phone via USB with **USB Debugging** enabled.
4. Click the green **Run** button (or press `Shift+F10`).
5. The app installs and launches. Follow the in-app onboarding to grant permissions.

### Option B — Build APK from the command line

```bash
git clone https://github.com/cylldby/blocker.git
cd blocker
./gradlew assembleDebug
```

The APK is produced at:
```
app/build/outputs/apk/debug/app-debug.apk
```

Transfer it to your phone (Google Drive, USB, etc.), tap it on the phone, and allow installation from unknown sources when prompted.

### Option C — Install via ADB

If your phone is connected via USB or wireless ADB:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### After installing

1. Open the app — the onboarding screen will walk you through all required permissions.
2. Grant: **Usage Access**, **Accessibility Service**, **Display Over Other Apps**, **VPN**.
3. Done. Add your first rule in the **Apps** or **Sites** tab.

> **Note:** This is a debug build signed with the default Android debug keystore. It is not distributed through the Play Store and must be sideloaded. Android may show a warning about installing from unknown sources — this is expected.

---

## Running Tests

**Unit tests** (JVM, no device needed):
```bash
./gradlew test
```
Covers: DNS query parsing, NXDOMAIN generation, subdomain matching, schedule logic (overnight windows, day-of-week mapping, quota edge cases), Room DAO CRUD, ViewModel Flow behaviour.

**Instrumented UI tests** (requires connected device or emulator):
```bash
./gradlew connectedAndroidTest
```
Covers: bottom-nav navigation, adding rules, dialog flows, Quick Block tab state transitions.

> Full end-to-end tests for the accessibility service and VPN blocking cannot be fully automated — enabling the accessibility service requires a manual system settings change that Android does not allow automation scripts to perform. The services themselves are covered by the unit tests on their pure logic components (`DnsBlocker`, `ScheduleChecker`).

---

## License

MIT — do whatever you want with it.
