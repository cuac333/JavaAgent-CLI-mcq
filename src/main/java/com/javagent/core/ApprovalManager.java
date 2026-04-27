package com.javagent.core;

import com.javagent.model.ToolCall;
import com.javagent.tools.Tool;
import com.javagent.tools.ToolDefinition;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class ApprovalManager {
    private final Config config;
    private final Map<String, ApprovalDecision> approvalCache = new LinkedHashMap<>();

    public ApprovalManager(Config config) {
        this.config = config;
    }

    public ApprovalOutcome authorize(Tool tool, ToolCall toolCall, ApprovalHandler approvalHandler) {
        ToolDefinition definition = tool.definition();
        PolicyCheck policyCheck = evaluatePolicy(definition, toolCall.input());
        if (policyCheck.verdict() == PolicyVerdict.ALLOW) {
            return ApprovalOutcome.approved(policyCheck.reason());
        }
        if (policyCheck.verdict() == PolicyVerdict.DENY) {
            return ApprovalOutcome.denied(policyCheck.reason());
        }

        String cacheKey = cacheKey(definition.name(), toolCall.input());
        if (config.approvalCacheEnabled() && approvalCache.containsKey(cacheKey)) {
            ApprovalDecision cached = approvalCache.get(cacheKey);
            return cached.isApproved()
                    ? ApprovalOutcome.cachedApproved("Approval cache reused for " + definition.name() + ".")
                    : ApprovalOutcome.cachedDenied("Approval cache denied " + definition.name() + ".");
        }

        ApprovalDecision decision = approvalHandler.request(toolCall);
        if (config.approvalCacheEnabled() && decision != ApprovalDecision.CANCELLED) {
            approvalCache.put(cacheKey, decision);
        }

        if (decision.isApproved()) {
            return ApprovalOutcome.approved("Approved by the user.");
        }

        String reason = decision == ApprovalDecision.CANCELLED
                ? "Tool execution was cancelled by the user for " + definition.name() + "."
                : "Tool execution was denied by the user for " + definition.name() + ".";
        return ApprovalOutcome.denied(reason);
    }

    public int cacheSize() {
        return approvalCache.size();
    }

    public void clearCache() {
        approvalCache.clear();
    }

    private PolicyCheck evaluatePolicy(ToolDefinition definition, Map<String, Object> input) {
        String toolName = definition.name();

        if ("bash".equals(toolName) && !config.bashEnabled()) {
            return new PolicyCheck(PolicyVerdict.DENY, "The bash tool is disabled in the current configuration.");
        }

        Path path = extractPath(toolName, input);
        if (path != null && !config.allowExternalPaths() && !path.startsWith(workspaceRoot())) {
            return new PolicyCheck(PolicyVerdict.DENY, "Path is outside the workspace and external paths are disabled: " + path);
        }

        if (definition.destructive() && path != null && isProtectedPath(path)) {
            return new PolicyCheck(PolicyVerdict.DENY, "Protected internal path cannot be modified: " + path);
        }

        if ("delete_file".equals(toolName) && path != null) {
            if (path.equals(workspaceRoot())) {
                return new PolicyCheck(PolicyVerdict.DENY, "Refusing to delete the workspace root.");
            }
            if (Files.exists(path) && Files.isDirectory(path)) {
                return new PolicyCheck(PolicyVerdict.DENY, "delete_file only supports regular files, not directories.");
            }
        }

        if (!definition.requiresApproval()) {
            return new PolicyCheck(PolicyVerdict.ALLOW, "Read-only tool auto approved.");
        }

        return new PolicyCheck(PolicyVerdict.REQUIRE_APPROVAL, "Tool requires approval.");
    }

    private Path extractPath(String toolName, Map<String, Object> input) {
        String rawPath = switch (toolName) {
            case "bash" -> stringValue(input.get("workingDirectory"));
            default -> stringValue(input.get("path"));
        };

        if (rawPath.isBlank()) {
            return null;
        }

        try {
            return resolvePath(rawPath);
        } catch (InvalidPathException ignored) {
            return null;
        }
    }

    private Path resolvePath(String rawPath) {
        Path path = Paths.get(rawPath);
        if (!path.isAbsolute()) {
            path = config.workingDirectory().resolve(path);
        }
        return path.normalize().toAbsolutePath();
    }

    private boolean isProtectedPath(Path path) {
        Path normalized = path.normalize().toAbsolutePath();
        Path stateDirectory = config.stateDirectory().toAbsolutePath().normalize();
        Path configFile = config.configPath().toAbsolutePath().normalize();
        Path legacySession = config.sessionPath().toAbsolutePath().normalize();

        if (normalized.startsWith(stateDirectory) || normalized.equals(configFile) || normalized.equals(legacySession)) {
            return true;
        }

        for (Path part : normalized) {
            String value = part.toString();
            if (value.equals(".git") || value.equals(".javaagent-cli")) {
                return true;
            }
        }
        return false;
    }

    private Path workspaceRoot() {
        return config.workingDirectory().toAbsolutePath().normalize();
    }

    private String cacheKey(String toolName, Map<String, Object> input) {
        Map<String, String> normalized = new TreeMap<>(Comparator.naturalOrder());
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                normalized.put(key, "");
                continue;
            }
            if ((key.equals("path") || key.equals("workingDirectory")) && value instanceof String raw && !raw.isBlank()) {
                try {
                    normalized.put(key, resolvePath(raw).toString());
                    continue;
                } catch (InvalidPathException ignored) {
                    // fall back to raw value
                }
            }
            normalized.put(key, value.toString());
        }
        return toolName.toLowerCase(Locale.ROOT) + "::" + normalized;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private enum PolicyVerdict {
        ALLOW,
        REQUIRE_APPROVAL,
        DENY
    }

    private record PolicyCheck(
            PolicyVerdict verdict,
            String reason
    ) {
    }
}
