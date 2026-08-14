package com.e7gear;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    private static final int MAX_CAPACITY = 1100;
    private static final double TARGET_PCT = 0.80;

    // Define the strict order for substats: fAtk, fDef, fHP, Atk%, Def%, HP%, SPD, Crit%, CDmg%, Eff%, ER%
    private static final List<String> SUBSTAT_ORDER = Arrays.asList(
            "Attack",                      // fAtk
            "Defense",                     // fDef
            "Health",                      // fHP
            "AttackPercent",               // Atk%
            "DefensePercent",              // Def%
            "HealthPercent",               // HP%
            "Speed",                       // SPD
            "CriticalHitChancePercent",    // Crit%
            "CriticalHitDamagePercent",    // CDmg%
            "EffectivenessPercent",        // Eff%
            "EffectResistancePercent"      // ER%
    );

    public static void main(String[] args) throws Exception {
        String filePath = args.length > 0 ? args[0] : "gear.txt";
        String outputFile = args.length > 1 ? args[1] : "delete_candidates.csv";

        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        System.out.println("[INFO] Loading gear from: " + filePath);
        JsonNode root = mapper.readTree(new File(filePath));
        List<Gear> items = parseItems(root, mapper, filePath);

        GearAnalyzer analyzer = new GearAnalyzer(MAX_CAPACITY, TARGET_PCT);
        List<GearAnalyzer.GearReport> reports = analyzer.analyze(items);

        int currentCount = items.size();
        int targetCount = (int) (MAX_CAPACITY * TARGET_PCT);
        int needToDelete = Math.max(0, currentCount - targetCount);

        System.out.printf("[STATS] Inventory: %d / %d (%.1f%%)%n",
                currentCount, MAX_CAPACITY, currentCount / (double) MAX_CAPACITY * 100);
        System.out.printf("[TARGET] Max: %d items | Need to clear: %d%n", targetCount, needToDelete);

        long lowQualityCount = reports.stream().filter(r -> r.lowQuality).count();
        System.out.printf("[WARN] Low quality +15 pieces (No Spikes): %d%n%n", lowQualityCount);

        writeCsv(outputFile, reports, needToDelete);
        printConsolePreview(reports, needToDelete);
    }

    private static List<Gear> parseItems(JsonNode root, ObjectMapper mapper, String filePath) throws IOException {
        if (root.has("items")) {
            return mapper.readValue(root.get("items").traverse(mapper),
                    mapper.getTypeFactory().constructCollectionType(List.class, Gear.class));
        } else if (root.isArray()) {
            return mapper.readValue(root.traverse(mapper),
                    mapper.getTypeFactory().constructCollectionType(List.class, Gear.class));
        }
        throw new RuntimeException("Unexpected JSON structure in " + filePath);
    }

    // Helper to sort substats based on the defined order
    private static List<Substat> sortSubstats(List<Substat> substats) {
        if (substats == null) return new ArrayList<>();
        return substats.stream()
                .sorted(Comparator.comparingInt(s -> {
                    int index = SUBSTAT_ORDER.indexOf(s.getType());
                    return index == -1 ? Integer.MAX_VALUE : index;
                }))
                .collect(Collectors.toList());
    }

    // Mapping to requested short names for Sets (Community Names)
    private static String shortSetName(String set) {
        if (set == null) return "Unknown";
        String name = set.replace("Set", "");
        switch (name) {
            case "Attack": return "Atk";
            case "Defense": return "Def";
            case "Health": return "HP";
            case "Speed": return "Spd";
            case "Critical": return "Crit";
            case "Destruction": return "Destro";
            case "Hit": return "Hit";
            case "Resist": return "Resist";
            case "Lifesteal": return "Life";
            case "Counter": return "Counter";
            case "Unity": return "Unity";
            case "Immunity": return "Immu";
            case "Rage": return "Rage";
            case "Penetration": return "Pen";
            case "Revenge": return "Revenge";
            case "Injury": return "Injury";
            case "Protection": return "Prot";
            case "Torrent": return "Torrent";
            case "Reversal": return "Reverse";
            case "Riposte": return "Riposte";
            case "Warfare": return "Warfare";
            case "Pursuit": return "Pursuit";
            case "Weakening": return "Weak";
            case "Fervor": return "Fervor";
            default: return name;
        }
    }

    // Mapping to requested short names for Stats
    private static String shortName(String type) {
        if (type == null) return "?";
        switch (type) {
            case "Attack":                      return "fAtk";
            case "Defense":                     return "fDef";
            case "Health":                      return "fHP";
            case "AttackPercent":               return "Atk%";
            case "DefensePercent":              return "Def%";
            case "HealthPercent":               return "HP%";
            case "Speed":                       return "SPD";
            case "CriticalHitChancePercent":    return "Crit%";
            case "CriticalHitDamagePercent":    return "CDmg%";
            case "EffectivenessPercent":        return "Eff%";
            case "EffectResistancePercent":     return "ER%";
            default:                            return type;
        }
    }

    private static String formatSubstat(Substat s, boolean csv) {
        String name = csv ? s.getType() : shortName(s.getType());
        String valStr;

        // Format value: Percent stats get %, others are integers
        if (s.getType() != null && s.getType().endsWith("Percent")) {
            valStr = String.format("%.0f%%", s.getValue());
        } else {
            valStr = String.format("%.0f", s.getValue());
        }

        int boosts = Math.max(0, s.getRolls() - 1);
        String mod = s.isModified() ? "*" : "";
        String sep = csv ? " boosts" : "^";

        return name + ": " + valStr + " (" + boosts + sep + ")" + mod;
    }

    private static void writeCsv(String outputFile, List<GearAnalyzer.GearReport> reports, int needToDelete) throws IOException {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFile)))) {
            writer.println("DELETE,InGameID,Set,Slot,GearScore,MaxBoosts,TotalBoosts,Role,Reason,Substats");
            int lowQualityMarkedCount = 0;

            for (int i = 0; i < reports.size(); i++) {
                GearAnalyzer.GearReport r = reports.get(i);

                String markDelete = "";
                if (r.lowQuality && !r.hasModified) {
                    if (lowQualityMarkedCount < needToDelete) {
                        markDelete = "YES";
                        lowQualityMarkedCount++;
                    } else {
                        markDelete = "OPTIONAL";
                    }
                }

                // Sort substats before formatting
                List<Substat> sortedSubs = sortSubstats(r.gear.getSubstats());

                StringBuilder subs = new StringBuilder();
                for (int j = 0; j < sortedSubs.size(); j++) {
                    if (j > 0) subs.append("; ");
                    subs.append(formatSubstat(sortedSubs.get(j), true));
                }

                writer.printf("%s,%s,%s,%s,%.2f,%d,%d,%s,\"%s\",\"%s\"%n",
                        markDelete, r.gear.getIngameId(), r.gear.getSet(), r.gear.getGear(),
                        r.gearScore, r.maxBoosts, r.totalBoosts, r.bestRole, r.reason, subs.toString());
            }
        }
        System.out.println("[FILE] Report written to: " + outputFile);
    }

    private static void printConsolePreview(List<GearAnalyzer.GearReport> reports, int needToDelete) {
        // Header: TAG, SET, SLOT, SCORE, MAX, ROLE, REASON, SUBSTATS
        System.out.printf("%-6s %-10s %-10s %6s %3s | %-8s | %-22s | %s%n",
                "TAG", "SET", "SLOT", "SCORE", "MAX", "ROLE", "REASON", "SUBSTATS");
        System.out.println(repeatChar('-', 120));

        int delCount = 0;
        int displayedCount = 0;
        // Show until we have found 'needToDelete' DELs, plus maybe 10 extra rows for context
        int targetDisplayRows = needToDelete + 10;

        for (int i = 0; i < reports.size(); i++) {
            // Stop if we've shown enough rows AND found enough deletions
            if (displayedCount >= targetDisplayRows && delCount >= needToDelete) {
                break;
            }

            GearAnalyzer.GearReport r = reports.get(i);

            String tag;
            boolean isDelCandidate = r.lowQuality && !r.hasModified;

            if (r.hasModified) {
                tag = "[SAFE]";
            } else if (isDelCandidate && delCount < needToDelete) {
                tag = "[DEL] ";
                delCount++;
            } else if (isDelCandidate) {
                tag = "[OPT] "; // Optional deletion (trash, but quota filled)
            } else {
                tag = "[REV] "; // Review (Has spikes)
            }

            // Sort substats before formatting
            List<Substat> sortedSubs = sortSubstats(r.gear.getSubstats());

            StringBuilder subs = new StringBuilder();
            for (int j = 0; j < sortedSubs.size(); j++) {
                if (j > 0) subs.append(" | ");
                subs.append(formatSubstat(sortedSubs.get(j), false));
            }

            // Truncate reason for display to fit column
            String shortReason = r.reason.length() > 20 ? r.reason.substring(0, 20) + ".." : r.reason;

            System.out.printf("%-6s %-10s %-10s %6.2f %3d | %-8s | %-22s | %s%n",
                    tag,
                    shortSetName(r.gear.getSet()),
                    r.gear.getGear(),
                    r.gearScore,
                    r.maxBoosts,
                    r.bestRole,
                    shortReason,
                    subs.toString());

            displayedCount++;
        }

        System.out.println(repeatChar('-', 120));
        System.out.printf("[NOTE] Found %d / %d required deletions in the list above.%n", delCount, needToDelete);
        System.out.println("[NOTE] [DEL]=Delete Target | [OPT]=Optional Trash | [REV]=Keep/Review | [SAFE]=Modified");
        System.out.println("[NOTE] Roles: DPS, Support, Tank, Debuffer, None (based on 2026 Guide)");
    }

    private static String repeatChar(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }
}