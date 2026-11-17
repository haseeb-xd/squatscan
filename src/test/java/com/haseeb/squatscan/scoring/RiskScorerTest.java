package com.haseeb.squatscan.scoring;

import com.haseeb.squatscan.model.PackageReport;
import com.haseeb.squatscan.model.Verdict;
import com.haseeb.squatscan.registry.NpmPackageData;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RiskScorerTest {

    private final TyposquatDetector detector = new TyposquatDetector(Set.of("lodash", "express", "react", "axios"));
    private final RiskScorer scorer = new RiskScorer(detector);

    @Test
    void knownGoodPopularPackageIsGreen() {
        NpmPackageData data = new NpmPackageData(
                true,
                Instant.now().minus(5000, ChronoUnit.DAYS),
                200,
                true,
                10,
                50_000_000
        );

        PackageReport report = scorer.score("express", data);

        assertThat(report.verdict()).isEqualTo(Verdict.GREEN);
        assertThat(report.score()).isLessThan(ScoringConfig.YELLOW_THRESHOLD);
    }

    @Test
    void obviousTyposquatIsRedOrYellowWithNearMissReason() {
        NpmPackageData data = new NpmPackageData(
                true,
                Instant.now().minus(10, ChronoUnit.DAYS),
                1,
                false,
                1,
                5
        );

        PackageReport report = scorer.score("lodahs", data);

        assertThat(report.verdict()).isIn(Verdict.RED, Verdict.YELLOW);
        assertThat(report.reasons()).anyMatch(r -> r.contains("near-miss") && r.contains("lodash"));
    }

    @Test
    void nonExistentPackageIsFlaggedYellow() {
        NpmPackageData data = NpmPackageData.notFound();

        PackageReport report = scorer.score("some-ai-hallucinated-pkg-that-does-not-exist", data);

        assertThat(report.verdict()).isEqualTo(Verdict.YELLOW);
        assertThat(report.reasons()).anyMatch(r -> r.contains("does not exist"));
        assertThat(report.metadata().exists()).isFalse();
    }

    @Test
    void nonExistentNearMissIsRed() {
        NpmPackageData data = NpmPackageData.notFound();

        PackageReport report = scorer.score("lodahs", data);

        assertThat(report.verdict()).isEqualTo(Verdict.RED);
    }

    @Test
    void reasonsAreNeverEmpty() {
        NpmPackageData data = new NpmPackageData(true, Instant.now().minus(3000, ChronoUnit.DAYS), 50, true, 5, 1_000_000);

        PackageReport report = scorer.score("react", data);

        assertThat(report.reasons()).isNotEmpty();
    }
}
