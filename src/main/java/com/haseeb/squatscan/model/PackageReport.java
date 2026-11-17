package com.haseeb.squatscan.model;

import java.util.List;

public record PackageReport(
        String name,
        Verdict verdict,
        int score,
        List<String> reasons,
        PackageMetadata metadata
) {
}
