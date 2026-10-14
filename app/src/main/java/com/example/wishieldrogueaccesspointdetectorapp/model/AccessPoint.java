package com.example.wishieldrogueaccesspointdetectorapp.model;

import android.net.wifi.ScanResult;

import java.util.ArrayList;
import java.util.List;

/**
 * One access point seen in a Wi-Fi scan, plus whatever the detector concluded
 * about it.
 *
 * Everything here comes from the beacon frames an access point broadcasts
 * publicly — the app never connects to, probes, or sends anything to these
 * networks.
 */
public class AccessPoint {

    public final String ssid;          // network name ("" when hidden)
    public final String bssid;         // the AP's MAC address
    public final String capabilities;  // raw security string, e.g. "[WPA2-PSK-CCMP][ESS]"
    public final int level;            // RSSI in dBm (closer to 0 = stronger)
    public final int frequency;        // MHz

    public String vendor = "Unknown";
    public Verdict verdict = Verdict.SAFE;

    /** True when at least one other access point in range broadcasts this SSID. */
    public boolean duplicateName = false;

    /** Plain-English explanations for the verdict, shown in the details dialog. */
    public final List<String> reasons = new ArrayList<>();

    public AccessPoint(String ssid, String bssid, String capabilities, int level, int frequency) {
        this.ssid = ssid == null ? "" : ssid;
        this.bssid = bssid == null ? "" : bssid;
        this.capabilities = capabilities == null ? "" : capabilities;
        this.level = level;
        this.frequency = frequency;
    }

    public static AccessPoint from(ScanResult result) {
        return new AccessPoint(
                result.SSID,
                result.BSSID,
                result.capabilities,
                result.level,
                result.frequency);
    }

    /** Raises the verdict only if {@code candidate} is more severe, and records why. */
    public void flag(Verdict candidate, String reason) {
        if (candidate.ordinal() > verdict.ordinal()) {
            verdict = candidate;
        }
        if (reason != null && !reasons.contains(reason)) {
            reasons.add(reason);
        }
    }

    public boolean isHidden() {
        return ssid.isEmpty();
    }

    public String getDisplaySsid() {
        return isHidden() ? "(Hidden network)" : ssid;
    }

    /**
     * Reads the raw capabilities string into a readable label.
     * Order matters — the strongest / most specific markers are checked first,
     * because a WPA3 beacon can also advertise WPA2 for backwards compatibility.
     */
    public String getSecurityLabel() {
        if (capabilities.contains("SAE") || capabilities.contains("WPA3")) return "WPA3";
        if (capabilities.contains("OWE")) return "Enhanced Open";
        if (capabilities.contains("WPA2") || capabilities.contains("RSN")) return "WPA2";
        if (capabilities.contains("WPA")) return "WPA";
        if (capabilities.contains("WEP")) return "WEP";
        return "Open";
    }

    /** True when traffic on this network isn't encrypted at the link layer. */
    public boolean isUnencrypted() {
        String security = getSecurityLabel();
        return security.equals("Open") || security.equals("WEP");
    }

    /**
     * Rough strength ranking used to spot an encryption downgrade between two
     * access points sharing one name. Higher is stronger.
     */
    public int getSecurityStrength() {
        switch (getSecurityLabel()) {
            case "WPA3":          return 4;
            case "WPA2":          return 3;
            case "WPA":           return 2;
            case "Enhanced Open": return 1;
            case "WEP":           return 0;
            default:              return 0; // Open
        }
    }

    /** Signal bars 0-4, for the UI. */
    public int getSignalBars() {
        if (level >= -55) return 4;
        if (level >= -67) return 3;
        if (level >= -78) return 2;
        if (level >= -90) return 1;
        return 0;
    }

    public String getBand() {
        if (frequency >= 5925) return "6 GHz";
        if (frequency >= 4900) return "5 GHz";
        return "2.4 GHz";
    }
}
