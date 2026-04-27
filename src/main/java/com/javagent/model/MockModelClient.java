package com.javagent.model;

import com.javagent.tools.ToolDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MockModelClient implements ModelClient {
    private static final Pattern FILE_PATTERN = Pattern.compile("(README(?:\\.md)?|pom\\.xml|[A-Za-z0-9_./\\\\-]+\\.(?:java|xml|md|txt|json|ya?ml|properties))");
    private static final Pattern QUOTED_TEXT_PATTERN = Pattern.compile("[\"']([^\"']+)[\"']");

    @Override
    public ModelResponse chat(String systemPrompt, List<Message> messages, List<ToolDefinition> tools) {
        if (messages.isEmpty()) {
            return ModelResponse.text("Mock mode ready.");
        }

        Message lastMessage = messages.get(messages.size() - 1);
        if (lastMessage.role() == Role.TOOL && lastMessage.toolResult() != null) {
            return summarizeToolResult(lastMessage.toolResult());
        }

        Optional<Message> maybeLastUser = messages.stream()
                .filter(message -> message.role() == Role.USER)
                .reduce((first, second) -> second);

        if (maybeLastUser.isEmpty()) {
            return ModelResponse.text("Mock mode did not find a user message.");
        }

        String input = maybeLastUser.get().content().trim();
        String lower = input.toLowerCase(Locale.ROOT);

        if (hasTool(tools, "delete_file") && containsAny(lower, "删除", "delete", "remove")) {
            String path = extractPathToken(input).orElse("notes.txt");
            return ModelResponse.toolCalls(
                    "I should delete the requested file before answering.",
                    List.of(ToolCall.of("delete_file", Map.of("path", path)))
            );
        }

        if (hasTool(tools, "write_file") && containsAny(lower, "写入", "写到", "create file", "write", "save to", "保存到")) {
            String path = extractPathToken(input).orElse("notes.txt");
            String content = extractWriteContent(input, path);
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("path", path);
            args.put("content", content);
            args.put("append", lower.contains("append") || lower.contains("追加"));
            return ModelResponse.toolCalls(
                    "I should write the requested content to disk first.",
                    List.of(ToolCall.of("write_file", args))
            );
        }

        if (hasTool(tools, "list_directory") && containsAny(lower, "列出", "目录", "list", "ls", "files")) {
            String path = extractPathToken(input).orElse(".");
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("path", path);
            args.put("recursive", lower.contains("递归") || lower.contains("recursive"));
            args.put("limit", 50);
            return ModelResponse.toolCalls(
                    "I should inspect the directory contents first.",
                    List.of(ToolCall.of("list_directory", args))
            );
        }

        if (hasTool(tools, "read_file") && containsAny(lower, "读取", "read", "open", "查看文件", "show")) {
            String path = extractFilePath(input).orElseGet(() -> extractPathToken(input).orElse("README.md"));
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("path", path);
            args.put("limit", 200);
            return ModelResponse.toolCalls(
                    "I should inspect the file before answering.",
                    List.of(ToolCall.of("read_file", args))
            );
        }

        if (hasTool(tools, "grep") && containsAny(lower, "搜索", "grep", "search", "find")) {
            String pattern = extractSearchPattern(input);
            String path = extractSearchPath(input).orElse(".");
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("pattern", pattern);
            args.put("path", path);
            args.put("caseSensitive", false);
            return ModelResponse.toolCalls(
                    "I should search the project for matching text first.",
                    List.of(ToolCall.of("grep", args))
            );
        }

        if (hasTool(tools, "bash") && containsAny(lower, "执行命令", "run command", "bash", "shell")) {
            String command = extractQuotedText(input).orElse("pwd");
            return ModelResponse.toolCalls(
                    "I should run the requested command first.",
                    List.of(ToolCall.of("bash", Map.of("command", command))))
                    ;
        }

        if (containsAny(lower, "help", "工具", "tools")) {
            return ModelResponse.text("Mock mode can read files, search text, list directories, write files, delete files, and demonstrate the full agent loop.");
        }

        return ModelResponse.text("[mock] I understood your request, but it does not require a tool call. Ask me to read, search, list, write, or delete something.");
    }

    @Override
    public String name() {
        return "mock-model";
    }

    private ModelResponse summarizeToolResult(ToolResultMessage toolResult) {
        if (toolResult.error()) {
            return ModelResponse.text("The tool call failed: " + toolResult.content());
        }

        return switch (toolResult.toolName()) {
            case "read_file" -> ModelResponse.text("I read the requested file successfully. Here is a concise summary based on the tool output:\n\n"
                    + firstLines(toolResult.content(), 12));
            case "grep" -> ModelResponse.text("I searched the requested path. Here are the most relevant matches:\n\n"
                    + firstLines(toolResult.content(), 14));
            case "list_directory" -> ModelResponse.text("I listed the requested directory. Here are the most relevant entries:\n\n"
                    + firstLines(toolResult.content(), 16));
            case "write_file" -> ModelResponse.text("The file write completed successfully.\n\n" + firstLines(toolResult.content(), 8));
            case "delete_file" -> ModelResponse.text("The requested file was deleted successfully.\n\n" + firstLines(toolResult.content(), 8));
            case "bash" -> ModelResponse.text("The command finished. Summary:\n\n" + firstLines(toolResult.content(), 12));
            default -> ModelResponse.text("Tool execution completed:\n\n" + firstLines(toolResult.content(), 12));
        };
    }

    private boolean hasTool(List<ToolDefinition> tools, String name) {
        return tools.stream().anyMatch(tool -> tool.name().equals(name));
    }

    private boolean containsAny(String input, String... values) {
        for (String value : values) {
            if (input.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> extractFilePath(String input) {
        Matcher matcher = FILE_PATTERN.matcher(input);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<String> extractPathToken(String input) {
        String[] tokens = input.split("\\s+");
        for (String token : tokens) {
            String cleaned = token
                    .replace("，", "")
                    .replace(",", "")
                    .replace("。", "")
                    .replace("：", "")
                    .replace(":", "")
                    .replace("\"", "")
                    .replace("'", "");
            if (cleaned.isBlank()) {
                continue;
            }
            if (cleaned.equals(".") || cleaned.equals("..") || cleaned.startsWith("./") || cleaned.startsWith("../")
                    || cleaned.startsWith("/") || cleaned.contains("/") || cleaned.contains("\\")
                    || FILE_PATTERN.matcher(cleaned).matches()) {
                return Optional.of(cleaned);
            }
        }
        return Optional.empty();
    }

    private Optional<String> extractQuotedText(String input) {
        Matcher matcher = QUOTED_TEXT_PATTERN.matcher(input);
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private String extractWriteContent(String input, String path) {
        Matcher matcher = QUOTED_TEXT_PATTERN.matcher(input);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (!candidate.equals(path)) {
                return candidate;
            }
        }

        String normalized = input
                .replace(path, "")
                .replace("请", "")
                .replace("帮我", "")
                .replace("写入", "")
                .replace("写到", "")
                .replace("保存到", "")
                .replace("create file", "")
                .replace("write", "")
                .replace("save to", "")
                .trim();

        if (normalized.isBlank()) {
            return "Generated by JavaAgent CLI.";
        }
        return normalized;
    }

    private String extractSearchPattern(String input) {
        Matcher quotedMatcher = QUOTED_TEXT_PATTERN.matcher(input);
        if (quotedMatcher.find()) {
            return quotedMatcher.group(1);
        }

        String normalized = input
                .replace("请", "")
                .replace("帮我", "")
                .replace("搜索", "")
                .replace("search", "")
                .replace("grep", "")
                .replace("find", "")
                .trim();

        String[] parts = normalized.split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) {
            return "agent";
        }

        String token = parts[0];
        if (token.contains("/") && !token.startsWith("/") && !token.startsWith("./") && !token.contains(".")) {
            return token.replace("/", "|");
        }
        return token;
    }

    private Optional<String> extractSearchPath(String input) {
        Matcher matcher = FILE_PATTERN.matcher(input);
        if (matcher.find()) {
            String fileLike = matcher.group(1);
            if (!fileLike.equalsIgnoreCase("README") && !fileLike.equalsIgnoreCase("README.md")) {
                return Optional.of(fileLike);
            }
        }
        String[] parts = input.split("\\s+");
        for (String part : parts) {
            if (part.startsWith("./") || part.startsWith("/") || part.contains("\\")) {
                return Optional.of(part);
            }
        }
        return Optional.empty();
    }

    private String firstLines(String input, int maxLines) {
        String[] lines = input.split("\\R");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            builder.append(lines[i]).append(System.lineSeparator());
        }
        if (lines.length > maxLines) {
            builder.append("...").append(System.lineSeparator());
        }
        return builder.toString().trim();
    }
}
