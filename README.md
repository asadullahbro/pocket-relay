# sms-forwarder

Forwards incoming SMS on an Android phone to push notifications on iOS, via a
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

Requires a host with a public IP/domain, a reverse proxy (nginx or similar)
terminating TLS, and a package manager (or Docker, if you prefer — this repo
assumes a native install via ntfy's `.deb`/`.rpm` releases).

1. Install ntfy: https://ntfy.sh/docs/install/
2. Copy `server/ntfy/server.yml` to `/etc/ntfy/server.yml`, replacing
   `base-url` with your own domain, then `systemctl enable --now ntfy`.
3. Point your reverse proxy at ntfy's `listen-http` address (e.g.
   `127.0.0.1:2586`) for your domain, and get a TLS cert (e.g. via certbot).
4. Create a scoped user and access token:
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
- Treat the access token and account password as secrets — don't commit
  them. `android/local.properties` and build outputs are gitignored.

## License

MIT — see [LICENSE](LICENSE).
