package com.e7gear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Substat {
    private String type;
    private double value;
    private int rolls;
    private boolean modified;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }

    public int getRolls() { return rolls; }
    public void setRolls(int rolls) { this.rolls = rolls; }

    public boolean isModified() { return modified; }
    public void setModified(boolean modified) { this.modified = modified; }
}