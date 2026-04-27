package com.javagent.tools;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ToolDefinition(
        String name,
        String description,
        Map<String, String> parameterDescriptions,
        Map<String, String> parameterTypes,
        Set<String> requiredParameters,
        boolean requiresApproval,
        boolean readOnly,
        boolean destructive,
        List<String> aliases
) {
    public ToolDefinition {
        parameterDescriptions = parameterDescriptions == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parameterDescriptions));
        parameterTypes = parameterTypes == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(parameterTypes));
        requiredParameters = requiredParameters == null
                ? Set.of()
                : Set.copyOf(new LinkedHashSet<>(requiredParameters));
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
    }
}
