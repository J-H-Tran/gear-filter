package com.e7gear.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_CONFIG_PATH = "filter-config.json";

    public static FilterConfig load() {
        return load(Path.of(DEFAULT_CONFIG_PATH));
    }

    public static FilterConfig load(Path path) {
        if (Files.exists(path)) {
            try {
                return MAPPER.readValue(path.toFile(), FilterConfig.class);
            } catch (IOException e) {
                System.err.println("Failed to load config from " + path + ": " + e.getMessage());
                System.err.println("Using default configuration.");
            }
        } else {
            System.out.println("Config file not found at " + path + ". Using defaults.");
        }
        return FilterConfig.defaults();
    }
}
