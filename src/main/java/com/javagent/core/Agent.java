package com.javagent.core;

import com.javagent.model.ModelClient;
import com.javagent.model.ModelResponse;
import com.javagent.model.TextStreamHandler;
import com.javagent.model.ToolCall;
import com.javagent.tools.Tool;
import com.javagent.tools.ToolDefinition;
import com.javagent.tools.ToolExecutionResult;
import com.javagent.tools.ToolRegistry;

import java.io.IOException;
import java.util.List;

public class Agent {
    private final Config config;
    private final ModelClient modelClient;
    private final ToolRegistry toolRegistry;
    private final ConversationManager conversationManager;
    private final ApprovalManager approvalManager;

    public Agent(Config config, ModelClient modelClient, ToolRegistry toolRegistry, ConversationManager conversationManager) {
        this.config = config;
        this.modelClient = modelClient;
        this.toolRegistry = toolRegistry;
        this.conversationManager = conversationManager;
        this.approvalManager = new ApprovalManager(config);
    }

    public String processTurn(String userInput, ApprovalHandler approvalHandler) {
        return processTurn(userInput, approvalHandler, null);
    }

    public String processTurn(String userInput, ApprovalHandler approvalHandler, TextStreamHandler streamHandler) {
        conversationManager.addUserMessage(userInput);
        String systemPrompt = buildSystemPrompt(toolRegistry.definitions());

        for (int iteration = 0; iteration < config.maxIterations(); iteration++) {
            TextStreamHandler effectiveStreamHandler = config.streamResponses() ? streamHandler : null;
            ModelResponse response = modelClient.chat(
                    systemPrompt,
                    conversationManager.currentContext(),
                    toolRegistry.definitions(),
                    effectiveStreamHandler
            );
            if (response.isError()) {
                String errorText = "Agent error: " + response.errorMessage();
                conversationManager.addAssistantMessage(errorText);
                autoSaveQuietly();
                return errorText;
            }

            if (response.isText()) {
                conversationManager.addAssistantMessage(response.content());
                autoSaveQuietly();
                return response.content();
            }

            conversationManager.addAssistantToolCallMessage(response.content(), response.toolCalls());
            for (ToolCall toolCall : response.toolCalls()) {
                ToolExecutionResult result = executeToolCall(toolCall, approvalHandler);
                conversationManager.addToolResultMessage(toolCall.id(), toolCall.name(), result.content(), result.error());
            }
        }

        String limitText = "Agent stopped after reaching the max tool-call iterations (" + config.maxIterations() + ").";
        conversationManager.addAssistantMessage(limitText);
        autoSaveQuietly();
        return limitText;
    }

    private ToolExecutionResult executeToolCall(ToolCall toolCall, ApprovalHandler approvalHandler) {
        Tool tool = toolRegistry.find(toolCall.name()).orElse(null);
        if (tool == null) {
            return ToolExecutionResult.error("Tool not found: " + toolCall.name());
        }

        ApprovalOutcome approvalOutcome = approvalManager.authorize(tool, toolCall, approvalHandler);
        if (!approvalOutcome.approved()) {
            return ToolExecutionResult.error(approvalOutcome.reason());
        }

        return tool.execute(toolCall.input());
    }

    private String buildSystemPrompt(List<ToolDefinition> tools) {
        StringBuilder builder = new StringBuilder();
        builder.append("You are JavaAgent CLI, a concise coding assistant.").append(System.lineSeparator());
        builder.append("Use tools only when they materially help answer the request.").append(System.lineSeparator());
        builder.append("Operate inside the workspace by default and avoid destructive actions unless explicitly requested.").append(System.lineSeparator());
        if (!config.customSystemPrompt().isBlank()) {
            builder.append("Additional system instructions:").append(System.lineSeparator());
            builder.append(config.customSystemPrompt()).append(System.lineSeparator());
        }
        builder.append("Available tools:").append(System.lineSeparator());
        for (ToolDefinition tool : tools) {
            builder.append("- ")
                    .append(tool.name())
                    .append(": ")
                    .append(tool.description())
                    .append(" | required params=")
                    .append(tool.requiredParameters())
                    .append(System.lineSeparator());
        }
        return builder.toString().trim();
    }

    private void autoSaveQuietly() {
        if (!config.autoSave()) {
            return;
        }
        try {
            conversationManager.saveCurrentSession();
        } catch (IOException ignored) {
            // Keep the CLI usable even if persistence fails.
        }
    }

    public int approvalCacheSize() {
        return approvalManager.cacheSize();
    }

    public void clearApprovalCache() {
        approvalManager.clearCache();
    }
}
