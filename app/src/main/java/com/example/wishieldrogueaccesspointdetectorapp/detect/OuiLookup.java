package com.example.wishieldrogueaccesspointdetectorapp.detect;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps the first three bytes of a MAC address (the OUI, assigned by the IEEE)
 * to a hardware vendor.
 *
 * IMPORTANT: this is a small hand-picked table, not the real IEEE registry,
 * which has tens of thousands of entries. A result of "Unknown" means only
 * "not in this table" — it is NOT evidence that anything is wrong, and the
 * detector never treats it as such. If you want real coverage, bundle the
 * IEEE OUI list as a CSV in assets/ and load it here instead.
 */
public final class OuiLookup {

    private static final Map<String, String> VENDORS = new HashMap<>();

    static {
        // Consumer router / AP vendors
        VENDORS.put("00:1A:2B", "Ayecom");
        VENDORS.put("A4:2B:B0", "TP-Link");
        VENDORS.put("50:C7:BF", "TP-Link");
        VENDORS.put("B0:BE:76", "TP-Link");
        VENDORS.put("C0:06:C3", "TP-Link");
        VENDORS.put("14:CC:20", "TP-Link");
        VENDORS.put("00:14:6C", "Netgear");
        VENDORS.put("A0:40:A0", "Netgear");
        VENDORS.put("2C:30:33", "Netgear");
        VENDORS.put("00:1D:7E", "Cisco-Linksys");
        VENDORS.put("00:22:6B", "Cisco-Linksys");
        VENDORS.put("48:F8:B3", "Cisco-Linksys");
        VENDORS.put("00:0C:29", "VMware");
        VENDORS.put("00:15:6D", "Ubiquiti");
        VENDORS.put("24:A4:3C", "Ubiquiti");
        VENDORS.put("78:8A:20", "Ubiquiti");
        VENDORS.put("FC:EC:DA", "Ubiquiti");
        VENDORS.put("00:1B:63", "Apple");
        VENDORS.put("A4:5E:60", "Apple");
        VENDORS.put("3C:15:C2", "Apple");
        VENDORS.put("F0:18:98", "Apple");
        VENDORS.put("00:26:BB", "Apple");
        VENDORS.put("00:E0:4C", "Realtek");
        VENDORS.put("18:A6:F7", "TP-Link");
        VENDORS.put("D8:47:32", "TP-Link");
        VENDORS.put("00:26:5A", "D-Link");
        VENDORS.put("1C:7E:E5", "D-Link");
        VENDORS.put("78:54:2E", "D-Link");
        VENDORS.put("00:1E:58", "D-Link");
        VENDORS.put("00:24:01", "D-Link");
        VENDORS.put("BC:F6:85", "D-Link");
        VENDORS.put("00:17:9A", "D-Link");
        VENDORS.put("04:BF:6D", "Zyxel");
        VENDORS.put("5C:F4:AB", "Zyxel");
        VENDORS.put("00:13:49", "Zyxel");
        VENDORS.put("38:2C:4A", "Asus");
        VENDORS.put("04:D4:C4", "Asus");
        VENDORS.put("2C:56:DC", "Asus");
        VENDORS.put("1C:B7:2C", "Asus");
        VENDORS.put("AC:9E:17", "Asus");
        VENDORS.put("00:1F:33", "Netgear");
        VENDORS.put("28:C6:8E", "Netgear");
        VENDORS.put("9C:3D:CF", "Netgear");

        // Enterprise / carrier gear
        VENDORS.put("00:0B:86", "Aruba Networks");
        VENDORS.put("6C:F3:7F", "Aruba Networks");
        VENDORS.put("00:1C:B3", "Cisco");
        VENDORS.put("00:23:04", "Cisco");
        VENDORS.put("58:97:1E", "Cisco");
        VENDORS.put("00:3A:9A", "Cisco");
        VENDORS.put("E0:CB:4E", "Huawei");
        VENDORS.put("00:25:9E", "Huawei");
        VENDORS.put("48:46:FB", "Huawei");
        VENDORS.put("70:72:3C", "Huawei");
        VENDORS.put("D4:6E:0E", "TP-Link");
        VENDORS.put("00:9A:CD", "Huawei");
        VENDORS.put("80:B6:86", "Huawei");
        VENDORS.put("18:28:61", "Xiaomi");
        VENDORS.put("64:09:80", "Xiaomi");
        VENDORS.put("50:8F:4C", "Xiaomi");
        VENDORS.put("8C:BE:BE", "Xiaomi");

        // Phone vendors — a hotspot from one of these is normal, but useful to name
        VENDORS.put("00:23:76", "HTC");
        VENDORS.put("40:4E:36", "HTC");
        VENDORS.put("F8:E0:79", "Motorola");
        VENDORS.put("00:24:E4", "Samsung");
        VENDORS.put("5C:0A:5B", "Samsung");
        VENDORS.put("78:47:1D", "Samsung");
        VENDORS.put("E8:50:8B", "Samsung");
        VENDORS.put("D0:22:BE", "Samsung");
        VENDORS.put("A0:21:B7", "Netgear");
        VENDORS.put("40:B0:76", "Asus");
    }

    private OuiLookup() {
    }

    /** Returns a vendor name, or "Unknown" when the OUI isn't in the table. */
    public static String vendorOf(String bssid) {
        if (bssid == null || bssid.length() < 8) {
            return "Unknown";
        }
        String oui = bssid.substring(0, 8).toUpperCase(Locale.US);
        String vendor = VENDORS.get(oui);
        return vendor != null ? vendor : "Unknown";
    }

    /**
     * True when the MAC has the "locally administered" bit set (bit 1 of the
     * first octet).
     *
     * Physical access-point hardware ships with a globally unique MAC bought
     * from the IEEE. A locally administered one is generated in software —
     * which is what phone hotspots, laptops running hostapd, and most
     * evil-twin setups produce. On its own this is a hint, not proof: Android
     * and iOS also randomise MACs for privacy, and some legitimate mesh
     * systems use locally administered addresses for their virtual APs.
     */
    public static boolean isLocallyAdministered(String bssid) {
        if (bssid == null || bssid.length() < 2) {
            return false;
        }
        try {
            int firstOctet = Integer.parseInt(bssid.substring(0, 2), 16);
            return (firstOctet & 0x02) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
