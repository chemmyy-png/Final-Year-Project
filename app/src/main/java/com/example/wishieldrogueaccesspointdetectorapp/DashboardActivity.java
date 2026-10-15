package com.example.wishieldrogueaccesspointdetectorapp;

import android.content.Intent;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wishieldrogueaccesspointdetectorapp.detect.RogueDetector;
import com.example.wishieldrogueaccesspointdetectorapp.model.AccessPoint;
import com.example.wishieldrogueaccesspointdetectorapp.model.Verdict;
import com.example.wishieldrogueaccesspointdetectorapp.scan.ScanRepository;
import com.example.wishieldrogueaccesspointdetectorapp.scan.WifiScanner;
import com.example.wishieldrogueaccesspointdetectorapp.ui.NetworkAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity
        implements WifiScanner.Listener, NetworkAdapter.OnCardActionListener {

    private static final int PERMISSION_REQUEST = 2001;

    private enum Filter { ALL, SAFE, FLAGGED }

    private WifiScanner scanner;
    private NetworkAdapter adapter;
    private Filter activeFilter = Filter.ALL;
    private boolean initialScanPending = true;

    private List<AccessPoint> allResults = new ArrayList<>();

    private TextView statTotal, statSafe, statFlagged;
    private TextView tabAll, tabSafe, tabFlagged;
    private TextView emptyState;
    private RecyclerView recyclerView;
    private MaterialButton btnRescan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        statTotal = findViewById(R.id.statTotal);
        statSafe = findViewById(R.id.statSafe);
        statFlagged = findViewById(R.id.statFlagged);

        tabAll = findViewById(R.id.tabAll);
        tabSafe = findViewById(R.id.tabSafe);
        tabFlagged = findViewById(R.id.tabFlagged);

        emptyState = findViewById(R.id.emptyState);
        recyclerView = findViewById(R.id.networkList);
        btnRescan = findViewById(R.id.btnRescan);

        adapter = new NetworkAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        scanner = new WifiScanner(this, this);

        btnRescan.setOnClickListener(v -> startScan());
        tabAll.setOnClickListener(v -> applyFilter(Filter.ALL));
        tabSafe.setOnClickListener(v -> applyFilter(Filter.SAFE));
        tabFlagged.setOnClickListener(v -> applyFilter(Filter.FLAGGED));

        // MainActivity already scanned before navigating here, so show those
        // results immediately rather than making the user wait again.
        allResults = ScanRepository.getInstance().getResults();
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        scanner.start();

        // The initial scan has to happen after scanner.start() has registered
        // the broadcast receiver — kicking it off in onCreate would race the
        // registration and the results could arrive with nothing listening.
        if (initialScanPending) {
            initialScanPending = false;
            if (allResults.isEmpty()) {
                // Empty means the process was restarted or the user landed here
                // directly, so there is nothing cached to show.
                startScan();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        scanner.stop();
    }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    private void startScan() {
        if (!WifiScanner.hasPermissions(this)) {
            ActivityCompat.requestPermissions(this, WifiScanner.requiredPermissions(),
                    PERMISSION_REQUEST);
            return;
        }

        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isLocationEnabled = false;
        if (locationManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLocationEnabled = locationManager.isLocationEnabled();
            } else {
                isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (!isLocationEnabled) {
            Toast.makeText(this, "Please enable Location/GPS to scan Wi-Fi networks.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        btnRescan.setEnabled(false);
        btnRescan.setText(R.string.scanning);
        scanner.requestScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (WifiScanner.hasPermissions(this)) {
                startScan();
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
                render();
            }
        }
    }

    @Override
    public void onScanComplete(List<AccessPoint> accessPoints) {
        runOnUiThread(() -> {
            allResults = accessPoints;
            resetRescanButton();
            render();
        });
    }

    @Override
    public void onScanFailed(String message) {
        runOnUiThread(() -> {
            resetRescanButton();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            render();
        });
    }

    private void resetRescanButton() {
        btnRescan.setEnabled(true);
        btnRescan.setText(R.string.rescan_network);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void applyFilter(Filter filter) {
        activeFilter = filter;
        render();
    }

    private void render() {
        RogueDetector.Summary summary = RogueDetector.summarise(allResults);
        statTotal.setText(String.valueOf(summary.total));
        statSafe.setText(String.valueOf(summary.safe));
        statFlagged.setText(String.valueOf(summary.flagged));

        styleTab(tabAll, activeFilter == Filter.ALL);
        styleTab(tabSafe, activeFilter == Filter.SAFE);
        styleTab(tabFlagged, activeFilter == Filter.FLAGGED);

        List<AccessPoint> visible = new ArrayList<>();
        for (AccessPoint ap : allResults) {
            boolean flagged = ap.verdict != Verdict.SAFE;
            if (activeFilter == Filter.ALL
                    || (activeFilter == Filter.SAFE && !flagged)
                    || (activeFilter == Filter.FLAGGED && flagged)) {
                visible.add(ap);
            }
        }
        adapter.submit(visible);

        boolean nothingToShow = visible.isEmpty();
        emptyState.setVisibility(nothingToShow ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(nothingToShow ? View.GONE : View.VISIBLE);

        if (nothingToShow) {
            emptyState.setText(allResults.isEmpty()
                    ? getString(R.string.empty_no_scan)
                    : getString(R.string.empty_no_match));
        }
    }

    private void styleTab(TextView tab, boolean selected) {
        tab.setBackgroundResource(selected
                ? R.drawable.rescan_button_background
                : R.drawable.filter_tab_background);
        tab.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.white_text : R.color.secondary_white));
    }

    // ------------------------------------------------------------------
    // Card actions
    // ------------------------------------------------------------------

    @Override
    public void onShowReasons(AccessPoint ap) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_security_block);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        // Telemetry breakdown
        StringBuilder body = new StringBuilder();
        body.append("SSID: ").append(ap.getDisplaySsid()).append("\n")
                .append("BSSID: ").append(ap.bssid).append(" (").append(ap.vendor).append(")\n")
                .append("Security: ").append(ap.getSecurityLabel()).append(" · ").append(ap.getBand()).append("\n")
                .append("Signal: ").append(ap.level).append(" dBm\n\n");

        if (ap.reasons != null && !ap.reasons.isEmpty()) {
            body.append("Security Findings:\n");
            for (String reason : ap.reasons) {
                body.append("• ").append(reason).append("\n");
            }
        } else {
            body.append("Heuristic engine detected anomalies on this network profile.");
        }

        // Bind using the exact XML IDs
        TextView tvDetails = dialog.findViewById(R.id.tvThreatDetails);
        com.google.android.material.button.MaterialButton btnKeepBlocked = dialog.findViewById(R.id.btnKeepBlocked);
        TextView tvOverrideConnect = dialog.findViewById(R.id.tvOverrideConnect);

        if (tvDetails != null) {
            tvDetails.setText(body.toString());
        }

        if (btnKeepBlocked != null) {
            btnKeepBlocked.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(this, "Connection safely prevented.", Toast.LENGTH_SHORT).show();
            });
        }

        if (tvOverrideConnect != null) {
            tvOverrideConnect.setOnClickListener(v -> {
                dialog.dismiss();
                Toast.makeText(this, "Proceeding with override...", Toast.LENGTH_SHORT).show();
                onConnect(ap);
            });
        }

        dialog.show();
    }

    @Override
    public void onConnect(AccessPoint ap) {
        // Android no longer lets an app silently join an arbitrary network, so
        // the honest thing is to hand the user to the system Wi-Fi picker.
        try {
            Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? new Intent(Settings.Panel.ACTION_WIFI)
                    : new Intent(Settings.ACTION_WIFI_SETTINGS);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.cannot_open_wifi_settings, Toast.LENGTH_SHORT).show();
        }
    }
}
