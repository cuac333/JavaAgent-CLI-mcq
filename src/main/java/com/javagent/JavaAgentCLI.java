package com.javagent;

import com.javagent.core.Agent;
import com.javagent.core.ApprovalDecision;
import com.javagent.core.Config;
import com.javagent.core.ConversationManager;
import com.javagent.model.MockModelClient;
import com.javagent.model.ModelClient;
import com.javagent.model.OpenAiCompatibleModelClient;
import com.javagent.model.TextStreamHandler;
import com.javagent.model.ToolCall;
import com.javagent.tools.BashTool;
import com.javagent.tools.DeleteFileTool;
import com.javagent.tools.GrepTool;
import com.javagent.tools.ListDirectoryTool;
import com.javagent.tools.ReadFileTool;
import com.javagent.tools.ToolDefinition;
import com.javagent.tools.ToolRegistry;
import com.javagent.tools.WriteFileTool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class JavaAgentCLI {
    private static final DateTimeFormatter SESSION_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private Config config;
    private ConversationManager conversationManager;
    private ToolRegistry toolRegistry;
    private ModelClient modelClient;
    private Agent agent;

    public static void main(String[] args) throws Exception {
        new JavaAgentCLI().run(args);
    }

    private void run(String[] args) throws Exception {
        config = Config.loadDefault();
        applyArgs(args);
        rebuildRuntime();
        startCli();
    }

    private void applyArgs(String[] args) throws IOException {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--mock" -> config.setMockMode(true);
                case "--real" -> config.setMockMode(false);
                case "--api-key" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--api-key requires a value");
                    }
                    config.setApiKey(args[++i]);
                }
                case "--help" -> {
                    printHelp();
                    System.exit(0);
                }
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
    }

    private void rebuildRuntime() {
        conversationManager = conversationManager == null ? new ConversationManager(config) : conversationManager;
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadFileTool());
        toolRegistry.register(new GrepTool());
        toolRegistry.register(new ListDirectoryTool());
        toolRegistry.register(new WriteFileTool());
        toolRegistry.register(new DeleteFileTool());
        if (config.bashEnabled()) {
            toolRegistry.register(new BashTool());
        }
        modelClient = config.isMockMode()
                ? new MockModelClient()
                : new OpenAiCompatibleModelClient(config);
        agent = new Agent(config, modelClient, toolRegistry, conversationManager);
    }

    private void startCli() throws IOException {
        printStartupBanner();
        System.out.println("Mode: " + (config.isMockMode() ? "mock" : "real"));
        System.out.println("Type /help for commands.");

        if (conversationManager.loadLastSession()) {
            System.out.println("Restored previous session: " + conversationManager.sessionStats());
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("javaagent> ");
                String input = reader.readLine();
                if (input == null) {
                    System.out.println();
                    return;
                }
                input = input.trim();
                if (input.isEmpty()) {
                    continue;
                }
                if (input.startsWith("/")) {
                    if (handleCommand(input)) {
                        continue;
                    }
                }

                ConsoleTextStreamHandler streamHandler = new ConsoleTextStreamHandler();
                String response = agent.processTurn(input, toolCall -> promptApproval(reader, toolCall), streamHandler);
                if (!streamHandler.emitted()) {
                    System.out.println(response);
                } else {
                    System.out.println();
                }
            }
        }
    }

    private boolean handleCommand(String input) throws IOException {
        String[] parts = input.split("\\s+", 2);
        String command = parts[0];
        String argument = parts.length > 1 ? parts[1].trim() : "";

        switch (command) {
            case "/help" -> {
                printHelp();
                return true;
            }
            case "/exit", "/quit" -> {
                System.out.println("Goodbye.");
                System.exit(0);
                return true;
            }
            case "/clear", "/new" -> {
                conversationManager.startNewSession(argument.isBlank() ? null : argument);
                System.out.println("Started a new session: " + conversationManager.sessionStats());
                return true;
            }
            case "/save" -> {
                conversationManager.saveCurrentSession(argument.isBlank() ? null : argument);
                System.out.println("Session saved: " + conversationManager.sessionStats());
                return true;
            }
            case "/load" -> {
                boolean loaded = argument.isBlank()
                        ? conversationManager.loadLastSession()
                        : conversationManager.loadSession(argument);
                if (loaded) {
                    System.out.println("Session loaded: " + conversationManager.sessionStats());
                } else {
                    System.out.println("No saved session matched that query.");
                }
                return true;
            }
            case "/sessions" -> {
                printSessions();
                return true;
            }
            case "/tools" -> {
                System.out.println(toolRegistry.listTools());
                return true;
            }
            case "/mode" -> {
                if (!argument.equals("mock") && !argument.equals("real")) {
                    System.out.println("Usage: /mode mock|real");
                    return true;
                }
                config.setMockMode(argument.equals("mock"));
                rebuildRuntime();
                System.out.println("Switched to " + argument + " mode.");
                if (!config.isMockMode() && config.apiKey().isBlank()) {
                    System.out.println("Warning: real mode is selected but no API key is configured.");
                }
                return true;
            }
            case "/stream" -> {
                if (!argument.equals("on") && !argument.equals("off")) {
                    System.out.println("Usage: /stream on|off");
                    return true;
                }
                config.setStreamResponses(argument.equals("on"));
                System.out.println("Streaming responses: " + (config.streamResponses() ? "enabled" : "disabled"));
                return true;
            }
            case "/bash" -> {
                if (!argument.equals("on") && !argument.equals("off")) {
                    System.out.println("Usage: /bash on|off");
                    return true;
                }
                config.setBashEnabled(argument.equals("on"));
                rebuildRuntime();
                System.out.println("Bash tool: " + (config.bashEnabled() ? "enabled" : "disabled"));
                return true;
            }
            case "/prompt" -> {
                handlePromptCommand(argument);
                return true;
            }
            case "/approvals" -> {
                if (argument.equals("clear")) {
                    agent.clearApprovalCache();
                    System.out.println("Approval cache cleared.");
                } else {
                    System.out.println("approvalCacheEntries=" + agent.approvalCacheSize());
                    System.out.println("Usage: /approvals clear");
                }
                return true;
            }
            case "/status" -> {
                printStatus();
                return true;
            }
            default -> {
                System.out.println("Unknown command. Type /help for the supported commands.");
                return true;
            }
        }
    }

    private void handlePromptCommand(String argument) throws IOException {
        if (argument.isBlank() || argument.equals("show")) {
            if (config.customSystemPrompt().isBlank()) {
                System.out.println("Custom system prompt is not set.");
            } else {
                System.out.println("Custom system prompt:");
                System.out.println(config.customSystemPrompt());
            }
            return;
        }

        if (argument.equals("reset")) {
            config.clearCustomSystemPrompt();
            System.out.println("Custom system prompt cleared.");
            return;
        }

        if (argument.startsWith("set ")) {
            config.setCustomSystemPrompt(argument.substring(4).trim());
            System.out.println("Custom system prompt updated.");
            return;
        }

        System.out.println("Usage: /prompt show | /prompt set <text> | /prompt reset");
    }

    private void printSessions() throws IOException {
        List<ConversationManager.SessionSummary> sessions = conversationManager.listSessions();
        if (sessions.isEmpty()) {
            System.out.println("No saved sessions.");
            return;
        }

        for (ConversationManager.SessionSummary session : sessions) {
            System.out.println("- " + shortId(session.id())
                    + " | " + session.title()
                    + " | " + SESSION_TIME_FORMAT.format(session.lastUpdated())
                    + " | messages=" + session.messageCount());
        }
    }

    private ApprovalDecision promptApproval(BufferedReader reader, ToolCall toolCall) {
        System.out.println("Approval required:");
        System.out.println("  tool: " + toolCall.name());
        System.out.println("  args: " + summarizeArgs(toolCall.input()));
        System.out.print("Approve? [yes/no/cancel]: ");
        try {
            String decision = reader.readLine();
            if (decision == null) {
                return ApprovalDecision.CANCELLED;
            }
            return switch (decision.trim().toLowerCase(Locale.ROOT)) {
                case "y", "yes" -> ApprovalDecision.APPROVED;
                case "n", "no" -> ApprovalDecision.DENIED;
                default -> ApprovalDecision.CANCELLED;
            };
        } catch (IOException e) {
            return ApprovalDecision.CANCELLED;
        }
    }

    private String summarizeArgs(Map<String, Object> input) {
        return input.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> entry.getKey() + "=" + summarizeValue(entry.getValue()))
                .collect(Collectors.joining(", "));
    }

    private String summarizeValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString().replaceAll("\\s+", " ").trim();
        if (text.length() > 80) {
            return text.substring(0, 77) + "...";
        }
        return text;
    }

    private void printStatus() throws IOException {
        List<ToolDefinition> tools = toolRegistry.definitions();
        System.out.println("mode=" + (config.isMockMode() ? "mock" : "real"));
        System.out.println("modelClient=" + modelClient.name());
        System.out.println("session=" + conversationManager.currentSessionTitle() + " [" + shortId(conversationManager.currentSessionId()) + "]");
        System.out.println("messages=" + conversationManager.messageCount());
        System.out.println("savedSessions=" + conversationManager.listSessions().size());
        System.out.println("tools=" + tools.size());
        System.out.println("bashEnabled=" + config.bashEnabled());
        System.out.println("streamResponses=" + config.streamResponses());
        System.out.println("approvalCacheEnabled=" + config.approvalCacheEnabled());
        System.out.println("approvalCacheEntries=" + agent.approvalCacheSize());
        System.out.println("allowExternalPaths=" + config.allowExternalPaths());
        System.out.println("customPromptSet=" + !config.customSystemPrompt().isBlank());
        System.out.println("config=" + config.configPath());
        System.out.println("sessionLegacy=" + config.sessionPath());
        System.out.println("sessionStore=" + config.sessionDirectory());
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  /help");
        System.out.println("  /exit | /quit");
        System.out.println("  /clear | /new [title]");
        System.out.println("  /save [title]");
        System.out.println("  /load [id|title|latest]");
        System.out.println("  /sessions");
        System.out.println("  /tools");
        System.out.println("  /mode mock|real");
        System.out.println("  /stream on|off");
        System.out.println("  /bash on|off");
        System.out.println("  /prompt show | /prompt set <text> | /prompt reset");
        System.out.println("  /approvals clear");
        System.out.println("  /status");
    }

    private void printStartupBanner() {
        System.out.println("==========================================");
        System.out.println("四川农业大学");
        System.out.println("信息工程学院");
        System.out.println("JavaAgent CLI 课程项目");
        System.out.println("作者：莫承潜 黄麟淞 王郅为 黄春云 胡鸿扬");
        System.out.println("==========================================");
    }

    private String shortId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "n/a";
        }
        return sessionId.length() <= 8 ? sessionId : sessionId.substring(0, 8);
    }

    private static final class ConsoleTextStreamHandler implements TextStreamHandler {
        private boolean emitted;

        @Override
        public void onChunk(String chunk) {
            emitted = true;
            System.out.print(chunk);
            System.out.flush();
        }

        private boolean emitted() {
            return emitted;
        }
    }
}
