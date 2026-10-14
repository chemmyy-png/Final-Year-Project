package com.example.wishieldrogueaccesspointdetectorapp.scan;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.example.wishieldrogueaccesspointdetectorapp.detect.RogueDetector;
import com.example.wishieldrogueaccesspointdetectorapp.model.AccessPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps the Wi-Fi scanning API so activities don't have to deal with
 * permissions, broadcast receivers, and the system's scan throttling.
 *
 * Usage: construct one, call {@link #start()} in onStart / onResume and
 * {@link #stop()} in onStop / onPause, then {@link #requestScan()} whenever
 * you want fresh results.
 */
public class WifiScanner {

    public interface Listener {
        /** Fresh, analysed results are ready. */
        void onScanComplete(List<AccessPoint> accessPoints);

        /** Something stopped the scan; {@code message} is user-facing. */
        void onScanFailed(String message);
    }

    private final Context context;
    private final WifiManager wifiManager;
    private final Listener listener;

    private boolean receiverRegistered = false;

    /**
     * SCAN_RESULTS_AVAILABLE_ACTION is a system-wide broadcast — it fires when
     * *any* app on the device (or the system itself) completes a scan, not just
     * ours. Without this flag the app would react to other apps' scans, which
     * on the splash screen means navigating to the dashboard on its own before
     * the user has pressed anything.
     */
    private boolean scanPending = false;

    /**
     * A scan normally completes in a few seconds. If the broadcast never
     * arrives — it does happen, e.g. when the Wi-Fi radio is busy or the
     * device is mid-roam — this fallback delivers whatever the system has
     * cached so the caller's spinner can't hang indefinitely.
     */
    private static final long SCAN_TIMEOUT_MS = 12_000L;

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    private final Runnable timeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (scanPending) {
                scanPending = false;
                deliverResults();
            }
        }
    };

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            if (!scanPending) {
                return; // somebody else's scan
            }
            scanPending = false;
            timeoutHandler.removeCallbacks(timeoutRunnable);
            deliverResults();
        }
    };

    public WifiScanner(Context context, Listener listener) {
        // Application context: this outlives individual activities and avoids
        // leaking one through the registered receiver.
        this.context = context.getApplicationContext();
        this.wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        this.listener = listener;
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------

    /**
     * On Android 13+ scanning is allowed with NEARBY_WIFI_DEVICES, which does
     * not imply location access. Below that, the platform requires a location
     * permission to hand over scan results at all.
     */
    public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.NEARBY_WIFI_DEVICES};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public static boolean hasPermissions(Context context) {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    public void start() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        // RECEIVER_NOT_EXPORTED is required from Android 13 and enforced with a
        // SecurityException from Android 14. ContextCompat applies an equivalent
        // guard on older versions, so this one call is correct everywhere.
        ContextCompat.registerReceiver(context, scanReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    public void stop() {
        if (!receiverRegistered) {
            return;
        }
        try {
            context.unregisterReceiver(scanReceiver);
        } catch (IllegalArgumentException ignored) {
            // Already unregistered — nothing to do.
        }
        receiverRegistered = false;
        scanPending = false;
        timeoutHandler.removeCallbacks(timeoutRunnable);
    }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    public void requestScan() {
        if (!hasPermissions(context)) {
            listener.onScanFailed("Permission needed to see nearby Wi-Fi networks.");
            return;
        }
        if (wifiManager == null || !wifiManager.isWifiEnabled()) {
            listener.onScanFailed("Turn on Wi-Fi to scan for access points.");
            return;
        }

        scanPending = true;
        timeoutHandler.removeCallbacks(timeoutRunnable);
        timeoutHandler.postDelayed(timeoutRunnable, SCAN_TIMEOUT_MS);

        @SuppressWarnings("deprecation")
        boolean started = wifiManager.startScan();

        // startScan() is deprecated and the platform throttles it hard (roughly
        // 4 calls per 2 minutes for a foreground app). There is no replacement
        // available to a normal app, so when the system refuses we fall back to
        // the most recent cached results rather than showing the user nothing.
        if (!started) {
            scanPending = false;
            timeoutHandler.removeCallbacks(timeoutRunnable);
            deliverResults();
        }
    }

    private void deliverResults() {
        if (!hasPermissions(context)) {
            listener.onScanFailed("Permission needed to see nearby Wi-Fi networks.");
            return;
        }

        List<ScanResult> raw;
        try {
            raw = wifiManager.getScanResults();
        } catch (SecurityException e) {
            listener.onScanFailed("Permission needed to see nearby Wi-Fi networks.");
            return;
        }

        if (raw == null) {
            raw = new ArrayList<>();
        }

        if (raw.isEmpty()) {
            // Almost always one of: location services switched off at the system
            // level (Android still requires this for scan results even when the
            // app has permission), or running on an emulator with no Wi-Fi radio.
            listener.onScanFailed("No networks found. Check that Location is turned on in"
                    + " system settings — Android needs it for Wi-Fi scan results.");
            return;
        }

        List<AccessPoint> analysed = RogueDetector.analyse(raw);
        ScanRepository.getInstance().setResults(analysed);
        listener.onScanComplete(analysed);
    }
}
