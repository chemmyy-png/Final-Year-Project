package com.example.wishieldrogueaccesspointdetectorapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.wishieldrogueaccesspointdetectorapp.model.AccessPoint;
import com.example.wishieldrogueaccesspointdetectorapp.scan.WifiScanner;

import java.util.List;

public class MainActivity extends AppCompatActivity implements WifiScanner.Listener {

    private static final int PERMISSION_REQUEST = 1001;

    private WifiScanner scanner;
    private Button scanButton;
    private LinearLayout scanningLayout;
    private boolean navigating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanButton = findViewById(R.id.scanButton);
        scanningLayout = findViewById(R.id.scanningLayout);

        scanner = new WifiScanner(this, this);

        scanButton.setOnClickListener(v -> beginScan());

        startIconAnimation();
    }

    @Override
    protected void onStart() {
        super.onStart();
        scanner.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        scanner.stop();
    }

    // ------------------------------------------------------------------
    // Scanning
    // ------------------------------------------------------------------

    private void beginScan() {
        // 1. Check & Request App Permissions
        if (!WifiScanner.hasPermissions(this)) {
            ActivityCompat.requestPermissions(this, WifiScanner.requiredPermissions(),
                    PERMISSION_REQUEST);
            return;
        }

        // 2. Check if System Location (GPS) is turned ON
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean isGpsEnabled = false;
        if (locationManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                isGpsEnabled = locationManager.isLocationEnabled();
            } else {
                isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            }
        }

        if (!isGpsEnabled) {
            Toast.makeText(this, "Please enable Location/GPS to scan Wi-Fi networks.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }

        // 3. Permissions & System Location ready -> Start Scan
        showScanningState(true);
        scanner.requestScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (WifiScanner.hasPermissions(this)) {
                beginScan();
            } else {
                Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onScanComplete(List<AccessPoint> accessPoints) {
        runOnUiThread(this::goToDashboard);
    }

    @Override
    public void onScanFailed(String message) {
        runOnUiThread(() -> {
            showScanningState(false);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private void showScanningState(boolean scanning) {
        scanButton.setVisibility(scanning ? View.GONE : View.VISIBLE);
        scanningLayout.setVisibility(scanning ? View.VISIBLE : View.GONE);
    }

    private void goToDashboard() {
        // A scan can report results more than once; only navigate the first time.
        if (navigating) {
            return;
        }
        navigating = true;
        startActivity(new Intent(MainActivity.this, DashboardActivity.class));
        finish();
    }

    // ------------------------------------------------------------------
    // Splash icon animation (unchanged from the original)
    // ------------------------------------------------------------------

    private void startIconAnimation() {
        ImageView appIcon = findViewById(R.id.appIcon);
        Runnable floating = new Runnable() {
            boolean up = true;

            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                appIcon.animate()
                        .translationY(up ? -14f : 0f)
                        .setDuration(1800)
                        .withEndAction(this)
                        .start();
                up = !up;
            }
        };
        floating.run();
    }
}