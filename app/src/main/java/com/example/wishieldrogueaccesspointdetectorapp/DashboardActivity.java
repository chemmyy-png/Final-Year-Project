package com.example.wishieldrogueaccesspointdetectorapp;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.example.wishieldrogueaccesspointdetectorapp.R;


import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class DashboardActivity extends AppCompatActivity {

    private boolean overridePermitted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MaterialButton btnConnectSafe = findViewById(R.id.btnConnectSafe);
        MaterialButton btnBlockedRogueAction = findViewById(R.id.btnBlockedRogueAction);

        // Safe Network: Direct connection
        if (btnConnectSafe != null) {
            btnConnectSafe.setOnClickListener(v -> {
                Toast.makeText(this, "Connecting securely to gmi (TP-Link)...", Toast.LENGTH_SHORT).show();
            });
        }

        // Rogue Network: Intercepted and blocked
        if (btnBlockedRogueAction != null) {
            btnBlockedRogueAction.setOnClickListener(v -> {
                if (!overridePermitted) {
                    showBlockedDialog(
                            "[rogue ap]",
                            "A4:5E:60:C2:14:78",
                            "Open (Unencrypted)",
                            "Unknown / Spoofed",
                            "88/100 (Critical)"
                    );
                } else {
                    Toast.makeText(this, "Override active: Connecting to [rogue ap] at your own risk...", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void showBlockedDialog(String ssid, String bssid, String security, String vendor, String threatScore) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_security_block);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        TextView tvThreatDetails = dialog.findViewById(R.id.tvThreatDetails);
        Button btnKeepBlocked = dialog.findViewById(R.id.btnKeepBlocked);
        TextView tvOverrideConnect = dialog.findViewById(R.id.tvOverrideConnect);

        tvThreatDetails.setText(
                "WiShield flagged this access point as an Evil Twin:\n\n" +
                        "• SSID: " + ssid + "\n" +
                        "• BSSID: " + bssid + "\n" +
                        "• Security: " + security + "\n" +
                        "• Vendor: " + vendor + "\n" +
                        "• Threat Score: " + threatScore + "\n\n" +
                        "Connecting will expose personal data, unencrypted traffic, and authentication credentials to eavesdropping."
        );

        // Default Choice: Dismiss dialog and maintain block
        btnKeepBlocked.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Connection safely prevented.", Toast.LENGTH_SHORT).show();
        });

        // Override Choice: User deliberately proceeds past the warning
        tvOverrideConnect.setOnClickListener(v -> {
            overridePermitted = true;
            dialog.dismiss();

            // Update button label to indicate override is active
            MaterialButton btnBlockedRogueAction = findViewById(R.id.btnBlockedRogueAction);
            if (btnBlockedRogueAction != null) {
                btnBlockedRogueAction.setText("Override Allowed (Connect)");
                btnBlockedRogueAction.setTextColor(Color.parseColor("#F59E0B")); // Amber warning color
                btnBlockedRogueAction.setStrokeColorResource(android.R.color.holo_orange_dark);
            }

            Toast.makeText(this, "⚠️ Warning: Override acknowledged. Connection permitted.", Toast.LENGTH_LONG).show();
        });

        dialog.show();
    }
}