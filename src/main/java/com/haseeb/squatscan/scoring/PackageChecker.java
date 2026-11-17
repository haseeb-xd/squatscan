package com.haseeb.squatscan.scoring;

import com.haseeb.squatscan.cache.RegistryCache;
import com.haseeb.squatscan.model.PackageReport;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Orchestrates cache + scorer for a single package or batch, and parses install
 * commands into package names. This is the shared core used by both the CLI and
 * the MCP tool layer.
 */
@Service
public class PackageChecker {

    private static final Pattern FLAG_PATTERN = Pattern.compile("^--?[A-Za-z].*");
    private static final List<String> INSTALL_VERBS = List.of("install", "i", "add");

    private final RegistryCache registryCache;
    private final RiskScorer riskScorer;

    public PackageChecker(RegistryCache registryCache, RiskScorer riskScorer) {
        this.registryCache = registryCache;
        this.riskScorer = riskScorer;
    }

    public PackageReport check(String rawName) {
        String name = stripVersionSpecifier(rawName);
        var data = registryCache.get(name);
        return riskScorer.score(name, data);
    }

    public List<PackageReport> checkAll(List<String> names) {
        return names.stream().map(this::check).collect(Collectors.toList());
    }

    public List<PackageReport> checkInstallCommand(String command) {
        return checkAll(extractPackageNames(command));
    }

    List<String> extractPackageNames(String command) {
        String[] tokens = command.trim().split("\\s+");

        int startIndex = 0;
        for (int i = 0; i < tokens.length; i++) {
            if (INSTALL_VERBS.contains(tokens[i])) {
                startIndex = i + 1;
                break;
            }
        }

        List<String> names = new ArrayList<>();
        for (int i = startIndex; i < tokens.length; i++) {
            String token = tokens[i];
            boolean isPackageManagerName = token.equals("npm") || token.equals("npx")
                    || token.equals("yarn") || token.equals("pnpm");
            if (token.isBlank() || isPackageManagerName || FLAG_PATTERN.matcher(token).matches()) {
                continue;
            }
            names.add(stripVersionSpecifier(token));
        }
        return names;
    }

    private String stripVersionSpecifier(String token) {
        // Scoped packages: @scope/name@version — only strip the version after the name.
        if (token.startsWith("@")) {
            int secondAt = token.indexOf('@', 1);
            return secondAt > 0 ? token.substring(0, secondAt) : token;
        }
        int at = token.indexOf('@');
        return at > 0 ? token.substring(0, at) : token;
    }
}
