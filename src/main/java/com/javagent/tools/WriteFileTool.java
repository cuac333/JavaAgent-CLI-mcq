package com.javagent.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WriteFileTool implements Tool {
    private static final int MAX_CONTENT_CHARS = 100_000;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            "write_file",
            "Write UTF-8 text to a file, optionally appending.",
            Map.of(
                    "path", "File path to write.",
                    "content", "UTF-8 text content to store.",
                    "append", "Whether content should be appended instead of overwritten."
            ),
            Map.of(
                    "path", "string",
                    "content", "string",
                    "append", "boolean"
            ),
            Set.of("path", "content"),
            true,
            false,
            true,
            List.of("write", "save_file", "create_file")
    );

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> input) {
        String rawPath = FileToolSupport.stringValue(input.get("path"));
        if (rawPath.isBlank()) {
            return ToolExecutionResult.error("write_file requires a non-empty path.");
        }

        String content = input.get("content") == null ? "" : input.get("content").toString();
        if (content.length() > MAX_CONTENT_CHARS) {
            return ToolExecutionResult.error("Content is too large to write safely.");
        }

        Path path;
        try {
            path = FileToolSupport.normalizePath(rawPath);
        } catch (InvalidPathException e) {
            return ToolExecutionResult.error("Invalid path: " + rawPath);
        }

        boolean append = FileToolSupport.booleanValue(input.get("append"), false);

        try {
            if (Files.exists(path) && Files.isDirectory(path)) {
                return ToolExecutionResult.error("Path is a directory, not a file: " + path);
            }
            if (Files.exists(path) && Files.isRegularFile(path) && FileToolSupport.isBinary(path)) {
                return ToolExecutionResult.error("Refusing to overwrite a binary file.");
            }
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            if (append) {
                Files.writeString(
                        path,
                        content,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND
                );
            } else {
                Files.writeString(
                        path,
                        content,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE
                );
            }

            return ToolExecutionResult.success(
                    "Wrote " + content.length() + " characters to " + path.toAbsolutePath() + " (append=" + append + ")."
            );
        } catch (IOException e) {
            return ToolExecutionResult.error("Failed to write file: " + e.getMessage());
        }
    }
}
