package com.haseeb.squatscan.scoring;

import com.haseeb.squatscan.model.PackageMetadata;
import com.haseeb.squatscan.model.PackageReport;
import com.haseeb.squatscan.model.Verdict;
import com.haseeb.squatscan.registry.NpmPackageData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class RiskScorer {

    private final TyposquatDetector typosquatDetector;

    public RiskScorer(TyposquatDetector typosquatDetector) {
        this.typosquatDetector = typosquatDetector;
    }

    public PackageReport score(String packageName, NpmPackageData data) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        if (!data.exists()) {
            score += ScoringConfig.WEIGHT_DOES_NOT_EXIST;
            reasons.add("Package does not exist on npm - possibly a hallucinated name not yet squatted.");
        }

        var nearMiss = typosquatDetector.findNearMiss(packageName);
        if (nearMiss.isPresent()) {
            var match = nearMiss.get();
            int weight = match.editDistance() == 1
                    ? ScoringConfig.WEIGHT_NEAR_MISS_DISTANCE_1
                    : ScoringConfig.WEIGHT_NEAR_MISS_DISTANCE_2;
            score += weight;
            reasons.add("Name is a near-miss (edit distance " + match.editDistance() + ") of popular package '"
                    + match.matchedPackage() + "' - classic typosquat pattern.");
        }

        if (data.exists()) {
            score += ageContribution(data, reasons);
            score += downloadsContribution(data, reasons);
            score += repositoryContribution(data, reasons);
            score += versionCountContribution(data, reasons);
            score += maintainerContribution(data, reasons);
        }

        int finalScore = Math.min(score, ScoringConfig.MAX_SCORE);
        Verdict verdict = toVerdict(finalScore);

        if (reasons.isEmpty()) {
            reasons.add("No strong risk signals detected.");
        }

        PackageMetadata metadata = new PackageMetadata(
                data.exists(),
                data.firstPublished() != null ? data.firstPublished().toString() : null,
                data.lastMonthDownloads(),
                data.versionCount(),
                data.hasRepo(),
                data.maintainerCount()
        );

        return new PackageReport(packageName, verdict, finalScore, reasons, metadata);
    }

    private int ageContribution(NpmPackageData data, List<String> reasons) {
        if (data.firstPublished() == null) {
            return 0;
        }
        long ageDays = ChronoUnit.DAYS.between(data.firstPublished(), Instant.now());
        if (ageDays < ScoringConfig.AGE_VERY_RECENT_DAYS) {
            reasons.add("Published " + ageDays + " days ago - very recently created, a common squatter pattern.");
            return ScoringConfig.WEIGHT_VERY_RECENT;
        }
        if (ageDays < ScoringConfig.AGE_RECENT_DAYS) {
            reasons.add("Published " + ageDays + " days ago - relatively new.");
            return ScoringConfig.WEIGHT_RECENT;
        }
        return 0;
    }

    private int downloadsContribution(NpmPackageData data, List<String> reasons) {
        long downloads = data.lastMonthDownloads();
        if (downloads < ScoringConfig.DOWNLOADS_VERY_LOW_THRESHOLD) {
            reasons.add("Only " + downloads + " downloads last month - essentially unused despite a plausible name.");
            return ScoringConfig.WEIGHT_VERY_LOW_DOWNLOADS;
        }
        if (downloads < ScoringConfig.DOWNLOADS_LOW_THRESHOLD) {
            reasons.add("Low download count last month (" + downloads + ").");
            return ScoringConfig.WEIGHT_LOW_DOWNLOADS;
        }
        return 0;
    }

    private int repositoryContribution(NpmPackageData data, List<String> reasons) {
        if (!data.hasRepo()) {
            reasons.add("No linked repository - legitimate packages usually link source control.");
            return ScoringConfig.WEIGHT_NO_REPOSITORY;
        }
        return 0;
    }

    private int versionCountContribution(NpmPackageData data, List<String> reasons) {
        if (data.versionCount() <= 1) {
            reasons.add("Only a single published version - one-shot publishes are common for squats.");
            return ScoringConfig.WEIGHT_SINGLE_VERSION;
        }
        return 0;
    }

    private int maintainerContribution(NpmPackageData data, List<String> reasons) {
        if (data.maintainerCount() <= ScoringConfig.MAINTAINER_THIN_COUNT) {
            reasons.add("Thin maintainer history (only " + data.maintainerCount() + " listed maintainer(s)).");
            return ScoringConfig.WEIGHT_THIN_MAINTAINER_HISTORY;
        }
        return 0;
    }

    private Verdict toVerdict(int score) {
        if (score >= ScoringConfig.RED_THRESHOLD) {
            return Verdict.RED;
        }
        if (score >= ScoringConfig.YELLOW_THRESHOLD) {
            return Verdict.YELLOW;
        }
        return Verdict.GREEN;
    }
}
