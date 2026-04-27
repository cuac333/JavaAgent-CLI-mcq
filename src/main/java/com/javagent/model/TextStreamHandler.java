package com.javagent.model;

@FunctionalInterface
public interface TextStreamHandler {
    void onChunk(String chunk);
}
