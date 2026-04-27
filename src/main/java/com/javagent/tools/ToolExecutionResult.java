package com.javagent.tools;

public record ToolExecutionResult(
        String content,
        boolean error
) {
    public ToolExecutionResult {
        content = content == null ? "" : content;
    }

    public static ToolExecutionResult success(String content) {
        return new ToolExecutionResult(content, false);
    }

    public static ToolExecutionResult error(String content) {
        return new ToolExecutionResult(content, true);
    }
}
