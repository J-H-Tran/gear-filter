package com.e7gear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Gear {
    private long id;
    private String gear;
    private String set;
    private int enhance;
    private List<Substat> substats;
    private Long ingameId;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getGear() { return gear; }
    public void setGear(String gear) { this.gear = gear; }
    public String getSet() { return set; }
    public void setSet(String set) { this.set = set; }
    public int getEnhance() { return enhance; }
    public void setEnhance(int enhance) { this.enhance = enhance; }
    public List<Substat> getSubstats() { return substats; }
    public void setSubstats(List<Substat> substats) { this.substats = substats; }
    public Long getIngameId() { return ingameId; }
    public void setIngameId(Long ingameId) { this.ingameId = ingameId; }
}