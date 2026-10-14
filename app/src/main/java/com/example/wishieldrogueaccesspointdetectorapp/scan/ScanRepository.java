package com.example.wishieldrogueaccesspointdetectorapp.scan;

import com.example.wishieldrogueaccesspointdetectorapp.model.AccessPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the most recent scan so MainActivity can hand results to
 * DashboardActivity without serialising them through an Intent.
 *
 * This lives in memory only. If Android kills the process in the background,
 * it comes back empty — DashboardActivity handles that by simply rescanning,
 * which is why nothing here is persisted.
 */
public class ScanRepository {

    private static ScanRepository instance;

    private List<AccessPoint> results = new ArrayList<>();

    private ScanRepository() {
    }

    public static synchronized ScanRepository getInstance() {
        if (instance == null) {
            instance = new ScanRepository();
        }
        return instance;
    }

    public synchronized void setResults(List<AccessPoint> results) {
        this.results = results != null ? new ArrayList<>(results) : new ArrayList<>();
    }

    public synchronized List<AccessPoint> getResults() {
        return new ArrayList<>(results);
    }

    public synchronized boolean isEmpty() {
        return results.isEmpty();
    }
}
