package com.example.wishieldrogueaccesspointdetectorapp.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wishieldrogueaccesspointdetectorapp.R;
import com.example.wishieldrogueaccesspointdetectorapp.model.AccessPoint;
import com.example.wishieldrogueaccesspointdetectorapp.model.Verdict;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders each analysed access point into the card design already used in
 * activity_dashboard.xml, colour-coded by verdict.
 */
public class NetworkAdapter extends RecyclerView.Adapter<NetworkAdapter.ViewHolder> {

    public interface OnCardActionListener {
        /** "Why flagged?" tapped on a SUSPICIOUS or ROGUE network. */
        void onShowReasons(AccessPoint accessPoint);

        /** "Connect" tapped on a SAFE network. */
        void onConnect(AccessPoint accessPoint);
    }

    private List<AccessPoint> items = new ArrayList<>();
    private final OnCardActionListener listener;

    public NetworkAdapter(OnCardActionListener listener) {
        this.listener = listener;
    }

    public void submit(List<AccessPoint> newItems) {
        items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_network_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AccessPoint ap = items.get(position);
        Context context = holder.itemView.getContext();

        holder.ssid.setText(ap.getDisplaySsid());
        holder.bssid.setText(ap.bssid);
        holder.security.setText(ap.getSecurityLabel());
        holder.vendor.setText(ap.vendor);
        holder.signal.setText(context.getString(R.string.signal_dbm, ap.level));

        int accent;
        int badgeBackground;
        String badgeText;

        switch (ap.verdict) {
            case ROGUE:
                accent = ContextCompat.getColor(context, R.color.status_red);
                badgeBackground = R.drawable.badge_revoked_background;
                badgeText = context.getString(R.string.revoke_access);
                break;
            case SUSPICIOUS:
                accent = ContextCompat.getColor(context, R.color.status_yellow);
                badgeBackground = R.drawable.badge_warning_background;
                badgeText = context.getString(R.string.badge_suspicious);
                break;
            default:
                accent = ContextCompat.getColor(context, R.color.status_green);
                badgeBackground = R.drawable.badge_safe_background;
                badgeText = context.getString(R.string.badge_safe);
        }

        holder.badge.setText(badgeText);
        holder.badge.setTextColor(accent);
        holder.badge.setBackgroundResource(badgeBackground);
        holder.icon.setImageTintList(android.content.res.ColorStateList.valueOf(accent));

        boolean flagged = ap.verdict != Verdict.SAFE;
        holder.action.setText(flagged
                ? context.getString(R.string.why_flagged)
                : context.getString(R.string.connect));
        holder.action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                flagged ? accent : ContextCompat.getColor(context, R.color.primary_blue)));

        holder.action.setOnClickListener(v -> {
            if (listener == null) {
                return;
            }
            if (flagged) {
                listener.onShowReasons(ap);
            } else {
                listener.onConnect(ap);
            }
        });

        // Tapping anywhere on the card always explains the verdict.
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShowReasons(ap);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView ssid;
        final TextView bssid;
        final TextView badge;
        final TextView security;
        final TextView vendor;
        final TextView signal;
        final ImageView icon;
        final MaterialButton action;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ssid = itemView.findViewById(R.id.textSsid);
            bssid = itemView.findViewById(R.id.textBssid);
            badge = itemView.findViewById(R.id.textBadge);
            security = itemView.findViewById(R.id.textSecurity);
            vendor = itemView.findViewById(R.id.textVendor);
            signal = itemView.findViewById(R.id.textSignal);
            icon = itemView.findViewById(R.id.networkIcon);
            action = itemView.findViewById(R.id.btnCardAction);
        }
    }
}
