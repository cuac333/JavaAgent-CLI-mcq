package com.javagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsLastSession() throws IOException {
        Config config = Config.load(tempDir);
        ConversationManager manager = new ConversationManager(config);

        manager.addUserMessage("hello");
        manager.addAssistantMessage("world");
        manager.saveCurrentSession();

        ConversationManager reloaded = new ConversationManager(config);
        assertTrue(reloaded.loadLastSession());
        assertEquals(2, reloaded.messageCount());
        assertEquals("world", reloaded.lastResponse());
    }

    @Test
    void savesMultipleSessionsAndLoadsSpecificOne() throws IOException {
        Config config = Config.load(tempDir);
        ConversationManager manager = new ConversationManager(config);

        manager.addUserMessage("alpha");
        manager.addAssistantMessage("one");
        manager.saveCurrentSession("alpha-session");

        manager.startNewSession("beta-session");
        manager.addUserMessage("beta");
        manager.addAssistantMessage("two");
        manager.saveCurrentSession();

        assertEquals(2, manager.listSessions().size());
        assertTrue(manager.loadSession("alpha-session"));
        assertEquals("one", manager.lastResponse());
        assertEquals("alpha-session", manager.currentSessionTitle());
    }
}
