package com.e7gear.stats;

import java.util.HashMap;
import java.util.Map;

/**
 * All possible substat/main-stat types in Epic Seven.
 * Provides display names, abbreviations, categories, and scoring weights.
 */
public enum StatType {

    // Percentage stats
    ATTACK_PERCENT("AttackPercent", "Atk%", true, false, 1.0,
            "att_rate", "AttackPercent"),
    DEFENSE_PERCENT("DefensePercent", "Def%", true, false, 1.0,
            "def_rate", "DefensePercent"),
    HEALTH_PERCENT("HealthPercent", "Hp%", true, false, 1.0,
            "max_hp_rate", "HealthPercent"),
    CRIT_CHANCE("CriticalHitChancePercent", "CC%", true, false, 8.0/5.0,
            "cri", "CriticalHitChance", "CriticalHitChancePercent"),
    CRIT_DAMAGE("CriticalHitDamagePercent", "CDMG%", true, false, 8.0/7.0,
            "cri_dmg", "CriticalHitDamage", "CriticalHitDamagePercent"),
    EFFECTIVENESS("EffectivenessPercent", "Eff%", true, false, 1.0,
            "acc", "Effectiveness", "EffectivenessPercent"),
    EFFECT_RESISTANCE("EffectResistancePercent", "ER%", true, false, 1.0,
            "res", "EffectResistance", "EffectResistancePercent"),

    // Flat stats
    FLAT_ATTACK("Attack", "fAtk", false, true, 3.46/39.0,
            "att", "Attack", "FlatAttack"),
    FLAT_DEFENSE("Defense", "fDef", false, true, 4.99/31.0,
            "def", "Defense", "FlatDefense"),
    FLAT_HEALTH("Health", "fHp", false, true, 3.09/174.0,
            "max_hp", "Health", "FlatHealth"),

    // Special
    SPEED("Speed", "Spd", false, false, 8.0/4.0,
            "speed", "Speed");

    private final String displayName;
    private final String abbreviation;
    private final boolean isPercentage;
    private final boolean isFlat;
    private final double weight;          // scoring multiplier (from gear-score.txt)
    private final String[] aliases;       // strings that map to this stat

    // Reverse lookup map – built in static initializer
    private static final Map<String, StatType> ALIAS_MAP = new HashMap<>();

    static {
        for (StatType stat : values()) {
            for (String alias : stat.aliases) {
                // If there are duplicate aliases, the last one wins (but we don't expect duplicates)
                ALIAS_MAP.put(alias, stat);
            }
        }
    }

    StatType(String displayName, String abbreviation, boolean isPercentage,
             boolean isFlat, double weight, String... aliases) {
        this.displayName = displayName;
        this.abbreviation = abbreviation;
        this.isPercentage = isPercentage;
        this.isFlat = isFlat;
        this.weight = weight;
        this.aliases = aliases;
    }

    public String displayName() { return displayName; }
    public String abbreviation() { return abbreviation; }
    public boolean isPercentage() { return isPercentage; }
    public boolean isFlat() { return isFlat; }
    public double weight() { return weight; }

    /**
     * Parse a string (from gear.txt) into a StatType.
     * Returns null if no match is found.
     */
    public static StatType fromString(String s) {
        if (s == null) return null;
        return ALIAS_MAP.get(s.trim());
    }
}
