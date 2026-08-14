package com.e7gear.gear;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Gear {
    private long id; private String code; private long ct; private long e; private String f; private int g;
    private boolean l; private int level; private String mainStatId; private String mainStatType;
    private double mainStatBaseValue; private double mainStatValue; private long mg; private String p; private String s;
    private double statMultiplier; private double tierMultiplier; private String type; private String gear;
    private String rank; private String set; private String name; private int enhance; private MainStat main;
    private List<Substat> substats; private Long ingameId; private String ingameEquippedId;

    public long getId(){return id;} public void setId(long v){id=v;}
    public String getCode(){return code;} public void setCode(String v){code=v;}
    public long getCt(){return ct;} public void setCt(long v){ct=v;}
    public long getE(){return e;} public void setE(long v){e=v;}
    public String getF(){return f;} public void setF(String v){f=v;}
    public int getG(){return g;} public void setG(int v){g=v;}
    public boolean isL(){return l;} public void setL(boolean v){l=v;}
    public int getLevel(){return level;} public void setLevel(int v){level=v;}
    public String getMainStatId(){return mainStatId;} public void setMainStatId(String v){mainStatId=v;}
    public String getMainStatTypeRaw(){return mainStatType;} public void setMainStatTypeRaw(String v){mainStatType=v;}
    public double getMainStatBaseValue(){return mainStatBaseValue;} public void setMainStatBaseValue(double v){mainStatBaseValue=v;}
    public double getMainStatValueRaw(){return mainStatValue;} public void setMainStatValueRaw(double v){mainStatValue=v;}
    public long getMg(){return mg;} public void setMg(long v){mg=v;}
    public String getP(){return p;} public void setP(String v){p=v;}
    public String getS(){return s;} public void setS(String v){s=v;}
    public double getStatMultiplier(){return statMultiplier;} public void setStatMultiplier(double v){statMultiplier=v;}
    public double getTierMultiplier(){return tierMultiplier;} public void setTierMultiplier(double v){tierMultiplier=v;}
    public String getType(){return type;} public void setType(String v){type=v;}
    public String getGear(){return gear;} public void setGear(String v){gear=v;}
    public String getRank(){return rank;} public void setRank(String v){rank=v;}
    public String getSet(){return set;} public void setSet(String v){set=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public int getEnhance(){return enhance;} public void setEnhance(int v){enhance=v;}
    public MainStat getMain(){return main;} public void setMain(MainStat v){main=v;}
    public List<Substat> getSubstats(){return substats;} public void setSubstats(List<Substat> v){substats=v;}
    public Long getIngameId(){return ingameId;} public void setIngameId(Long v){ingameId=v;}
    public String getIngameEquippedId(){return ingameEquippedId;} public void setIngameEquippedId(String v){ingameEquippedId=v;}
    public String getMainStatType(){return main==null?null:main.getType();}
    public double getMainStatValue(){return main==null?0.0:main.getValue();}
}
