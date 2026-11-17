package com.haseeb.squatscan.scoring;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TyposquatDetectorTest {

    private final TyposquatDetector detector = new TyposquatDetector(Set.of("lodash", "express", "react", "axios"));

    @Test
    void flagsClassicTyposquat() {
        var result = detector.findNearMiss("lodahs");

        assertThat(result).isPresent();
        assertThat(result.get().matchedPackage()).isEqualTo("lodash");
        assertThat(result.get().editDistance()).isLessThanOrEqualTo(2);
    }

    @Test
    void exactMatchIsNotFlaggedAsASquat() {
        var result = detector.findNearMiss("express");

        assertThat(result).isEmpty();
        assertThat(detector.isKnownPopularPackage("express")).isTrue();
    }

    @Test
    void distantNameIsNotFlagged() {
        var result = detector.findNearMiss("some-totally-unrelated-thing");

        assertThat(result).isEmpty();
    }

    @Test
    void levenshteinDistanceIsSymmetricAndCorrect() {
        assertThat(TyposquatDetector.levenshteinDistance("lodash", "lodahs")).isEqualTo(2);
        assertThat(TyposquatDetector.levenshteinDistance("express", "express")).isEqualTo(0);
        assertThat(TyposquatDetector.levenshteinDistance("axios", "axois")).isEqualTo(2);
    }
}
