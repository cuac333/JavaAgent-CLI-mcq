package com.javagent.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE = "config.properties";
    private static final String SESSION_FILE = "last_session.json";

    private static final String KEY_MOCK_MODE = "agent.mock_mode";
    private static final String KEY_API_KEY = "agent.api_key";
    private static final String KEY_BASE_URL = "agent.base_url";
    private static final String KEY_MODEL = "agent.model";
    private static final String KEY_AUTO_SAVE = "agent.auto_save";
    private static final String KEY_MAX_ITERATIONS = "agent.max_iterations";
    private static final String KEY_ENABLE_BASH = "agent.enable_bash";
    private static final String KEY_STREAM_RESPONSES = "agent.stream_responses";
    private static final String KEY_SYSTEM_PROMPT = "agent.system_prompt";
    private static final String KEY_APPROVAL_CACHE = "agent.approval_cache";
    private static final String KEY_ALLOW_EXTERNAL_PATHS = "agent.allow_external_paths";

    private final Properties properties = new Properties();
    private final Path workingDirectory;
    private final Path appHome;
    private final Path configPath;

    private Config(Path workingDirectory, Path appHome, Path configPath) {
        this.workingDirectory = workingDirectory;
        this.appHome = appHome;
        this.configPath = configPath;
    }

    public static Config loadDefault() throws IOException {
        Path workingDirectory = Paths.get(System.getProperty("user.dir"));
        Path appHome = Paths.get(System.getProperty("user.home")).resolve(".javaagent-cli");
        return load(workingDirectory, appHome);
    }

    public static Config load(Path workingDirectory) throws IOException {
        return load(workingDirectory, workingDirectory.resolve(".javaagent-cli"));
    }

    private static Config load(Path workingDirectory, Path appHome) throws IOException {
        Path candidateInWorkingTree = workingDirectory.resolve(CONFIG_FILE);
        Path configPath = Files.exists(candidateInWorkingTree)
                ? candidateInWorkingTree
                : appHome.resolve(CONFIG_FILE);

        Config config = new Config(workingDirectory, appHome, configPath);
        config.read();
        config.ensureDefaults();
        config.save();
        return config;
    }

    private void read() throws IOException {
        if (!Files.exists(configPath)) {
            return;
        }
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            properties.load(inputStream);
        }
    }

    private void ensureDefaults() {
        properties.putIfAbsent(KEY_MOCK_MODE, "true");
        properties.putIfAbsent(KEY_API_KEY, "");
        properties.putIfAbsent(KEY_BASE_URL, "https://api.openai.com/v1");
        properties.putIfAbsent(KEY_MODEL, "gpt-5.4-mini");
        properties.putIfAbsent(KEY_AUTO_SAVE, "true");
        properties.putIfAbsent(KEY_MAX_ITERATIONS, "6");
        properties.putIfAbsent(KEY_ENABLE_BASH, "false");
        properties.putIfAbsent(KEY_STREAM_RESPONSES, "true");
        properties.putIfAbsent(KEY_SYSTEM_PROMPT, "");
        properties.putIfAbsent(KEY_APPROVAL_CACHE, "true");
        properties.putIfAbsent(KEY_ALLOW_EXTERNAL_PATHS, "false");
    }

    public void save() throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream outputStream = Files.newOutputStream(configPath)) {
            properties.store(outputStream, "JavaAgent CLI configuration");
        }
    }

    public Path configPath() {
        return configPath;
    }

    public Path stateDirectory() {
        if (Files.isWritable(workingDirectory)) {
            return workingDirectory.resolve(".javaagent-cli");
        }
        return appHome;
    }

    public Path sessionPath() {
        if (Files.isWritable(workingDirectory)) {
            return workingDirectory.resolve(SESSION_FILE);
        }
        return stateDirectory().resolve(SESSION_FILE);
    }

    public Path sessionDirectory() {
        return stateDirectory().resolve("sessions");
    }

    public Path currentSessionMarkerPath() {
        return stateDirectory().resolve("current-session.txt");
    }

    public boolean isMockMode() {
        return Boolean.parseBoolean(properties.getProperty(KEY_MOCK_MODE));
    }

    public void setMockMode(boolean mockMode) throws IOException {
        properties.setProperty(KEY_MOCK_MODE, Boolean.toString(mockMode));
        save();
    }

    public String apiKey() {
        return properties.getProperty(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) throws IOException {
        properties.setProperty(KEY_API_KEY, apiKey == null ? "" : apiKey.trim());
        save();
    }

    public String baseUrl() {
        return properties.getProperty(KEY_BASE_URL);
    }

    public String model() {
        return properties.getProperty(KEY_MODEL);
    }

    public boolean autoSave() {
        return Boolean.parseBoolean(properties.getProperty(KEY_AUTO_SAVE));
    }

    public int maxIterations() {
        return Integer.parseInt(properties.getProperty(KEY_MAX_ITERATIONS));
    }

    public boolean bashEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_ENABLE_BASH));
    }

    public void setBashEnabled(boolean enabled) throws IOException {
        properties.setProperty(KEY_ENABLE_BASH, Boolean.toString(enabled));
        save();
    }

    public boolean streamResponses() {
        return Boolean.parseBoolean(properties.getProperty(KEY_STREAM_RESPONSES));
    }

    public void setStreamResponses(boolean enabled) throws IOException {
        properties.setProperty(KEY_STREAM_RESPONSES, Boolean.toString(enabled));
        save();
    }

    public String customSystemPrompt() {
        return properties.getProperty(KEY_SYSTEM_PROMPT, "").trim();
    }

    public void setCustomSystemPrompt(String prompt) throws IOException {
        properties.setProperty(KEY_SYSTEM_PROMPT, prompt == null ? "" : prompt.trim());
        save();
    }

    public void clearCustomSystemPrompt() throws IOException {
        properties.setProperty(KEY_SYSTEM_PROMPT, "");
        save();
    }

    public boolean approvalCacheEnabled() {
        return Boolean.parseBoolean(properties.getProperty(KEY_APPROVAL_CACHE));
    }

    public void setApprovalCacheEnabled(boolean enabled) throws IOException {
        properties.setProperty(KEY_APPROVAL_CACHE, Boolean.toString(enabled));
        save();
    }

    public boolean allowExternalPaths() {
        return Boolean.parseBoolean(properties.getProperty(KEY_ALLOW_EXTERNAL_PATHS));
    }

    public void setAllowExternalPaths(boolean enabled) throws IOException {
        properties.setProperty(KEY_ALLOW_EXTERNAL_PATHS, Boolean.toString(enabled));
        save();
    }

    public Path workingDirectory() {
        return workingDirectory;
    }
}
