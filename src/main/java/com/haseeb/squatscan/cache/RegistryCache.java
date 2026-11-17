package com.haseeb.squatscan.cache;

import com.haseeb.squatscan.registry.NpmPackageData;
import com.haseeb.squatscan.registry.NpmRegistryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wraps {@link NpmRegistryClient} with a simple in-memory TTL cache so repeated
 * lookups of the same package within a session don't hammer the npm registry.
 */
@Component
public class RegistryCache {

    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final NpmRegistryClient registryClient;
    private final Duration ttl;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Autowired
    public RegistryCache(NpmRegistryClient registryClient) {
        this(registryClient, DEFAULT_TTL);
    }

    RegistryCache(NpmRegistryClient registryClient, Duration ttl) {
        this.registryClient = registryClient;
        this.ttl = ttl;
    }

    public NpmPackageData get(String packageName) {
        CacheEntry entry = cache.get(packageName);
        Instant now = Instant.now();
        if (entry != null && entry.expiresAt.isAfter(now)) {
            return entry.data;
        }

        NpmPackageData fresh = registryClient.fetch(packageName);
        cache.put(packageName, new CacheEntry(fresh, now.plus(ttl)));
        return fresh;
    }

    public void invalidate(String packageName) {
        cache.remove(packageName);
    }

    public void clear() {
        cache.clear();
    }

    int size() {
        return cache.size();
    }

    private record CacheEntry(NpmPackageData data, Instant expiresAt) {
    }
}
