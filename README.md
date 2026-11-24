# Aether-Mesh

Zero-Infrastructure Tactical Emergency & Multi-Radio Mesh Command Center built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **Firebase AI Logic**.

- **Application ID:** `com.aethermesh.tactical` (`minSdk 24`, `targetSdk 36`)
- **APK:** `aether-mesh-debug.apk` (repository root)

---

## Core Architecture

- **`MainActivity.kt`**: Bootstraps Firebase App Check (`configureAppCheck`) and edge-to-edge dark UI before rendering `AetherMeshApp`.
- **`AetherMeshViewModel.kt`**: Coordinates real multi-radio discovery/transport, adaptive Dijkstra routing, AES-128-CBC packet inspection, acoustic FSK transmission, and streaming triage AI via `StateFlow`.

---

## UI & Cupertino Liquid Glass (`ui/`)

- **Optical Shader Engine (`LiquidGlass.kt`)**:
  - **Android 13+ (API 33+)**: Chains GPU `RenderEffect` blur (`24px`) with a custom AGSL `RuntimeShader` for rounded-bevel Snell refraction, liquid ripple distortion, chromatic RGB edge dispersion, and Fresnel specular reflections over a live caustic background (`LiquidAmbientBackground`).
  - **Android 12 (API 31–32)**: Hardware `RenderEffect` blur fallback.
  - **Android 7–11 / Battery Saver**: Lightweight translucent gradients.
- **Adaptive Layouts (`AetherMeshApp.kt`)**: Compact 4-tab layout (**Mesh**, **Telemetry**, **Acoustic**, **Medic AI**) with a floating Liquid Glass dock on phones, expanding to a 3-column command center on tablets (`>= 900dp`).
- **Assets**: Bundled `Space Grotesk` & `JetBrains Mono` fonts (`res/font/`) and a Cupertino-style liquid-orb adaptive launcher icon (`mdpi`–`xxxhdpi`).

---

## Multi-Radio Mesh & Routing (`transport/`, `mesh/`)

Zero simulated/fake nodes — displays only the host device (`YOU`) and real discovered peers:

| Radio Mode | Mechanism | Details |
| :--- | :--- | :--- |
| **LAN / Hotspot** | `LanMeshTransport` | UDP broadcast discovery (`:45872`) + TCP socket messaging (`:45873`) |
| **BLE & HypRange** | `BleMeshTransport` | GATT Server/Client (`0000AE74` / `0000AE75`); **HypRange** enables BLE 5+ Coded PHY (`PHY_LE_CODED`) advertising, scanning, and 1.45× link horizon |
| **Bluetooth Classic** | `BtClassicMeshTransport` | Insecure RFCOMM/SPP (`0000AE76`) discovery and stream transport |
| **Wi-Fi Direct** | `WifiP2pMeshTransport` | `WifiP2pManager` peer scan, Group Owner hosting, and P2P TCP routing |

- **Adaptive Rerouting (`MeshRouter.kt`)**: Computes shortest-latency paths with Dijkstra, detects broken links on peer mute/blackout, reroutes traffic automatically, and logs hop efficiency (`0–99%`).
- **Tactical Crowd-Pins**: Drops hazard markers (`BLK-RD`, `MED-AID`, `SHELTER`, `H2O-OK`) and syncs encrypted coordinates across peers.

---

## Security, Acoustic Modem & Field Medic AI

- **Encryption (`TacticalCrypto.kt`)**: `AES/CBC/PKCS5Padding` (`AES-128`) with random 16-byte IV per packet (`AETHER_AES128_V1` wire envelope), live hex/JSON inspector, and wrong-key tamper verification toggle.
- **Acoustic Fallback Modem (`AcousticFskModem.kt`)**: Real-time `AudioTrack` (`44.1 kHz` PCM) FSK synthesis (`1.2/2.2 kHz` audible or `6.8/8.2 kHz` high-band @ ~62.5 bps) with live waveform, binary bitstream, and ITU Morse encoder.
- **Survival AI (`SurvivalAiRepository.kt`, `AppCheckConfig.kt`)**: Streaming emergency protocols powered by **Firebase AI Logic** (`gemini-3.8-flash`) protected by Firebase App Check (Debug / ReCAPTCHA).
- **Permissions (`AndroidManifest.xml`)**: Scoped with `neverForLocation` on `BLUETOOTH_SCAN` and `NEARBY_WIFI_DEVICES`, and `maxSdkVersion="32"` on `ACCESS_FINE_LOCATION`.

---

## Verification (`app/src/test/java/com/example/`)

Verified with `:app:testDebugUnitTest` (Robolectric SDK 36) and `:app:assembleDebug`:
- `AetherMeshLogicTest`: AES-128 wire roundtrip & rogue-key rejection, adaptive Dijkstra rerouting, FSK binary/Morse encoding, RSSI distance estimation, and HypRange Coded PHY link extension.
- `ExampleRobolectricTest` & `ExampleUnitTest`: App context resource resolution and baseline checks.
