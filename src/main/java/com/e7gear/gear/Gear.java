package com.e7gear.gear;

import com.e7gear.stats.StatType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Gear {
    private long id;
    private String ingameId;
    private String gear;
    private String type;
    private String set;
    private String rank;
    private int level;
    private int enhance;
    @JsonProperty("main")
    private MainStat main;
    private List<Substat> substats = new ArrayList<>();
    private boolean reforged;

    public Gear() {}

    public Gear(long id, String ingameId, String gear, String type, String set, String rank,
                int level, int enhance, MainStat main, List<Substat> substats, boolean reforged) {
        this.id = id;
        this.ingameId = ingameId;
        this.gear = gear;
        this.type = type;
        this.set = set;
        this.rank = rank;
        this.level = level;
        this.enhance = enhance;
        this.main = main;
        this.substats = substats != null ? substats : new ArrayList<>();
        this.reforged = reforged;
    }

    public boolean hasStat(StatType targetType) {
        if (substats == null || targetType == null) return false;
        return substats.stream().anyMatch(s -> StatType.fromString(s.getType()) == targetType);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getIngameId() { return ingameId != null ? ingameId : String.valueOf(id); }
    public void setIngameId(String ingameId) { this.ingameId = ingameId; }

    public String getGear() { return gear; }
    public void setGear(String gear) { this.gear = gear; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSet() { return set; }
    public void setSet(String set) { this.set = set; }

    public String getRank() { return rank; }
    public void setRank(String rank) { this.rank = rank; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getGearLevel() { return level; }

    public int getEnhance() { return enhance; }
    public void setEnhance(int enhance) { this.enhance = enhance; }

    public MainStat getMain() { return main; }
    public void setMain(MainStat main) { this.main = main; }

    public String getMainStatType() { return main != null ? main.getType() : null; }
    public double getMainStatValue() { return main != null ? main.getValue() : 0.0; }

    public List<Substat> getSubstats() { return substats; }
    public void setSubstats(List<Substat> substats) { this.substats = substats; }

    public boolean isReforged() { return reforged; }
    public void setReforged(boolean reforged) { this.reforged = reforged; }

    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .ingameId(this.ingameId)
                .gear(this.gear)
                .type(this.type)
                .set(this.set)
                .rank(this.rank)
                .level(this.level)
                .enhance(this.enhance)
                .main(this.main)
                .substats(this.substats != null ? new ArrayList<>(this.substats) : new ArrayList<>())
                .reforged(this.reforged);
    }

    public static class Builder {
        private long id;
        private String ingameId;
        private String gear;
        private String type;
        private String set;
        private String rank;
        private int level;
        private int enhance;
        private MainStat main;
        private List<Substat> substats = new ArrayList<>();
        private boolean reforged;

        public Builder id(long id) { this.id = id; return this; }
        public Builder ingameId(String ingameId) { this.ingameId = ingameId; return this; }
        public Builder gear(String gear) { this.gear = gear; return this; }
        public Builder type(String type) { this.type = type; return this; }
        public Builder set(String set) { this.set = set; return this; }
        public Builder rank(String rank) { this.rank = rank; return this; }
        public Builder level(int level) { this.level = level; return this; }
        public Builder enhance(int enhance) { this.enhance = enhance; return this; }
        public Builder main(MainStat main) { this.main = main; return this; }
        public Builder substats(List<Substat> substats) { this.substats = substats; return this; }
        public Builder reforged(boolean reforged) { this.reforged = reforged; return this; }

        public Gear build() {
            return new Gear(id, ingameId, gear, type, set, rank, level, enhance, main, substats, reforged);
        }
    }
}
