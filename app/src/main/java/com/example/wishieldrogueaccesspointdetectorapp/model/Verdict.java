package com.example.wishieldrogueaccesspointdetectorapp.model;

/**
 * Severity assigned to an access point by {@code RogueDetector}.
 *
 * Declared in increasing order of severity — the detector uses
 * {@link #ordinal()} to keep the worst finding when several checks fire on the
 * same access point, so do not reorder these constants.
 */
public enum Verdict {

    /** Nothing suspicious found. Modern encryption, no name collisions. */
    SAFE,

    /** Worth a look — but has legitimate explanations (e.g. mesh Wi-Fi). */
    SUSPICIOUS,

    /** Matches a known rogue / evil-twin pattern. Shown as REVOKE ACCESS. */
    ROGUE
}
