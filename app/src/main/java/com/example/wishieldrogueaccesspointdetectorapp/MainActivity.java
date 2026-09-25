package com.example.wishieldrogueaccesspointdetectorapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // scanning button
        Button scanButton = findViewById(R.id.scanButton);
        LinearLayout scanningLayout = findViewById(R.id.scanningLayout);

        // change ui
        scanButton.setOnClickListener(v -> {
            scanButton.setVisibility(View.GONE);
            scanningLayout.setVisibility(View.VISIBLE);

            // Simulate scanning for 3 seconds, then navigate to Dashboard
            new Handler().postDelayed(() -> {
                Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                startActivity(intent);
                finish(); // Optional: Close MainActivity so user can't go back to splash
            }, 3000);
        });

        //icon animation

        ImageView appIcon = findViewById(R.id.appIcon);
        Runnable floating = new Runnable() {
            boolean up = true;

            @Override
            public void run() {
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