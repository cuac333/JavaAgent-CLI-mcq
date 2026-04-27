package com.javagent.model;

import java.util.List;

public record ModelResponse(
        ResponseType type,
        String content,
        List<ToolCall> toolCalls,
        String errorMessage
) {
    public enum ResponseType {
        TEXT,
        TOOL_CALLS,
        ERROR
    }

    public ModelResponse {
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static ModelResponse text(String content) {
        return new ModelResponse(ResponseType.TEXT, content, List.of(), "");
    }

    public static ModelResponse toolCalls(String content, List<ToolCall> toolCalls) {
        return new ModelResponse(ResponseType.TOOL_CALLS, content, toolCalls, "");
    }

    public static ModelResponse error(String errorMessage) {
        return new ModelResponse(ResponseType.ERROR, "", List.of(), errorMessage);
    }

    public boolean isText() {
        return type == ResponseType.TEXT;
    }

    public boolean isToolCalls() {
        return type == ResponseType.TOOL_CALLS;
    }

    public boolean isError() {
        return type == ResponseType.ERROR;
    }
}
