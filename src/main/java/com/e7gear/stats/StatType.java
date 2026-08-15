package com.e7gear.stats;

import java.util.HashMap;
import java.util.Map;

public enum StatType {

    ATTACK_PERCENT("AttackPercent", "Atk%", true, false, 1.0, 8.0, "att_rate", "AttackPercent", "atk%"),
    DEFENSE_PERCENT("DefensePercent", "Def%", true, false, 1.0, 8.0, "def_rate", "DefensePercent", "def%"),
    HEALTH_PERCENT("HealthPercent", "Hp%", true, false, 1.0, 8.0, "max_hp_rate", "HealthPercent", "hp%"),
    CRIT_CHANCE("CriticalHitChancePercent", "CC%", true, false, 1.6, 5.0, "cri", "CriticalHitChance", "CriticalHitChancePercent", "cc%"),
    CRIT_DAMAGE("CriticalHitDamagePercent", "CDMG%", true, false, 1.142857, 7.0, "cri_dmg", "CriticalHitDamage", "CriticalHitDamagePercent", "cdmg%"),
    EFFECTIVENESS("EffectivenessPercent", "Eff%", true, false, 1.0, 8.0, "acc", "Effectiveness", "EffectivenessPercent", "eff%"),
    EFFECT_RESISTANCE("EffectResistancePercent", "ER%", true, false, 1.0, 8.0, "res", "EffectResistance", "EffectResistancePercent", "er%"),
    SPEED("Speed", "Spd", false, false, 2.0, 4.0, "speed", "Speed", "spd"),
    FLAT_ATTACK("Attack", "fAtk", false, true, 3.75 / 39.0, 39.0, "att", "Attack", "fatk"),
    FLAT_DEFENSE("Defense", "fDef", false, true, 3.75 / 31.0, 31.0, "def", "Defense", "fdef"),
    FLAT_HEALTH("Health", "fHp", false, true, 3.75 / 174.0, 174.0, "max_hp", "Health", "fhp");

    private final String displayName;
    private final String abbreviation;
    private final boolean percentage;
    private final boolean flat;
    private final double weight;
    private final double maxModValue;
    private final String[] aliases;

    StatType(String displayName, String abbreviation, boolean percentage, boolean flat,
             double weight, double maxModValue, String... aliases) {
        this.displayName = displayName;
        this.abbreviation = abbreviation;
        this.percentage = percentage;
        this.flat = flat;
        this.weight = weight;
        this.maxModValue = maxModValue;
        this.aliases = aliases;
    }

    public String displayName() { return displayName; }
    public String getDisplayName() { return displayName; }

    public String abbreviation() { return abbreviation; }
    public String getAbbreviation() { return abbreviation; }

    public boolean isPercentage() { return percentage; }
    public boolean isFlat() { return flat; }

    public double weight() { return weight; }
    public double getWeight() { return weight; }

    public double getMaxModValue() { return maxModValue; }

    private static final Map<String, StatType> LOOKUP = new HashMap<>();

    static {
        for (StatType st : values()) {
            LOOKUP.put(st.name().toLowerCase(), st);
            LOOKUP.put(st.displayName.toLowerCase(), st);
            LOOKUP.put(st.abbreviation.toLowerCase(), st);
            for (String alias : st.aliases) {
                LOOKUP.put(alias.toLowerCase(), st);
            }
        }
    }

    public static StatType fromString(String raw) {
        if (raw == null) return null;
        return LOOKUP.get(raw.trim().toLowerCase());
    }
}
