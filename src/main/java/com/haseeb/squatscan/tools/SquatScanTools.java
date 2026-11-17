package com.haseeb.squatscan.tools;

import com.haseeb.squatscan.model.PackageReport;
import com.haseeb.squatscan.scoring.PackageChecker;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tool surface for SquatScan. Thin wrapper over {@link PackageChecker} — all
 * scoring logic lives in the scoring package so it stays independently testable.
 */
@Service
public class SquatScanTools {

    private final PackageChecker packageChecker;

    public SquatScanTools(PackageChecker packageChecker) {
        this.packageChecker = packageChecker;
    }

    @McpTool(
            name = "check_package",
            description = "Check a single npm package for signs of slopsquatting — a hallucinated or "
                    + "typosquatted package name that was pre-registered by an attacker. This does NOT just "
                    + "check whether the package exists; a package that installs fine can still be "
                    + "RED/suspicious. Call this before suggesting or installing any npm package, especially "
                    + "one you are not highly confident is a real, well-known package.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true
            )
    )
    public PackageReport checkPackage(
            @McpToolParam(required = true, description = "The npm package name to check, e.g. 'lodash'.")
            String name,
            @McpToolParam(required = false, description = "Specific version to evaluate (optional). "
                    + "If omitted, the package is evaluated generally.")
            String version
    ) {
        return packageChecker.check(name);
    }

    @McpTool(
            name = "check_packages",
            description = "Batch-check multiple npm package names for slopsquatting risk in one call. "
                    + "Use this when evaluating several packages at once, e.g. all dependencies suggested "
                    + "in a single response.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true
            )
    )
    public List<PackageReport> checkPackages(
            @McpToolParam(required = true, description = "List of npm package names to check.")
            List<String> names
    ) {
        return packageChecker.checkAll(names);
    }

    @McpTool(
            name = "check_install_command",
            description = "Parse a shell install command (e.g. 'npm install express lodahs some-pkg') and "
                    + "check every package name found in it for slopsquatting risk. Use this when the user "
                    + "or an AI assistant is about to run an install command, to catch risky packages before "
                    + "they are installed.",
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true
            )
    )
    public List<PackageReport> checkInstallCommand(
            @McpToolParam(required = true, description = "The raw install command, e.g. "
                    + "'npm install express lodahs some-ai-suggested-pkg'.")
            String command
    ) {
        return packageChecker.checkInstallCommand(command);
    }
}
