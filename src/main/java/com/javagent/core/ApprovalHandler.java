package com.javagent.core;

import com.javagent.model.ToolCall;

public interface ApprovalHandler {
    ApprovalDecision request(ToolCall toolCall);
}
