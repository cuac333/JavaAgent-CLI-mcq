package com.javagent.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.javagent.core.Config;
import com.javagent.tools.ToolDefinition;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OpenAiCompatibleModelClient implements ModelClient {
    private final Config config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleModelClient(Config config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public ModelResponse chat(String systemPrompt, List<Message> messages, List<ToolDefinition> tools) {
        if (config.apiKey().isBlank()) {
            return ModelResponse.error("Real mode requires an API key. Configure agent.api_key or switch back to mock mode.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(systemPrompt, messages, tools)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return ModelResponse.error("Real mode request failed: HTTP " + response.statusCode() + " -> " + response.body());
            }
            return parseResponse(response.body());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return ModelResponse.error("Real mode request failed: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    private String buildRequestBody(String systemPrompt, List<Message> messages, List<ToolDefinition> tools) throws JsonProcessingException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.model());
        root.put("temperature", 0.0);
        root.put("tool_choice", "auto");

        ArrayNode messagesNode = objectMapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messagesNode.add(systemMessage);
        }

        for (Message message : messages) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("role", message.role().apiValue());

            switch (message.role()) {
                case USER, SYSTEM -> node.put("content", message.content());
                case ASSISTANT -> {
                    node.put("content", message.content());
                    if (message.hasToolCalls()) {
                        ArrayNode toolCallsNode = objectMapper.createArrayNode();
                        for (ToolCall toolCall : message.toolCalls()) {
                            ObjectNode toolCallNode = objectMapper.createObjectNode();
                            toolCallNode.put("id", toolCall.id());
                            toolCallNode.put("type", "function");
                            ObjectNode functionNode = objectMapper.createObjectNode();
                            functionNode.put("name", toolCall.name());
                            functionNode.put("arguments", objectMapper.writeValueAsString(toolCall.input()));
                            toolCallNode.set("function", functionNode);
                            toolCallsNode.add(toolCallNode);
                        }
                        node.set("tool_calls", toolCallsNode);
                    }
                }
                case TOOL -> {
                    if (message.toolResult() == null) {
                        node.put("content", message.content());
                    } else {
                        node.put("tool_call_id", message.toolResult().toolCallId());
                        node.put("content", message.toolResult().content());
                    }
                }
            }
            messagesNode.add(node);
        }
        root.set("messages", messagesNode);

        ArrayNode toolsNode = objectMapper.createArrayNode();
        for (ToolDefinition tool : tools) {
            ObjectNode toolNode = objectMapper.createObjectNode();
            toolNode.put("type", "function");
            ObjectNode functionNode = objectMapper.createObjectNode();
            functionNode.put("name", tool.name());
            functionNode.put("description", tool.description());
            ObjectNode parametersNode = objectMapper.createObjectNode();
            parametersNode.put("type", "object");
            ObjectNode propertiesNode = objectMapper.createObjectNode();
            tool.parameterDescriptions().forEach((name, description) -> {
                ObjectNode propertyNode = objectMapper.createObjectNode();
                propertyNode.put("type", tool.parameterTypes().getOrDefault(name, "string"));
                propertyNode.put("description", description);
                propertiesNode.set(name, propertyNode);
            });
            parametersNode.set("properties", propertiesNode);
            ArrayNode requiredNode = objectMapper.createArrayNode();
            for (String name : tool.requiredParameters()) {
                requiredNode.add(name);
            }
            parametersNode.set("required", requiredNode);
            functionNode.set("parameters", parametersNode);
            toolNode.set("function", functionNode);
            toolsNode.add(toolNode);
        }
        root.set("tools", toolsNode);

        return objectMapper.writeValueAsString(root);
    }

    private ModelResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(responseBody);
        if (root.has("error")) {
            return ModelResponse.error(root.path("error").path("message").asText("Unknown API error"));
        }

        JsonNode messageNode = root.path("choices").path(0).path("message");
        if (messageNode.isMissingNode()) {
            return ModelResponse.error("Real mode response did not contain a message.");
        }

        JsonNode toolCallsNode = messageNode.path("tool_calls");
        if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
            List<ToolCall> toolCalls = objectMapper.convertValue(toolCallsNode, new TypeReference<List<JsonNode>>() {})
                    .stream()
                    .map(node -> {
                        String id = node.path("id").asText();
                        String name = node.path("function").path("name").asText();
                        String rawArguments = node.path("function").path("arguments").asText("{}");
                        Map<String, Object> input = parseArguments(rawArguments);
                        return new ToolCall(id, name, input);
                    })
                    .toList();
            return ModelResponse.toolCalls(extractContent(messageNode.path("content")), toolCalls);
        }

        return ModelResponse.text(extractContent(messageNode.path("content")));
    }

    private Map<String, Object> parseArguments(String rawArguments) {
        try {
            return objectMapper.readValue(rawArguments, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return Map.of("raw", rawArguments);
        }
    }

    private String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return "";
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode block : contentNode) {
                if (block.has("text")) {
                    builder.append(block.path("text").asText());
                }
            }
            return builder.toString();
        }
        return contentNode.toString();
    }
}
