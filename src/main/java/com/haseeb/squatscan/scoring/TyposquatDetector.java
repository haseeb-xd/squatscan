package com.haseeb.squatscan.scoring;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TyposquatDetector {

    private static final String RESOURCE_PATH = "top-npm-packages.txt";

    private final Set<String> topPackages;

    public TyposquatDetector() {
        this(loadDefaultTopPackages());
    }

    TyposquatDetector(Set<String> topPackages) {
        this.topPackages = topPackages;
    }

    public Optional<NearMiss> findNearMiss(String packageName) {
        if (topPackages.contains(packageName)) {
            return Optional.empty();
        }

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : topPackages) {
            int distance = levenshteinDistance(packageName, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = candidate;
            }
        }

        if (bestMatch != null && bestDistance <= 2 && bestDistance > 0) {
            return Optional.of(new NearMiss(bestMatch, bestDistance));
        }
        return Optional.empty();
    }

    public boolean isKnownPopularPackage(String packageName) {
        return topPackages.contains(packageName);
    }

    static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        return dp[a.length()][b.length()];
    }

    private static Set<String> loadDefaultTopPackages() {
        try (InputStream is = TyposquatDetector.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Missing resource: " + RESOURCE_PATH);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toUnmodifiableSet());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE_PATH, e);
        }
    }

    public record NearMiss(String matchedPackage, int editDistance) {
    }
}
