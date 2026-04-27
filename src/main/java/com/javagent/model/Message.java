package com.javagent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record Message(
        String id,
        Role role,
        String content,
        LocalDateTime timestamp,
        List<ToolCall> toolCalls,
        ToolResultMessage toolResult
) {
    public Message {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        content = content == null ? "" : content;
        timestamp = timestamp == null ? LocalDateTime.now() : timestamp;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static Message system(String content) {
        return new Message(null, Role.SYSTEM, content, null, List.of(), null);
    }

    public static Message user(String content) {
        return new Message(null, Role.USER, content, null, List.of(), null);
    }

    public static Message assistant(String content) {
        return new Message(null, Role.ASSISTANT, content, null, List.of(), null);
    }

    public static Message assistantWithToolCalls(String content, List<ToolCall> toolCalls) {
        return new Message(null, Role.ASSISTANT, content, null, toolCalls, null);
    }

    public static Message toolResult(String toolCallId, String toolName, String content, boolean error) {
        ToolResultMessage result = error
                ? ToolResultMessage.error(toolCallId, toolName, content)
                : ToolResultMessage.success(toolCallId, toolName, content);
        return new Message(null, Role.TOOL, content, null, List.of(), result);
    }

    @JsonIgnore
    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    @JsonIgnore
    public boolean isToolError() {
        return toolResult != null && toolResult.error();
    }
}
