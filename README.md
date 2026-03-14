# Fortunate HTML

A network debugging proxy for Android with MITM (Man-in-the-Middle) interception capability. Capture, inspect, and analyze HTTP/HTTPS traffic directly on your Android device.

## ⚠️ Disclaimer

**This application is for authorized use only.** You must only use this application on domains, networks, and systems for which you have explicit written permission from the owner. Unauthorized interception of network traffic is illegal in most jurisdictions.

## Features

* 📱 Native Android application written in Kotlin
* 🔒 MITM proxy for HTTPS traffic interception
* 🌐 HTTP/HTTPS request and response capture
* 📋 Detailed traffic inspection (headers, body, status codes, timing)
* 🔑 Custom CA certificate generation and management
* 🛡️ VPN-based traffic routing — no root required
* ⚖️ Built-in legal disclaimer requiring user acceptance
* 🎨 Material Design UI with color-coded status indicators

## Project Structure

```
android/
├── app/
│   ├── build.gradle.kts          # App build configuration
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/fortunatehtml/android/
│   │   │   │   ├── FortunateHtmlApp.kt         # Application class
│   │   │   │   ├── data/                        # Data layer
│   │   │   │   │   ├── PreferencesManager.kt    # SharedPreferences wrapper
│   │   │   │   │   └── TrafficRepository.kt     # Traffic data store
│   │   │   │   ├── model/                       # Data models
│   │   │   │   │   └── TrafficEntry.kt          # Traffic entry model
│   │   │   │   ├── proxy/                       # Proxy engine
│   │   │   │   │   ├── CertificateManager.kt    # CA & host cert generation
│   │   │   │   │   ├── ProxyServer.kt           # HTTP/HTTPS proxy server
│   │   │   │   │   ├── ProxyVpnService.kt       # Android VPN service
│   │   │   │   │   └── SSLContextHelper.kt      # SSL utilities
│   │   │   │   └── ui/                          # UI layer
│   │   │   │       ├── DisclaimerActivity.kt    # Legal disclaimer screen
│   │   │   │       ├── MainActivity.kt          # Main traffic list
│   │   │   │       ├── TrafficAdapter.kt        # RecyclerView adapter
│   │   │   │       └── TrafficDetailActivity.kt # Request detail view
│   │   │   └── res/                             # Android resources
│   │   └── test/                                # Unit tests
│   └── proguard-rules.pro
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts
└── gradle.properties
```

## Building the APK

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 17+
- Android SDK 34

### Build Steps

1. Open the `android/` directory in Android Studio
2. Sync Gradle and let dependencies download
3. Build the project: **Build → Make Project**
4. Generate APK: **Build → Build Bundle(s) / APK(s) → Build APK(s)**

Or build from the command line:

```bash
cd android
./gradlew assembleDebug
```

The debug APK will be at `android/app/build/outputs/apk/debug/app-debug.apk`.

## How It Works

1. **Disclaimer**: On first launch, users must accept the legal disclaimer confirming they will only use the app on authorized domains
2. **VPN Service**: The app creates a local VPN to route device traffic through the proxy
3. **MITM Proxy**: A local proxy server intercepts HTTP and HTTPS connections using dynamically generated certificates
4. **Traffic Capture**: All intercepted requests/responses are displayed in real-time in the traffic list
5. **Certificate Installation**: Users can export the CA certificate to install on their device for HTTPS interception

## Setting Up HTTPS Interception

1. Start the proxy from the main screen (tap the play button)
2. Open the menu (⋮) and select **Export CA Certificate**
3. Install the exported certificate on your device:
   - Go to **Settings → Security → Install certificates**
   - Select the exported PEM file
4. HTTPS traffic will now be visible in the traffic list

## Running Tests

```bash
cd android
./gradlew test
```

## Have a Problem?

Open a GitHub ticket to report issues or request features.
