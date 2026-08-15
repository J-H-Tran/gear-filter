package com.e7gear.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Externalized decision thresholds for the gear filter.
 * Loaded from filter-config.json (or uses defaults).
 */
public record FilterConfig(
        @JsonProperty(defaultValue = "58.0") double reviewScore,
        @JsonProperty(defaultValue = "66.0") double keepScore,
        @JsonProperty(defaultValue = "2") int strongCoreStats,
        @JsonProperty(defaultValue = "1") int reviewCoreStats,
        @JsonProperty(defaultValue = "15") int highSpeed,
        @JsonProperty(defaultValue = "4.0") double rightSidePenalty,
        @JsonProperty(defaultValue = "17") int openerSpeedThreshold,

        @JsonProperty Map<String, Integer> modGemMax,
        @JsonProperty Map<String, Double> setMultipliers
) {
    @JsonCreator
    public FilterConfig {
        // handle null maps
        if (modGemMax == null) {
            modGemMax = defaultModGemMax();
        }
        if (setMultipliers == null) {
            setMultipliers = defaultSetMultipliers();
        }
    }

    public static FilterConfig defaults() {
        return new FilterConfig(
                58.0, 66.0, 2, 1, 15, 4.0, 17,
                defaultModGemMax(),
                defaultSetMultipliers()
        );
    }

    private static Map<String, Integer> defaultModGemMax() {
        return Map.of(
                "AttackPercent", 7,
                "CriticalHitChancePercent", 5,
                "CriticalHitDamagePercent", 7,
                "Speed", 4
        );
    }

    private static Map<String, Double> defaultSetMultipliers() {
        return Map.of(
                "SpeedSet", 1.3,
                "DestructionSet", 1.3,
                "PenetrationSet", 1.2,
                "TorrentSet", 1.2,
                "CounterSet", 1.2
        );
    }
}
