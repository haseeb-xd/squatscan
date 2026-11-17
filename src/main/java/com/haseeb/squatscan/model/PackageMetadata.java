package com.haseeb.squatscan.model;

public record PackageMetadata(
        boolean exists,
        String firstPublished,
        long totalDownloads,
        int versionCount,
        boolean hasRepo,
        int maintainerCount
) {
    public static PackageMetadata notFound() {
        return new PackageMetadata(false, null, 0, 0, false, 0);
    }
}
