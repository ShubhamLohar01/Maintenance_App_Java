# FactoryOps — How to Run

---

## Step 1 — Install Android Studio

Download and install **Android Studio** (free):
👉 https://developer.android.com/studio

Install it with default settings. It will automatically install Java, Kotlin, and the Android SDK for you.

---

## Step 2 — Open the Project

1. Open Android Studio
2. Click **Open**
3. Select the folder: `D:\Maintenance module\FactoryOps`
4. Click **OK**

Android Studio will show a progress bar at the bottom saying **"Gradle sync in progress"**.
This is like `npm install` — it downloads all libraries. Wait for it to finish (3–5 min first time).

> ✅ When sync is done, the bottom bar shows no errors and you see the project files on the left.

---

## Step 3 — Run on Virtual Mobile (Emulator)

### Create a virtual device (one-time setup)

1. In Android Studio, click **Tools → Device Manager**
2. Click **Create Device**
3. Select **Pixel 6** → Next
4. Select **API 34** → if there's a download button, click it first → Next → Finish

### Run the app

1. In the top toolbar, make sure the dropdown shows your virtual device
2. Click the green **▶ Run** button (or press `Shift + F10`)
3. The emulator opens and the app launches automatically

```
Web equivalent:   npm run dev  →  opens browser
Android:          ▶ Run        →  opens emulator with app
```

---

## Step 4 — Run on Your Real Mobile

### One-time setup on your phone

1. Go to **Settings → About phone**
2. Tap **Build number** 7 times
3. Go back → **Settings → Developer Options**
4. Turn on **USB Debugging**

### Connect and run

1. Plug your phone into your PC via USB
2. Phone will ask "Allow USB debugging?" → tap **Allow**
3. In Android Studio, your phone name will appear in the device dropdown
4. Click **▶ Run** — app installs and opens on your real phone

---

## Step 5 — After Changing Code (How to Reload)

### Option A — Apply Changes (fast, like hot reload)

Press **Ctrl + F10** (or click the lightning bolt ⚡ icon in toolbar)
Works for most UI changes without restarting the app.

### Option B — Full Reinstall (always works)

Click **▶ Run** again (`Shift + F10`)
Rebuilds and reinstalls the full app. Takes 20–40 seconds.

```
Web equivalent:   Save file → browser auto-refreshes
Android:          Ctrl+F10  → app updates instantly  (most changes)
                  ▶ Run     → full rebuild            (structural changes)
```

> Changes to code like new Activities, Manifest, or Hilt modules require full Run, not Apply Changes.

---

## Demo Login

When the app opens, use these credentials:

| Username | Password |
|----------|----------|
| `operator1` | `pass123` |

---

## View Logs (like browser console)

Click **Logcat** at the bottom of Android Studio.
Filter by your app: type `factoryops` in the search bar.

```
Web equivalent:   F12 → Console
Android:          Logcat tab (bottom of Android Studio)
```

---

## View the Database

1. Run the app on emulator or device
2. Click **App Inspection** tab at the bottom of Android Studio
3. Click **Database Inspector**
4. You can see all Room tables: `machines`, `production_runs`, `sync_queue`, etc.
5. You can even run SQL queries live

```
Web equivalent:   pgAdmin / TablePlus for PostgreSQL
Android:          App Inspection → Database Inspector
```

---

## Keyboard Shortcuts

| Action | Shortcut |
|--------|----------|
| Run app | `Shift + F10` |
| Apply changes (hot reload) | `Ctrl + F10` |
| Stop app | `Shift + F5` |
| Find any file | `Shift + Shift` (double shift) |
| Go to definition | `Ctrl + Click` on any class/function |
| Format code | `Ctrl + Alt + L` |

---

## Common Problems

**"Gradle sync failed"**
→ Check your internet connection → click **File → Sync Project with Gradle Files**

**"Emulator is very slow"**
→ Open Task Manager → Performance → CPU → check if Virtualization says Enabled
→ If Disabled, enable VT-x in your PC BIOS settings

**"App crashes on open"**
→ Check **Logcat** tab at bottom, filter by `factoryops`, look for red error lines

**"Device not showing in dropdown"**
→ Unplug and replug USB → tap Allow on phone when it asks again
