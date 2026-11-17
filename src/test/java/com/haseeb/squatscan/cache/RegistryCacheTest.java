package com.haseeb.squatscan.cache;

import com.haseeb.squatscan.registry.NpmPackageData;
import com.haseeb.squatscan.registry.NpmRegistryClient;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistryCacheTest {

    @Test
    void secondLookupWithinTtlDoesNotHitClientAgain() {
        NpmRegistryClient client = mock(NpmRegistryClient.class);
        NpmPackageData data = new NpmPackageData(true, null, 1, true, 1, 100);
        when(client.fetch("express")).thenReturn(data);

        RegistryCache cache = new RegistryCache(client, Duration.ofHours(1));

        NpmPackageData first = cache.get("express");
        NpmPackageData second = cache.get("express");

        assertThat(first).isEqualTo(data);
        assertThat(second).isEqualTo(data);
        verify(client, times(1)).fetch("express");
    }

    @Test
    void lookupAfterTtlExpiryHitsClientAgain() throws InterruptedException {
        NpmRegistryClient client = mock(NpmRegistryClient.class);
        NpmPackageData data = new NpmPackageData(true, null, 1, true, 1, 100);
        when(client.fetch("express")).thenReturn(data);

        RegistryCache cache = new RegistryCache(client, Duration.ofMillis(10));

        cache.get("express");
        Thread.sleep(50);
        cache.get("express");

        verify(client, times(2)).fetch("express");
    }

    @Test
    void invalidateForcesRefetch() {
        NpmRegistryClient client = mock(NpmRegistryClient.class);
        NpmPackageData data = new NpmPackageData(true, null, 1, true, 1, 100);
        when(client.fetch("express")).thenReturn(data);

        RegistryCache cache = new RegistryCache(client, Duration.ofHours(1));
        cache.get("express");
        cache.invalidate("express");
        cache.get("express");

        verify(client, times(2)).fetch("express");
    }
}
