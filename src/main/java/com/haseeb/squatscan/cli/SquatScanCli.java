package com.haseeb.squatscan.cli;

import com.haseeb.squatscan.cache.RegistryCache;
import com.haseeb.squatscan.model.PackageReport;
import com.haseeb.squatscan.registry.NpmRegistryClient;
import com.haseeb.squatscan.scoring.PackageChecker;
import com.haseeb.squatscan.scoring.RiskScorer;
import com.haseeb.squatscan.scoring.TyposquatDetector;

import java.util.List;

/**
 * Thin terminal entry point for eyeballing scoring results before the MCP layer
 * is wired up. Not a Spring Boot application — wires the plain POJOs directly so
 * it starts instantly.
 *
 * Usage:
 *   check <package> [<package> ...]
 *   install "npm install express lodahs some-ai-suggested-pkg"
 */
public final class SquatScanCli {

    private SquatScanCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        TyposquatDetector detector = new TyposquatDetector();
        NpmRegistryClient registryClient = new NpmRegistryClient();
        RegistryCache cache = new RegistryCache(registryClient);
        RiskScorer scorer = new RiskScorer(detector);
        PackageChecker checker = new PackageChecker(cache, scorer);

        String subcommand = args[0];
        List<PackageReport> reports;

        switch (subcommand) {
            case "hook" -> {
                System.exit(HookRunner.run(checker));
                return;
            }
            case "check" -> {
                if (args.length < 2) {
                    printUsage();
                    System.exit(1);
                    return;
                }
                reports = checker.checkAll(List.of(args).subList(1, args.length));
            }
            case "install" -> {
                if (args.length < 2) {
                    printUsage();
                    System.exit(1);
                    return;
                }
                String command = String.join(" ", List.of(args).subList(1, args.length));
                reports = checker.checkInstallCommand(command);
            }
            default -> {
                printUsage();
                System.exit(1);
                return;
            }
        }

        reports.forEach(SquatScanCli::printReport);
    }

    private static void printReport(PackageReport report) {
        System.out.println("=".repeat(60));
        System.out.printf("%-30s %-8s score=%d%n", report.name(), report.verdict(), report.score());
        System.out.println("-".repeat(60));
        report.reasons().forEach(reason -> System.out.println("  - " + reason));
        var meta = report.metadata();
        System.out.printf(
                "  metadata: exists=%s firstPublished=%s downloads=%d versions=%d hasRepo=%s maintainers=%d%n",
                meta.exists(), meta.firstPublished(), meta.totalDownloads(), meta.versionCount(),
                meta.hasRepo(), meta.maintainerCount());
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  check <package> [<package> ...]");
        System.out.println("  install \"npm install <pkg1> <pkg2> ...\"");
    }
}
