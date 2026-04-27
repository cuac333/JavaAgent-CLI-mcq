package com.javagent.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BashTool implements Tool {
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_OUTPUT_CHARS = 50_000;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "bash",
            "Run a shell command. This tool is disabled by default and always requires approval.",
            Map.of(
                    "command", "Shell command to execute.",
                    "timeoutSeconds", "Maximum runtime before the process is terminated."
            ),
            Map.of(
                    "command", "string",
                    "timeoutSeconds", "integer"
            ),
            Set.of("command"),
            true,
            false,
            true,
            List.of("shell", "exec", "run")
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> input) {
        String command = stringValue(input.get("command"));
        if (command.isBlank()) {
            return ToolExecutionResult.error("bash requires a non-empty command.");
        }
        if (looksDangerous(command)) {
            return ToolExecutionResult.error("Command rejected by the built-in safety policy.");
        }

        int timeoutSeconds = intValue(input.get("timeoutSeconds"), DEFAULT_TIMEOUT_SECONDS);
        ProcessBuilder processBuilder = new ProcessBuilder("/bin/bash", "-lc", command);
        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append(System.lineSeparator());
                    if (builder.length() > MAX_OUTPUT_CHARS) {
                        builder.append("... (output truncated)");
                        break;
                    }
                }
                output = builder.toString().trim();
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolExecutionResult.error("Command timed out after " + timeoutSeconds + " seconds.");
            }

            String result = "$ " + command + System.lineSeparator() + output + System.lineSeparator() + "[exit=" + process.exitValue() + "]";
            if (process.exitValue() == 0) {
                return ToolExecutionResult.success(result.trim());
            }
            return ToolExecutionResult.error(result.trim());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolExecutionResult.error("Command execution failed: " + e.getMessage());
        }
    }

    private boolean looksDangerous(String command) {
        String normalized = command.toLowerCase();
        return normalized.contains("rm -rf /")
                || normalized.contains("rm -rf ~")
                || normalized.contains("mkfs")
                || normalized.contains("dd if=/dev/zero")
                || normalized.contains(":(){:|:&};:");
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String raw) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
