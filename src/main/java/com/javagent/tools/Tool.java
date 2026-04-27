package com.javagent.tools;

import java.util.Map;

public interface Tool {
    ToolDefinition definition();

    ToolExecutionResult execute(Map<String, Object> input);
}
