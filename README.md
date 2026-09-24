# Pocket Relay

Forwards incoming SMS and call alerts from an Android phone to push notifications on iOS, via a
self-hosted [ntfy](https://ntfy.sh) relay. Built because iOS doesn't allow a
persistent background connection and a custom iOS push implementation needs a
paid Apple Developer account — this sidesteps that by using ntfy's official
iOS app, which already has push notifications working.

```
Android phone --SMS--> SmsReceiver --HTTP POST--> self-hosted ntfy server --push--> iOS app
```

## How it works

- **`android/`** — a small Android app. `SmsReceiver` listens for incoming
  SMS (`RECEIVE_SMS`), and hands each message to a `WorkManager` job
  (`SmsForwardWorker`) that POSTs it as JSON to your ntfy server, with retry
  on failure. `MainActivity` is a bare settings screen for the server
  URL/topic/token and permission grants.
- **Call alerts** — when a call starts ringing, `CallReceiver` sends an urgent ntfy alert like
  "Name (number) is calling" (the contact name comes from your contacts, otherwise just the
  number). It needs the phone-state, call-log and contacts permissions (the "Grant permissions"
  button asks for all of them) and can be switched off in the app. It only tells you who is
  calling; it does not forward the call itself.
- **Control panel (optional, off by default)** — a small PIN-protected web page served by the
  app, for controlling the Android from the iPhone once the phone's hotspot is on. Open
  `http://192.168.43.1:8080` (the address is shown in the app) in Safari and enter the PIN.
  The master switch and a switch per feature live in the app's "Control panel" card, and a
  feature that is off is hidden from the page and refused by the server. Features: ring the
  phone, flashlight, Bluetooth / Wi-Fi / mobile-data switches (plus restart mobile data), volume
  and silent mode, a music player (now playing plus previous / play / next for whatever your music app has
  loaded; Play resumes the last app you used), recent messages, sending a text (off by
  default), recent calls, the devices connected to the hotspot (tap one to name it), and phone
  info. It only accepts connections from the phone itself and private (hotspot / Wi-Fi)
  addresses, locks out after 5 wrong PINs, and remembers a browser after the PIN is entered once
  (stored as a hash; "New PIN" signs everything out). It cannot turn the hotspot on, since it
  only exists while the hotspot is running. Some features need extra access that you grant in
  Android: accessibility (Wi-Fi and mobile-data switches, which only work while the phone is
  unlocked), Do Not Disturb access (silent mode) and notification access (music). Bluetooth
  switches directly and works with the screen off; Wi-Fi cannot be switched by apps while the
  hotspot is on.
- **`server/ntfy/server.yml`** — config for a self-hosted ntfy instance.
  Auth defaults to deny-all; only a scoped user/token can publish or
  subscribe to the topic. `upstream-base-url` is set to `ntfy.sh` so the
  official iOS app can receive push notifications (only the topic name and
  message ID are relayed there to trigger the push — message content is
  fetched directly from your own server).
- **iOS** — no custom app. Install the official [ntfy app](https://ntfy.sh)
  from the App Store and subscribe to your server/topic.

## Setup

### 1. Server

One-command install on any systemd-based Linux host (Debian/Ubuntu, RHEL/
Fedora, or a generic fallback for anything else) — installs ntfy, configures
nginx + certbot if present, and creates a scoped user/token:

```
curl -sSL https://get.asdl.website/pocket-relay | DOMAIN=ntfy.example.com bash
```

(or `bash -s -- ntfy.example.com` to pass the domain positionally; run
`... | bash -s -- --version` to check the stamped installer version without
doing anything; pin a specific release with
`get.asdl.website/v1.0.0/pocket-relay`). `server/install.sh` in this repo
is the source — the release pipeline stamps a version into it (see
[Releases](#releases)). `get.asdl.website` itself is infra shared across
several projects and isn't part of this repo.

Manual setup instead: install ntfy (https://ntfy.sh/docs/install/), copy
`server/ntfy/server.yml` to `/etc/ntfy/server.yml` with your own `base-url`,
`systemctl enable --now ntfy`, point a reverse proxy at its `listen-http`
address (default `127.0.0.1:2586`), get a TLS cert, then:
```
sudo ntfy user add --role=user forwarder
sudo ntfy access forwarder sms-forward rw
sudo ntfy token add forwarder
```

### 2. Android app

Open `android/` in Android Studio (or build with `./gradlew assembleDebug`),
install it on your phone, grant SMS permissions, disable battery
optimization for the app, and fill in the settings screen with your server
URL, topic, and token.

A GitHub Actions workflow (`.github/workflows/build-apk.yml`) also builds
the debug APK on every push and uploads it as a build artifact.

### 3. iOS app

Install the official **ntfy** app from the App Store, add a subscription
for your server URL and topic, and log in with the ntfy account's
username/password (or an `Authorization: Bearer <token>` custom header,
where supported).

## Security notes

- SMS content transits your own server only — the public ntfy.sh relay only
  ever sees a topic name and message ID, never the message body.
- The ntfy server's default auth policy is deny-all; nothing is readable or
  publishable without the scoped token/account.
- The control panel uses plain HTTP on your own hotspot/Wi-Fi network, protected by a PIN; only
  enable it if you trust the devices on that network.
- Treat the access token and account password as secrets — don't commit
  them. `android/local.properties` and build outputs are gitignored.

## Releases

Tagging a version (`vX.Y.Z`) triggers `.github/workflows/release.yml`, which
builds the APK and publishes a GitHub Release with two stamped assets:
- `install.sh` — `server/install.sh` with `@@VERSION@@` replaced by the tag
- `pocket-relay-vX.Y.Z.apk`

`https://get.asdl.website/pocket-relay` serves the `install.sh` from the
latest release, so the one-line install command always matches a tagged,
reproducible version rather than whatever's on `master`.

## License

MIT — see [LICENSE](LICENSE).
