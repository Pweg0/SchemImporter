package com.schematicimporter.command;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Server-side translation resolver. Since this is a server-only mod,
 * Component.translatable() won't work — the client doesn't have our lang files.
 * Instead, we load en_us.json at startup and format strings server-side using
 * Component.literal().
 */
public final class ServerTranslations {

    private static final Map<String, String> TRANSLATIONS = new HashMap<>();

    private ServerTranslations() {}

    static {
        loadLang("assets/schematicimporter/lang/en_us.json");
    }

    private static void loadLang(String resourcePath) {
        try (InputStream is = ServerTranslations.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is != null) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> map = new Gson().fromJson(
                    new InputStreamReader(is, StandardCharsets.UTF_8), type);
                if (map != null) {
                    TRANSLATIONS.putAll(map);
                }
            }
        } catch (Exception e) {
            // Fallback: keys will be returned as-is
        }
    }

    /**
     * Format a translation key with arguments, server-side.
     * Falls back to the raw key if not found.
     */
    public static String get(String key, Object... args) {
        String pattern = TRANSLATIONS.getOrDefault(key, key);
        if (args.length == 0) {
            return pattern;
        }
        try {
            return String.format(pattern, args);
        } catch (Exception e) {
            return key;
        }
    }
}
