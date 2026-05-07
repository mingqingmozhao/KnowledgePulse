# KnowledgePulse Mobile App

This frontend keeps the normal web build and adds two mobile paths:

- PWA: browser install to home screen.
- Capacitor Android: native Android shell wrapping the same Vue app.

## Web/PWA

```bash
npm run build
npm run preview
```

The production build includes:

- `public/manifest.webmanifest`
- `public/sw.js`
- install icons under `public/pwa/`

The web app still works through the same deployed URL. PWA installation requires HTTPS, except for `localhost`.

## Android App

Set a backend URL that the phone can reach. Do not use `localhost` unless the backend runs on the phone.

```bash
set VITE_API_BASE_URL=https://your-domain.example/api/v1
npm run cap:android
npm run cap:open:android
```

Then build or run from Android Studio.

For local network testing, use a LAN IP or tunnel:

```bash
set VITE_API_BASE_URL=http://192.168.1.10:8080/api/v1
npm run cap:run:android
```

Recommended local Android workflow:

```bash
copy .env.mobile.example .env.mobile
```

Edit `.env.mobile` and replace `192.168.1.10` with the computer IPv4 address on the same Wi-Fi:

```env
PUBLIC_APP_URL=http://192.168.1.10:5173
VITE_API_BASE_URL=http://192.168.1.10:8080/api/v1
```

Then rebuild and sync the Android project:

```bash
npm run cap:android:mobile
npm run cap:open:android
```

After that, rebuild and reinstall the APK from Android Studio.

If you want to run directly to a connected phone or emulator:

```bash
npm run cap:run:android:mobile
```

For local HTTP testing, Android cleartext traffic is enabled in `android/app/src/main/AndroidManifest.xml`. For production or ngrok, prefer HTTPS.

## Ngrok Mobile Testing

Use this when the phone cannot reach the computer through LAN, for example phone hotspot plus computer ethernet.

Start a backend tunnel:

```bash
ngrok http 8080
```

Copy the HTTPS URL and set:

```env
VITE_API_BASE_URL=https://your-ngrok-domain.ngrok-free.dev/api/v1
```

Then rebuild and reinstall:

```bash
npm run cap:android:mobile
cd android
.\gradlew.bat assembleDebug
```

The App can run without USB as long as ngrok and the backend are still running. Free ngrok URLs may change after restart, so rebuild the APK when the ngrok URL changes.

## Android Troubleshooting

### `e.map is not a function`

This usually means the App called the wrong API address and received HTML, an error page, or another non-array payload. In a phone App:

- `localhost` means the phone itself, not the computer.
- `/api/v1` is relative to the Capacitor WebView, not the Vite dev server proxy.
- Use a LAN IP, ngrok URL, or deployed HTTPS domain in `VITE_API_BASE_URL`.

Check these before rebuilding:

- The phone and computer are on the same Wi-Fi when using `http://192.168.x.x:8080/api/v1`.
- The backend is reachable from another device, not only from the computer.
- Windows Firewall allows the backend port, usually `8080`.
- If using ngrok, set `VITE_API_BASE_URL=https://your-ngrok-domain/api/v1`.

## Android SDK Requirement

The native Gradle build needs Android SDK. If Gradle reports `SDK location not found`, copy:

```bash
android/local.properties.example
```

to:

```bash
android/local.properties
```

and update `sdk.dir` to your local Android SDK path.

## iOS Note

iOS packaging requires macOS with Xcode. The same Capacitor config can be used later with:

```bash
npm install @capacitor/ios
npx cap add ios
npm run build
npx cap sync ios
```
