package com.javagent.model;

public record ToolResultMessage(
        String toolCallId,
        String toolName,
        boolean error,
        String content
) {
    public static ToolResultMessage success(String toolCallId, String toolName, String content) {
        return new ToolResultMessage(toolCallId, toolName, false, content);
    }

    public static ToolResultMessage error(String toolCallId, String toolName, String content) {
        return new ToolResultMessage(toolCallId, toolName, true, content);
    }
}
