package com.javagent.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record ToolCall(
        String id,
        String name,
        Map<String, Object> input
) {
    public ToolCall {
        id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        input = input == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(input));
    }

    public static ToolCall of(String name, Map<String, Object> input) {
        return new ToolCall(UUID.randomUUID().toString(), name, input);
    }
}
