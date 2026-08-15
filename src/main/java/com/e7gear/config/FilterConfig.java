package com.e7gear.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Externalized decision thresholds for the gear filter.
 * Loaded from filter-config.json (or uses defaults).
 */
public record FilterConfig(
        @JsonProperty(defaultValue = "56.0") double reviewScore,
        @JsonProperty(defaultValue = "64.0") double keepScore,
        @JsonProperty(defaultValue = "64.0") double reforgeThreshold,
        @JsonProperty(defaultValue = "2") int strongCoreStats,
        @JsonProperty(defaultValue = "1") int reviewCoreStats,
        @JsonProperty(defaultValue = "15") int highSpeed,
        @JsonProperty(defaultValue = "4.0") double rightSidePenalty,
        @JsonProperty(defaultValue = "15") int openerSpeedThreshold,

        @JsonProperty Map<String, Map<String, Integer>> modGemMax,
        @JsonProperty Map<String, Double> setMultipliers,
        @JsonProperty(defaultValue = "62.0") double modGemThreshold,

        @JsonProperty(defaultValue = "100") int parallelThreshold,
        @JsonProperty(defaultValue = "true") boolean useCache
) {
    @JsonCreator
    public FilterConfig {
        if (modGemMax == null) {
            modGemMax = defaultModGemMax();
        }
        if (setMultipliers == null) {
            setMultipliers = defaultSetMultipliers();
        }
    }

    public static FilterConfig defaults() {
        return new FilterConfig(
                56.0, 64.0, 64.0, 2, 1, 15, 4.0, 17,
                defaultModGemMax(),
                defaultSetMultipliers(),
                60.0, 100, true
        );
    }

    private static Map<String, Map<String, Integer>> defaultModGemMax() {
        return Map.ofEntries(
                // Percentage stats
                Map.entry("AttackPercent", Map.of("85", 8, "88", 9, "90", 9)),
                Map.entry("DefensePercent", Map.of("85", 8, "88", 9, "90", 9)),
                Map.entry("HealthPercent", Map.of("85", 8, "88", 9, "90", 9)),
                Map.entry("EffectivenessPercent", Map.of("85", 8, "88", 9, "90", 9)),
                Map.entry("EffectResistancePercent", Map.of("85", 8, "88", 9, "90", 9)),

                // Special stats
                Map.entry("CriticalHitChancePercent", Map.of("85", 5, "88", 6, "90", 6)),
                Map.entry("CriticalHitDamagePercent", Map.of("85", 7, "88", 8, "90", 8)),
                Map.entry("Speed", Map.of("85", 4, "88", 5, "90", 5)),

                // Flat stats
                Map.entry("Attack", Map.of("85", 39, "88", 46, "90", 46)),
                Map.entry("Defense", Map.of("85", 31, "88", 36, "90", 36)),
                Map.entry("Health", Map.of("85", 174, "88", 202, "90", 202))
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
