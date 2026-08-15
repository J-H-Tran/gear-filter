package com.e7gear.gear;

import com.e7gear.stats.StatType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MainStat {
    private String type;
    private double value;

    public MainStat() {}

    public MainStat(String type, double value) {
        this.type = type;
        this.value = value;
    }

    public MainStat(StatType type, double value) {
        this(type != null ? type.displayName() : null, value);
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public StatType statType() { return StatType.fromString(type); }
}
