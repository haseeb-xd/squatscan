package com.haseeb.squatscan.scoring;

/**
 * All scoring weights and thresholds live here so tuning never requires touching
 * {@link RiskScorer} logic. See squatscan-spec.md section 5 for rationale.
 */
public final class ScoringConfig {

    private ScoringConfig() {
    }

    // --- Verdict thresholds (0-100 score) ---
    public static final int RED_THRESHOLD = 70;
    public static final int YELLOW_THRESHOLD = 40;

    // --- Signal weights ---
    public static final int WEIGHT_DOES_NOT_EXIST = 45;

    public static final int WEIGHT_NEAR_MISS_DISTANCE_1 = 55;
    public static final int WEIGHT_NEAR_MISS_DISTANCE_2 = 35;

    public static final int AGE_VERY_RECENT_DAYS = 30;
    public static final int WEIGHT_VERY_RECENT = 30;
    public static final int AGE_RECENT_DAYS = 90;
    public static final int WEIGHT_RECENT = 15;

    public static final long DOWNLOADS_VERY_LOW_THRESHOLD = 50;
    public static final int WEIGHT_VERY_LOW_DOWNLOADS = 25;
    public static final long DOWNLOADS_LOW_THRESHOLD = 500;
    public static final int WEIGHT_LOW_DOWNLOADS = 10;

    public static final int WEIGHT_NO_REPOSITORY = 15;

    public static final int WEIGHT_SINGLE_VERSION = 10;

    public static final int MAINTAINER_THIN_COUNT = 1;
    public static final int WEIGHT_THIN_MAINTAINER_HISTORY = 10;

    public static final int MAX_SCORE = 100;
}
