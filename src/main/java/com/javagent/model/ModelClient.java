package com.javagent.model;

import com.javagent.tools.ToolDefinition;

import java.util.List;

public interface ModelClient {
    ModelResponse chat(String systemPrompt, List<Message> messages, List<ToolDefinition> tools);

    default ModelResponse chat(
            String systemPrompt,
            List<Message> messages,
            List<ToolDefinition> tools,
            TextStreamHandler streamHandler
    ) {
        ModelResponse response = chat(systemPrompt, messages, tools);
        if (streamHandler != null && response.isText()) {
            emitTextChunks(response.content(), streamHandler);
        }
        return response;
    }

    String name();

    private void emitTextChunks(String text, TextStreamHandler streamHandler) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int chunkSize = Math.max(12, Math.min(32, text.length() / 6 == 0 ? text.length() : text.length() / 6));
        for (int start = 0; start < text.length(); start += chunkSize) {
            int end = Math.min(text.length(), start + chunkSize);
            streamHandler.onChunk(text.substring(start, end));
        }
    }
}
