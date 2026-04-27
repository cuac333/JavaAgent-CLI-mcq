package com.javagent.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ListDirectoryTool implements Tool {
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "list_directory",
            "List files and directories with optional recursion.",
            Map.of(
                    "path", "Directory path to inspect. Defaults to current directory.",
                    "recursive", "Whether child directories should be traversed recursively.",
                    "limit", "Maximum number of entries to return."
            ),
            Map.of(
                    "path", "string",
                    "recursive", "boolean",
                    "limit", "integer"
            ),
            Set.of(),
            false,
            true,
            false,
            List.of("ls", "dir", "list_files")
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> input) {
        String rawPath = FileToolSupport.stringValue(input.get("path"));
        if (rawPath.isBlank()) {
            rawPath = ".";
        }

        Path path;
        try {
            path = FileToolSupport.normalizePath(rawPath);
        } catch (InvalidPathException e) {
            return ToolExecutionResult.error("Invalid path: " + rawPath);
        }

        if (!Files.exists(path)) {
            return ToolExecutionResult.error("Path not found: " + path);
        }
        if (!Files.isDirectory(path)) {
            return ToolExecutionResult.error("Path is not a directory: " + path);
        }

        boolean recursive = FileToolSupport.booleanValue(input.get("recursive"), false);
        int limit = Math.min(MAX_LIMIT, Math.max(1, FileToolSupport.intValue(input.get("limit"), DEFAULT_LIMIT)));

        try (Stream<Path> stream = recursive ? Files.walk(path) : Files.list(path)) {
            List<Path> entries = stream
                    .filter(candidate -> !candidate.equals(path))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(limit)
                    .toList();

            StringBuilder builder = new StringBuilder();
            builder.append("Directory: ").append(path.toAbsolutePath()).append(System.lineSeparator());
            builder.append("recursive=").append(recursive).append(System.lineSeparator());
            builder.append("-----").append(System.lineSeparator());
            for (Path entry : entries) {
                String marker = Files.isDirectory(entry) ? "[D] " : "[F] ";
                String display = recursive
                        ? path.toAbsolutePath().normalize().relativize(entry.toAbsolutePath().normalize()).toString()
                        : entry.getFileName().toString();
                builder.append(marker).append(display).append(System.lineSeparator());
            }
            builder.append("-----").append(System.lineSeparator())
                    .append("entries=").append(entries.size());
            if (entries.size() == limit) {
                builder.append(" (limit reached)");
            }
            return ToolExecutionResult.success(builder.toString().trim());
        } catch (IOException e) {
            return ToolExecutionResult.error("Failed to list directory: " + e.getMessage());
        }
    }
}
