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
                    // The clearest evil-twin signature there is: same name as a
                    // protected network, but served with no working encryption.
                    // An attacker can copy an SSID for free; copying the
                    // passphrase is the part they can't do, so the clone runs
                    // open (or on WEP, which amounts to the same thing).
                    ap.flag(Verdict.ROGUE, "Another access point broadcasts \"" + ap.ssid
                            + "\" with real encryption, but this one is "
                            + ap.getSecurityLabel()
                            + " — a classic evil-twin pattern, where a copied network"
                            + " name is served without the real network's protection.");
                } else if (downgraded) {
                    // Both are encrypted, just to different standards. Very
                    // common and usually innocent: a single dual-band router
                    // often advertises WPA3 on 5 GHz and WPA2 on 2.4 GHz for
                    // older client devices. Worth noting, not worth alarming.
                    ap.flag(Verdict.SUSPICIOUS, "Another access point broadcasts \"" + ap.ssid
                            + "\" using stronger encryption than this one ("
                            + ap.getSecurityLabel() + "). Dual-band routers do this"
                            + " routinely for compatibility, so this is usually normal.");
                } else if (group.size() > 1) {
                    ap.flag(Verdict.SUSPICIOUS, group.size() + " access points broadcast the name \""
                            + ap.ssid + "\". This is completely normal for mesh Wi-Fi,"
                            + " offices and campuses, but it is also what a cloned network"
                            + " looks like. Worth confirming you expect that many.");
                }

                if (mixedVendors) {
                    // TextUtils.join, not String.join — the latter needs API 26
                    // and this project's minSdk is 24.
                    ap.flag(Verdict.SUSPICIOUS, "Access points using this name come from"
                            + " different hardware vendors (" + TextUtils.join(", ", vendors)
                            + "). Mixed hardware happens in real networks, but it also"
                            + " suggests one of them may not belong.");
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
            ap.flag(Verdict.SUSPICIOUS, "This network has no encryption. Anything you send"
                    + " over it that isn't itself encrypted can be read by anyone in range.");
        } else if ("WEP".equals(security)) {
            ap.flag(Verdict.SUSPICIOUS, "WEP encryption is broken and can be cracked in minutes"
                    + " with freely available tools. Treat it as effectively open.");
        }

        if (OuiLookup.isLocallyAdministered(ap.bssid)) {
            // Real AP hardware ships with a vendor-assigned, globally unique MAC.
            // A locally administered one is generated in software.
            ap.flag(Verdict.SUSPICIOUS, "This access point's MAC address is software-generated"
                    + " rather than assigned to real hardware. Phone hotspots and laptops"
                    + " sharing a connection look like this — and so do most improvised"
                    + " rogue access points.");

            // Software-generated MAC + no encryption + a name someone else is
            // already using is three independent red flags on one device.
            if (ap.isUnencrypted() && ap.duplicateName) {
                ap.flag(Verdict.ROGUE, "Taken together — a software-generated MAC, no encryption,"
                        + " and a name already in use nearby — this matches the profile of a"
                        + " rogue access point rather than ordinary network equipment.");
            }
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
