package com.haseeb.squatscan.cli;

import com.haseeb.squatscan.model.PackageReport;
import com.haseeb.squatscan.model.Verdict;
import com.haseeb.squatscan.scoring.PackageChecker;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Claude Code PreToolUse hook for the Bash tool. Reads the hook payload from stdin,
 * finds every package an install command in it would fetch, and:
 * <ul>
 *   <li>RED anywhere  → denies the command, with the reasons shown to Claude</li>
 *   <li>YELLOW        → asks the user to confirm</li>
 *   <li>otherwise     → exits silently, letting the command run</li>
 * </ul>
 * Fails open: any error (bad payload, registry unreachable) lets the command through,
 * so an outage never blocks all installs.
 */
final class HookRunner {

    private static final Pattern SEGMENT_SPLIT = Pattern.compile("\\s*(?:&&|\\|\\||[;|&\\n])\\s*");
    private static final Pattern NPM_NAME =
            Pattern.compile("^(@[a-z0-9~][a-z0-9._~-]*/)?[a-z0-9~][a-z0-9._~-]*$");
    private static final Set<String> MANAGERS = Set.of("npm", "pnpm", "yarn", "bun");
    private static final Set<String> RUNNERS = Set.of("npx", "pnpx", "bunx");
    private static final Set<String> INSTALL_VERBS = Set.of("install", "i", "in", "add");
    private static final Set<String> EXEC_VERBS = Set.of("exec", "dlx", "x");

    private HookRunner() {
    }

    static int run(PackageChecker checker) {
        PrintStream realOut = System.out;
        // Anything a library prints must not corrupt the JSON we return on stdout.
        System.setOut(System.err);
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
            String command = payload.path("tool_input").path("command").asString("");

            List<String> packages = extractPackages(command);
            if (packages.isEmpty()) {
                return 0;
            }

            List<PackageReport> reports = checker.checkAll(packages);
            List<PackageReport> red = filter(reports, Verdict.RED);
            List<PackageReport> yellow = filter(reports, Verdict.YELLOW);
            if (red.isEmpty() && yellow.isEmpty()) {
                return 0;
            }

            String decision = red.isEmpty() ? "ask" : "deny";
            StringBuilder reason = new StringBuilder("SquatScan flagged packages in this install command:\n");
            for (PackageReport r : reports) {
                if (r.verdict() == Verdict.GREEN) {
                    continue;
                }
                reason.append("- ").append(r.name()).append(": ").append(r.verdict())
                        .append(" (score ").append(r.score()).append(") - ")
                        .append(String.join(" ", r.reasons())).append('\n');
            }
            reason.append(red.isEmpty()
                    ? "Verify these names are what you intended before installing."
                    : "Do not install RED packages. Check for a typo and use the intended package instead.");

            ObjectNode out = mapper.createObjectNode();
            ObjectNode specific = out.putObject("hookSpecificOutput");
            specific.put("hookEventName", "PreToolUse");
            specific.put("permissionDecision", decision);
            specific.put("permissionDecisionReason", reason.toString());
            realOut.println(mapper.writeValueAsString(out));
            realOut.flush();
            return 0;
        } catch (Exception e) {
            System.err.println("squatscan hook: skipped check (" + e.getMessage() + ")");
            return 0;
        } finally {
            System.setOut(realOut);
        }
    }

    /** Package names an install/exec command would fetch from the registry. */
    static List<String> extractPackages(String command) {
        Set<String> found = new LinkedHashSet<>();
        for (String segment : SEGMENT_SPLIT.split(command)) {
            List<String> tokens = tokenize(segment);
            if (tokens.isEmpty()) {
                continue;
            }
            String tool = tokens.get(0);
            List<String> rest = tokens.subList(1, tokens.size());

            if (RUNNERS.contains(tool)) {
                firstNonFlag(rest).ifPresent(found::add);
            } else if (MANAGERS.contains(tool)) {
                int verbIdx = indexOfFirstNonFlag(rest);
                if (verbIdx < 0) {
                    continue;
                }
                String verb = rest.get(verbIdx);
                List<String> args = rest.subList(verbIdx + 1, rest.size());
                if (INSTALL_VERBS.contains(verb)) {
                    args.stream().filter(t -> !t.startsWith("-")).forEach(found::add);
                } else if (EXEC_VERBS.contains(verb)) {
                    firstNonFlag(args).ifPresent(found::add);
                }
            }
        }
        return found.stream()
                .map(HookRunner::stripVersion)
                .filter(n -> NPM_NAME.matcher(n).matches())
                .distinct()
                .collect(Collectors.toList());
    }

    private static List<String> tokenize(String segment) {
        List<String> tokens = new ArrayList<>();
        for (String raw : segment.trim().split("\\s+")) {
            String t = raw.replaceAll("^[\"']|[\"']$", "");
            if (t.isEmpty()) {
                continue;
            }
            // Skip leading env assignments (FOO=bar npm i x) and sudo.
            if (tokens.isEmpty() && (t.matches("^[A-Za-z_][A-Za-z0-9_]*=.*") || t.equals("sudo"))) {
                continue;
            }
            if (tokens.isEmpty()) {
                t = t.replaceAll("(?i)\\.(cmd|exe|ps1)$", "");
            }
            tokens.add(t);
        }
        return tokens;
    }

    private static int indexOfFirstNonFlag(List<String> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).startsWith("-")) {
                return i;
            }
        }
        return -1;
    }

    private static java.util.Optional<String> firstNonFlag(List<String> tokens) {
        int i = indexOfFirstNonFlag(tokens);
        return i < 0 ? java.util.Optional.empty() : java.util.Optional.of(tokens.get(i));
    }

    private static String stripVersion(String token) {
        if (token.startsWith("@")) {
            int at = token.indexOf('@', 1);
            return at > 0 ? token.substring(0, at) : token;
        }
        int at = token.indexOf('@');
        return at > 0 ? token.substring(0, at) : token;
    }

    private static List<PackageReport> filter(List<PackageReport> reports, Verdict verdict) {
        return reports.stream().filter(r -> r.verdict() == verdict).collect(Collectors.toList());
    }
}
