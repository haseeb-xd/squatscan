package com.haseeb.squatscan.registry;

import java.time.Instant;

public record NpmPackageData(
        boolean exists,
        Instant firstPublished,
        int versionCount,
        boolean hasRepo,
        int maintainerCount,
        long lastMonthDownloads
) {
    public static NpmPackageData notFound() {
        return new NpmPackageData(false, null, 0, false, 0, 0);
    }
}
