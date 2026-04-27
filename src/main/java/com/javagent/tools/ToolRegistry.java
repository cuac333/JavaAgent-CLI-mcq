package com.javagent.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ToolRegistry {
    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();
    private final Map<String, Tool> toolsByAlias = new LinkedHashMap<>();

    public void register(Tool tool) {
        ToolDefinition definition = tool.definition();
        toolsByName.put(definition.name().toLowerCase(Locale.ROOT), tool);
        for (String alias : definition.aliases()) {
            toolsByAlias.put(alias.toLowerCase(Locale.ROOT), tool);
        }
    }

    public Optional<Tool> find(String toolName) {
        if (toolName == null) {
            return Optional.empty();
        }
        String key = toolName.toLowerCase(Locale.ROOT);
        Tool tool = toolsByName.get(key);
        if (tool != null) {
            return Optional.of(tool);
        }
        return Optional.ofNullable(toolsByAlias.get(key));
    }

    public List<ToolDefinition> definitions() {
        return toolsByName.values().stream()
                .map(Tool::definition)
                .toList();
    }

    public int count() {
        return toolsByName.size();
    }

    public Collection<Tool> all() {
        return List.copyOf(toolsByName.values());
    }

    public String listTools() {
        List<ToolDefinition> definitions = new ArrayList<>(definitions());
        if (definitions.isEmpty()) {
            return "No tools registered.";
        }

        return definitions.stream()
                .map(def -> {
                    String aliases = def.aliases().isEmpty()
                            ? ""
                            : " [" + String.join(", ", def.aliases()) + "]";
                    String flags = def.requiresApproval()
                            ? " (approval required)"
                            : " (auto approved)";
                    return "- " + def.name() + aliases + ": " + def.description() + flags;
                })
                .collect(Collectors.joining(System.lineSeparator()));
    }
}
