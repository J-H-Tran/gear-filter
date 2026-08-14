package com.e7gear.gear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GearInventory {
    private List<Gear> items;

    public List<Gear> getItems() { return items; }
    public void setItems(List<Gear> items) { this.items = items; }
}
