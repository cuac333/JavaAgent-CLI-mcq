package com.javagent.tools;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

final class FileToolSupport {
    private FileToolSupport() {
    }

    static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String raw) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    static boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String raw) {
            return Boolean.parseBoolean(raw);
        }
        return defaultValue;
    }

    static Path normalizePath(String rawPath) {
        try {
            return Paths.get(rawPath).normalize();
        } catch (InvalidPathException e) {
            throw e;
        }
    }

    static boolean isBinary(Path path) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(path);
        int inspected = Math.min(bytes.length, 1024);
        for (int i = 0; i < inspected; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
        try {
            decoder.decode(ByteBuffer.wrap(bytes, 0, inspected));
            return false;
        } catch (CharacterCodingException e) {
            return true;
        }
    }
}
