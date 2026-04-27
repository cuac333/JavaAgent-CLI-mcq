package com.javagent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.javagent.model.Message;
import com.javagent.model.Role;
import com.javagent.model.ToolCall;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class ConversationManager {
    private static final DateTimeFormatter TITLE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Config config;
    private final ObjectMapper objectMapper;

    private String currentSessionId;
    private String currentSessionTitle;
    private List<Message> messages = new ArrayList<>();
    private LocalDateTime sessionStart = LocalDateTime.now();
    private String lastResponse = "";

    public ConversationManager(Config config) {
        this.config = config;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
        startNewSession(null);
    }

    public void addUserMessage(String content) {
        messages.add(Message.user(content));
    }

    public void addAssistantMessage(String content) {
        messages.add(Message.assistant(content));
        lastResponse = content == null ? "" : content;
    }

    public void addAssistantToolCallMessage(String content, List<ToolCall> toolCalls) {
        messages.add(Message.assistantWithToolCalls(content, toolCalls));
    }

    public void addToolResultMessage(String toolCallId, String toolName, String content, boolean error) {
        messages.add(Message.toolResult(toolCallId, toolName, content, error));
    }

    public List<Message> currentContext() {
        return List.copyOf(messages);
    }

    public int messageCount() {
        return messages.size();
    }

    public String lastResponse() {
        return lastResponse;
    }

    public String currentSessionId() {
        return currentSessionId;
    }

    public String currentSessionTitle() {
        return currentSessionTitle;
    }

    public void clearCurrentSession() {
        startNewSession(null);
    }

    public void startNewSession(String title) {
        LocalDateTime now = LocalDateTime.now();
        this.currentSessionId = UUID.randomUUID().toString();
        this.currentSessionTitle = title == null || title.isBlank() ? defaultTitle(now) : title.trim();
        this.messages = new ArrayList<>();
        this.sessionStart = now;
        this.lastResponse = "";
    }

    public void saveCurrentSession() throws IOException {
        saveCurrentSession(null);
    }

    public void saveCurrentSession(String titleOverride) throws IOException {
        if (titleOverride != null && !titleOverride.isBlank()) {
            currentSessionTitle = titleOverride.trim();
        }

        SessionSnapshot snapshot = new SessionSnapshot(
                currentSessionId,
                currentSessionTitle,
                sessionStart,
                LocalDateTime.now(),
                List.copyOf(messages),
                lastResponse
        );

        Files.createDirectories(config.sessionDirectory());
        if (config.sessionPath().getParent() != null) {
            Files.createDirectories(config.sessionPath().getParent());
        }
        if (config.currentSessionMarkerPath().getParent() != null) {
            Files.createDirectories(config.currentSessionMarkerPath().getParent());
        }

        objectMapper.writeValue(sessionFile(currentSessionId).toFile(), snapshot);
        objectMapper.writeValue(config.sessionPath().toFile(), snapshot);
        Files.writeString(config.currentSessionMarkerPath(), currentSessionId);
    }

    public boolean loadLastSession() throws IOException {
        Path markerPath = config.currentSessionMarkerPath();
        if (Files.exists(markerPath)) {
            String sessionId = Files.readString(markerPath).trim();
            if (!sessionId.isBlank() && Files.exists(sessionFile(sessionId))) {
                applySnapshot(objectMapper.readValue(sessionFile(sessionId).toFile(), SessionSnapshot.class));
                return true;
            }
        }

        if (!Files.exists(config.sessionPath())) {
            return false;
        }

        SessionSnapshot snapshot = objectMapper.readValue(config.sessionPath().toFile(), SessionSnapshot.class);
        applySnapshot(snapshot);
        saveCurrentSession();
        return true;
    }

    public boolean loadSession(String query) throws IOException {
        List<SessionSummary> sessions = listSessions();
        if (sessions.isEmpty()) {
            return false;
        }

        SessionSummary summary;
        if (query == null || query.isBlank() || "latest".equalsIgnoreCase(query.trim())) {
            summary = sessions.getFirst();
        } else {
            String normalized = query.trim();
            summary = sessions.stream()
                    .filter(session -> session.id().equals(normalized)
                            || session.id().startsWith(normalized)
                            || session.title().equalsIgnoreCase(normalized)
                            || session.title().toLowerCase().contains(normalized.toLowerCase()))
                    .findFirst()
                    .orElse(null);
        }

        if (summary == null) {
            return false;
        }

        applySnapshot(objectMapper.readValue(sessionFile(summary.id()).toFile(), SessionSnapshot.class));
        Files.writeString(config.currentSessionMarkerPath(), currentSessionId);
        objectMapper.writeValue(config.sessionPath().toFile(), currentSnapshot());
        return true;
    }

    public List<SessionSummary> listSessions() throws IOException {
        if (!Files.exists(config.sessionDirectory())) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(config.sessionDirectory())) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readSummaryQuietly)
                    .filter(summary -> summary != null)
                    .sorted(Comparator.comparing(SessionSummary::lastUpdated).reversed())
                    .toList();
        }
    }

    public String sessionStats() {
        long userCount = messages.stream().filter(message -> message.role() == Role.USER).count();
        long assistantCount = messages.stream().filter(message -> message.role() == Role.ASSISTANT).count();
        long toolCount = messages.stream().filter(message -> message.role() == Role.TOOL).count();
        return "id=" + shortId(currentSessionId)
                + ", title=" + currentSessionTitle
                + ", messages=" + messages.size()
                + ", user=" + userCount
                + ", assistant=" + assistantCount
                + ", tool=" + toolCount;
    }

    private SessionSnapshot currentSnapshot() {
        return new SessionSnapshot(
                currentSessionId,
                currentSessionTitle,
                sessionStart,
                LocalDateTime.now(),
                List.copyOf(messages),
                lastResponse
        );
    }

    private SessionSummary readSummaryQuietly(Path file) {
        try {
            SessionSnapshot snapshot = objectMapper.readValue(file.toFile(), SessionSnapshot.class);
            String id = snapshot.sessionId() == null || snapshot.sessionId().isBlank()
                    ? file.getFileName().toString().replace(".json", "")
                    : snapshot.sessionId();
            String title = snapshot.title() == null || snapshot.title().isBlank()
                    ? defaultTitle(snapshot.sessionStart() == null ? LocalDateTime.now() : snapshot.sessionStart())
                    : snapshot.title();
            LocalDateTime updated = snapshot.lastUpdated() == null ? LocalDateTime.MIN : snapshot.lastUpdated();
            int messageCount = snapshot.messages() == null ? 0 : snapshot.messages().size();
            return new SessionSummary(id, title, updated, messageCount);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void applySnapshot(SessionSnapshot snapshot) {
        this.currentSessionId = snapshot.sessionId() == null || snapshot.sessionId().isBlank()
                ? UUID.randomUUID().toString()
                : snapshot.sessionId();
        this.currentSessionTitle = snapshot.title() == null || snapshot.title().isBlank()
                ? defaultTitle(snapshot.sessionStart() == null ? LocalDateTime.now() : snapshot.sessionStart())
                : snapshot.title();
        this.sessionStart = snapshot.sessionStart() == null ? LocalDateTime.now() : snapshot.sessionStart();
        this.messages = snapshot.messages() == null ? new ArrayList<>() : new ArrayList<>(snapshot.messages());
        this.lastResponse = snapshot.lastResponse() == null ? "" : snapshot.lastResponse();
    }

    private Path sessionFile(String sessionId) {
        return config.sessionDirectory().resolve(sessionId + ".json");
    }

    private String defaultTitle(LocalDateTime time) {
        return "Session " + TITLE_FORMAT.format(time);
    }

    private String shortId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "n/a";
        }
        return sessionId.length() <= 8 ? sessionId : sessionId.substring(0, 8);
    }

    public record SessionSummary(
            String id,
            String title,
            LocalDateTime lastUpdated,
            int messageCount
    ) {
    }

    public record SessionSnapshot(
            String sessionId,
            String title,
            LocalDateTime sessionStart,
            LocalDateTime lastUpdated,
            List<Message> messages,
            String lastResponse
    ) {
    }
}
