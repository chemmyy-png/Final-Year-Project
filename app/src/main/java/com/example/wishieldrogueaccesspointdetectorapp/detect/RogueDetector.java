package com.example.wishieldrogueaccesspointdetectorapp.detect;

import android.net.wifi.ScanResult;
import android.text.TextUtils;

import com.example.wishieldrogueaccesspointdetectorapp.model.AccessPoint;
import com.example.wishieldrogueaccesspointdetectorapp.model.Verdict;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which nearby access points look like rogue / evil-twin APs.
 *
 * Everything here is passive: it reasons only about the beacon information
 * every access point already broadcasts to everyone in range. The app does not
 * connect to, deauthenticate, probe, or transmit anything toward any network.
 *
 * <h3>What an evil twin actually is</h3>
 * An attacker stands up an access point advertising the same SSID as a network
 * you trust, hoping your phone (or you) connects to theirs instead. They can
 * copy the name freely, but they usually cannot copy the passphrase — so the
 * clone very often runs open, or with weaker encryption than the real one.
 * That mismatch is the single most reliable signal available from a passive
 * scan, and it's what {@link #checkSsidGroups} looks for.
 *
 * <h3>Honest limitations — read before trusting a verdict</h3>
 * These are heuristics, not proof:
 * <ul>
 *   <li>Every large Wi-Fi deployment (campus, hotel, office, home mesh) runs
 *       many access points under one SSID. That is normal and is exactly what
 *       an evil twin looks like from outside, which is why a plain duplicate
 *       is only ever SUSPICIOUS here, never ROGUE.</li>
 *   <li>A patient attacker can match the encryption type and spoof a
 *       vendor-looking MAC, which defeats most of these checks.</li>
 *   <li>Nothing here detects an attacker who is passively listening rather
 *       than broadcasting.</li>
 * </ul>
 * Treat a flag as "look into this", and SAFE as "nothing obvious", not as a
 * security guarantee.
 */
public final class RogueDetector {

    private RogueDetector() {
    }

    /**
     * Converts raw scan results into analysed access points.
     * Duplicate BSSIDs (the same radio seen twice) are collapsed, keeping the
     * strongest reading.
     */
    public static List<AccessPoint> analyse(List<ScanResult> results) {
        Map<String, AccessPoint> byBssid = new LinkedHashMap<>();

        for (ScanResult result : results) {
            if (result == null || result.BSSID == null) {
                continue;
            }
            AccessPoint incoming = AccessPoint.from(result);
            AccessPoint existing = byBssid.get(incoming.bssid);
            if (existing == null || incoming.level > existing.level) {
                byBssid.put(incoming.bssid, incoming);
            }
        }

        List<AccessPoint> accessPoints = new ArrayList<>(byBssid.values());

        for (AccessPoint ap : accessPoints) {
            ap.vendor = OuiLookup.vendorOf(ap.bssid);
        }

        checkSsidGroups(accessPoints);
        for (AccessPoint ap : accessPoints) {
            checkIndividual(ap);
        }

        for (AccessPoint ap : accessPoints) {
            if (ap.verdict == Verdict.SAFE) {
                checkSafeHighlights(ap);
            }
        }

        sortBySeverityThenSignal(accessPoints);
        return accessPoints;
    }

    // ------------------------------------------------------------------
    // Check 1: networks sharing a name (the evil-twin check)
    // ------------------------------------------------------------------

    private static void checkSsidGroups(List<AccessPoint> accessPoints) {
        Map<String, List<AccessPoint>> groups = new LinkedHashMap<>();
        for (AccessPoint ap : accessPoints) {
            if (ap.isHidden()) {
                continue; // hidden APs have no name to collide on
            }
            List<AccessPoint> group = groups.get(ap.ssid);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(ap.ssid, group);
            }
            group.add(ap);
        }

        for (Map.Entry<String, List<AccessPoint>> entry : groups.entrySet()) {
            List<AccessPoint> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }

            // Find the strongest encryption anyone in this group is using.
            int strongest = 0;
            for (AccessPoint ap : group) {
                strongest = Math.max(strongest, ap.getSecurityStrength());
            }

            Set<String> vendors = new HashSet<>();
            for (AccessPoint ap : group) {
                if (!"Unknown".equals(ap.vendor)) {
                    vendors.add(ap.vendor);
                }
            }
            boolean mixedVendors = vendors.size() > 1;

            for (AccessPoint ap : group) {
                ap.duplicateName = true;
                boolean downgraded = ap.getSecurityStrength() < strongest;

                if (downgraded && ap.isUnencrypted()) {
                    ap.flag(Verdict.ROGUE, "Fake Network Alert: Copies a protected network name, but requires NO password.");
                } else if (downgraded) {
                    ap.flag(Verdict.SUSPICIOUS, "Security Difference: Another device nearby uses this name with stronger protection (" + ap.getSecurityLabel() + ").");
                } else if (group.size() > 1) {
                    ap.flag(Verdict.SUSPICIOUS, "Multiple Devices: " + group.size() + " devices share the name \"" + ap.ssid + "\". Common in office/mesh Wi-Fi.");
                }

                if (mixedVendors) {
                    ap.flag(Verdict.SUSPICIOUS, "Mixed Brands: Devices with this name come from different brands (" + TextUtils.join(", ", vendors) + ").");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Check 2: per-access-point properties
    // ------------------------------------------------------------------

    private static void checkIndividual(AccessPoint ap) {
        String security = ap.getSecurityLabel();

        if ("Open".equals(security)) {
            ap.flag(Verdict.SUSPICIOUS, "No Password: Open network without protection. Anyone nearby can see what you send.");
        } else if ("WEP".equals(security)) {
            ap.flag(Verdict.SUSPICIOUS, "Weak Security: Uses outdated WEP security that hackers can easily crack.");
        }

        if (OuiLookup.isLocallyAdministered(ap.bssid)) {
            ap.flag(Verdict.SUSPICIOUS, "Hotspot/Software Address: Broadcast from a mobile hotspot or software tool rather than a physical router.");

            if (ap.isUnencrypted() && ap.duplicateName) {
                ap.flag(Verdict.ROGUE, "Fake Wi-Fi Warning: Unprotected network copying a nearby Wi-Fi name from a hotspot device.");
            }
        }
    }

    // ------------------------------------------------------------------
    // Check 3: positive safety highlights for safe networks
    // ------------------------------------------------------------------

    private static void checkSafeHighlights(AccessPoint ap) {
        String security = ap.getSecurityLabel();
        if ("WPA3".equals(security) || "WPA2".equals(security)) {
            ap.reasons.add("Strong Encryption: Protected with " + security + " password security.");
        } else if ("WPA".equals(security)) {
            ap.reasons.add("Encrypted Connection: Protected with WPA password security.");
        } else if ("Enhanced Open".equals(security)) {
            ap.reasons.add("Encrypted Connection: Uses Enhanced Open (OWE) wireless encryption.");
        }

        if (!ap.duplicateName) {
            ap.reasons.add("Unique Name: No duplicate or copied network names detected nearby.");
        } else {
            ap.reasons.add("Multi-AP Setup: Shares name with matching secure routers nearby.");
        }

        if (!OuiLookup.isLocallyAdministered(ap.bssid) && !"Unknown".equals(ap.vendor)) {
            ap.reasons.add("Hardware Verified: Broadcast from recognized " + ap.vendor + " router equipment.");
        } else if (!OuiLookup.isLocallyAdministered(ap.bssid)) {
            ap.reasons.add("Standard Hardware Address: Uses a valid physical hardware address.");
        }
    }

    // ------------------------------------------------------------------
    // Ordering: worst first, then strongest signal
    // ------------------------------------------------------------------

    private static void sortBySeverityThenSignal(List<AccessPoint> accessPoints) {
        java.util.Collections.sort(accessPoints, (a, b) -> {
            int bySeverity = b.verdict.ordinal() - a.verdict.ordinal();
            if (bySeverity != 0) {
                return bySeverity;
            }
            return b.level - a.level; // -45 dBm sorts above -80 dBm
        });
    }

    // ------------------------------------------------------------------
    // Summary counts for the dashboard stat cards
    // ------------------------------------------------------------------

    public static class Summary {
        public int total;
        public int safe;
        public int flagged; // SUSPICIOUS + ROGUE
    }

    public static Summary summarise(List<AccessPoint> accessPoints) {
        Summary summary = new Summary();
        summary.total = accessPoints.size();
        for (AccessPoint ap : accessPoints) {
            if (ap.verdict == Verdict.SAFE) {
                summary.safe++;
            } else {
                summary.flagged++;
            }
        }
        return summary;
    }
}
