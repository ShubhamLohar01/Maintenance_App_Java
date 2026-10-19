# Backend task — Instant push for new breakdowns (FCM)

We are adding **instant push notifications** so a technician's phone buzzes the moment an
operator submits a breakdown, instead of waiting for the app's 2-minute poll. The Android
app side is already done. You need to build the **server send side** using Firebase Cloud
Messaging (FCM).

## Context you need
- A Firebase project **`factoryops-f08d9`** already exists. The app is registered.
- You will receive a **service-account JSON key** (generated from Firebase Console →
  Project settings → Service accounts → Generate new private key). Treat it as a secret:
  load it from an env var / secrets manager, **do NOT commit it to git**.
- The Android app authenticates every request with a Bearer JWT (same as all existing
  endpoints).

## 1) Store device tokens
Create a table (names to taste, match our conventions):

```
device_tokens
  id           PK
  user_id      FK -> users     (indexed)
  token        text  UNIQUE     (the FCM registration token)
  platform     text            ("android")
  updated_at   timestamptz
```

`token` must be UNIQUE. A token identifies one app install and can move between users on a
shared device, so **UPSERT by token** (on conflict, overwrite user_id + updated_at).

## 2) Endpoint: register a device token
```
POST /devices/token
Authorization: Bearer <jwt>
Content-Type: application/json

{ "user_id": "<id>", "token": "<fcm token>", "platform": "android" }
```
- Auth required (reuse existing JWT dependency).
- UPSERT by token (see above). Return **2xx with an empty body** on success.
- The app calls this on login, app start, and whenever FCM rotates the token, so it will
  be hit repeatedly with the same values — that's expected; just upsert.

## 3) Send a push when a breakdown is raised
In the existing `POST /breakdowns/flag` handler, **after** the breakdown row is saved,
send an FCM push to the technicians who should be alerted:

- **Audience:** all users with role **TECHNICIAN** in the **same plant** as the breakdown
  (a new breakdown is unassigned, so every technician in that plant is alerted — this
  matches the app's own logic). Look up their tokens from `device_tokens`.
- Use FCM **multicast** (send to the list of tokens).

### Message format — IMPORTANT: send a DATA-ONLY message
The app builds its own notification from the `data` map (so it can localize + de-duplicate).
Do **NOT** include a `notification` block — send **data only**, with **high priority**:

```
message = {
  "data": {
    "type":        "B1_NEW_BREAKDOWN",
    "category":    "BREAKDOWN",
    "entityId":    "<breakdown/flag id>",     // the id returned by /breakdowns/flag
    "title":       "<SEVERITY> breakdown — <asset name>",   // e.g. "MAJOR breakdown — Band Sealer"
    "body":        "<description, or 'Needs a technician.' if empty>",
    "targetRole":  "TECHNICIAN",
    "tier":        "0"
  },
  "android": { "priority": "high" }
  // token / tokens set per recipient
}
```
All `data` values must be **strings** (FCM requirement). `tier` is the string `"0"`.

### Clean up dead tokens
When FCM returns `UNREGISTERED` / `INVALID_ARGUMENT` for a token in the multicast
response, **delete that token** from `device_tokens` so the table doesn't rot.

## 4) Firebase Admin setup
- Add the Firebase Admin SDK (Python: `firebase-admin`).
- Initialize once at startup with the service-account credentials from the secret/env.
- Use `messaging.send_each_for_multicast(...)` (or the current multicast API) for step 3.

## Scope / non-goals for now
- **Only the "new breakdown → technician" instant push.** The timed escalations
  (Supervisor at ~1h, Head at ~5h) are still handled on-device by the app's poll, so you
  do NOT need a scheduler yet. (If we later want those as push too, that's a follow-up.)
- No changes needed to the breakdown data model beyond the new `device_tokens` table.

## How we'll test end to end
1. Two phones, both on the real backend, notification permission granted.
2. Technician logs in on phone B → confirm a row appears in `device_tokens`.
3. Operator raises a breakdown on phone A → phone B should buzz within ~1–2 seconds.
   (If a phone was killed and misses the push, the app's 2-minute poll still catches it —
   that's the intended safety net.)
