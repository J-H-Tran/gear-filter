package com.e7gear.gear;

import com.e7gear.stats.StatType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Substat {
    private String type;
    private double value;
    private int rolls;
    @JsonProperty("modified")
    private boolean modified;

    public Substat() {}

    public Substat(String type, double value, int rolls, boolean modified) {
        this.type = type;
        this.value = value;
        this.rolls = rolls;
        this.modified = modified;
    }

    public Substat(String type, double value, int rolls) {
        this(type, value, rolls, false);
    }

    public Substat(StatType statType, double value, int rolls, boolean modified) {
        this(statType != null ? statType.displayName() : null, value, rolls, modified);
    }

    public Substat(StatType statType, double value, int rolls) {
        this(statType, value, rolls, false);
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public int getRolls() { return rolls; }
    public void setRolls(int rolls) { this.rolls = rolls; }

    public boolean isModified() { return modified; }
    public void setModified(boolean modified) { this.modified = modified; }

    public StatType statType() { return StatType.fromString(type); }
}
