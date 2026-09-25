# WiShield: Rogue AP Detector

**WiShield** is an Android-based security application designed to detect and mitigate rogue access points (such as "Evil Twin" attacks) in public Wi-Fi environments. Operating completely offline, WiShield passively scans the local radio frequency spectrum, evaluates beacon frame attributes, and calculates real-time risk scores to protect user devices before a connection is established.

---

## Key Features

* **Passive Offline Scanning:** Scans 2.4 GHz and 5 GHz frequency bands over-the-air using the native Android `WifiManager` API without requiring an active internet connection or authenticating to suspicious networks.
* **Evil Twin Detection:** Groups broadcasting networks by SSID to identify duplicate network names and flag potential unauthorized clones.
* **Beacon Fingerprint Analysis:** Extracts and compares parameters across IEEE 802.11 beacon frames (BSSID, MAC Vendor, RSSI signal behavior, and encryption protocols) to detect network anomalies.
* **Dynamic Risk Scoring:** Runs an internal risk-assessment algorithm locally on the device to grade surrounding access points based on threat level.
* **Proactive Connection Blocking:** Automatically triggers protection mechanisms to block connection attempts to high-risk access points.
* **Real-Time Dashboard:** Displays live network scan results, threat levels, and security alerts via an intuitive Android user interface.

---

## Architecture & System Flow

```
[ Nearby Wi-Fi Spectrum ]
           │
           ▼ (Passive IEEE 802.11 Beacon Capture)
[ Android WifiManager API ]
           │
           ▼ (Raw ScanResult Object Extraction)
[ SSID Grouping & Fingerprint Engine ]
           │
           ▼ (MAC, RSSI, and Security Parameter Evaluation)
[ Local Risk Scoring Algorithm ]
           │
           ├──► [ High Risk ] ──► Trigger Active Block & Alert User
           └──► [ Low Risk ]  ──► Classify Safe & Update Dashboard UI
```

---

## Detection Parameters & Risk Factors

| Parameter | Technical Usage |
| :--- | :--- |
| **SSID** | Identifies duplicate network broadcast names to flag potential clone attempts. |
| **BSSID / MAC Address** | Extracts the hardware address to perform vendor lookup and cross-verify physical hardware consistency. |
| **RSSI** | Tracks signal strength anomalies and patterns (e.g., an unnaturally strong signal competing with a known legitimate AP). |
| **Capabilities / Security** | Flags protocol mismatches, such as a rogue twin advertising an open or altered security configuration compared to the original network. |
| **Beacon Frame Parameters** | Captures metadata from IEEE 802.11 beacon frames to construct a unique network fingerprint for anomaly evaluation. |

---

## Tech Stack & Requirements

* **Language:** Java
* **Platform:** Android (Android SDK)
* **Core API:** Native Android `WifiManager` API (`ScanResult`)
* **Development Environment:** Android Studio
* **Hardware & Testing Setup:**
  * **Client Device:** Android Smartphone
  * **Testbed Environment:** M5 Cardputer running Bruce Firmware (used strictly in controlled environments to simulate Evil Twin broadcasts)
