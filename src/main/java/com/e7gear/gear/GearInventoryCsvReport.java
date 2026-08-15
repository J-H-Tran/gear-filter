package com.e7gear.gear;

import com.e7gear.config.ConfigLoader;
import com.e7gear.config.FilterConfig;
import com.e7gear.engine.Decision;
import com.e7gear.engine.DecisionEngine;
import com.e7gear.role.Role;
import com.e7gear.role.RoleEvaluation;
import com.e7gear.role.RoleEvaluator;
import com.e7gear.role.RoleScore;
import com.e7gear.scorer.GearScore;
import com.e7gear.scorer.GearScorer;
import com.e7gear.stats.StatType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public final class GearInventoryCsvReport {

    private static final String HEADER = String.join(",",
            "id", "ingameId", "gear", "type", "set", "rank", "level", "enhance",
            "mainStatType", "mainStatValue", "substats",
            "gearScore", "dScore", "sScore", "cScore",
            "maxEnhancementRolls", "totalEnhancementRolls", "hasSpike", "hasModified",
            "bestRole", "usefulStatCount", "slotPreferredStatCount", "mainStatPreferred",
            "quality", "reason"
    );

    public static void main(String[] args) throws Exception {
        // --- Load config ---
        FilterConfig config = ConfigLoader.load();
        GearScorer gearScorer = new GearScorer();
        RoleEvaluator roleEvaluator = new RoleEvaluator(gearScorer, config);
        DecisionEngine decisionEngine = new DecisionEngine(config, gearScorer);

        Path input = args.length > 0 ? Path.of(args[0]) : Path.of("gear.txt");
        Path output = args.length > 1 ? Path.of(args[1]) : Path.of("gear-analysis.csv");

        ObjectMapper mapper = new ObjectMapper();
        GearInventory inventory = mapper.readValue(input.toFile(), GearInventory.class);

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write(HEADER);
            writer.newLine();

            for (Gear gear : inventory.getItems()) {
                GearScore score = gearScorer.score(gear);
                RoleEvaluation roles = roleEvaluator.evaluate(gear);
                Decision decision = decisionEngine.decide(gear, score, roles);

                RoleScore best = roles.bestRole() == Role.NONE
                        ? null
                        : roles.scoreFor(roles.bestRole());

                writer.write(csv(gear.getId()));
                writer.write(','); writer.write(csv(gear.getIngameId()));
                writer.write(','); writer.write(csv(gear.getGear()));
                writer.write(','); writer.write(csv(gear.getType()));
                writer.write(','); writer.write(csv(gear.getSet()));
                writer.write(','); writer.write(csv(gear.getRank()));
                writer.write(','); writer.write(csv(gear.getLevel()));
                writer.write(','); writer.write(csv(gear.getEnhance()));
                writer.write(','); writer.write(csv(gear.getMainStatType()));
                writer.write(','); writer.write(csv(gear.getMainStatValue()));
                writer.write(','); writer.write(csv(formatSubstats(gear.getSubstats())));
                writer.write(','); writer.write(csv(score.score()));
                writer.write(','); writer.write(csv(score.dScore()));
                writer.write(','); writer.write(csv(score.sScore()));
                writer.write(','); writer.write(csv(score.cScore()));
                writer.write(','); writer.write(csv(score.maxEnhancementRolls()));
                writer.write(','); writer.write(csv(score.totalEnhancementRolls()));
                writer.write(','); writer.write(csv(score.hasSpike()));
                writer.write(','); writer.write(csv(score.hasModified()));
                writer.write(','); writer.write(csv(roles.bestRole()));
                writer.write(','); writer.write(csv(best == null ? 0 : best.usefulStatCount()));
                writer.write(','); writer.write(csv(best == null ? 0 : best.slotPreferredStatCount()));
                writer.write(','); writer.write(csv(best != null && best.mainStatPreferred()));
                writer.write(','); writer.write(csv(decision.quality()));
                writer.write(','); writer.write(csv(decision.reason()));
                writer.newLine();
            }
        }

        System.out.println("Inventory: " + inventory.getItems().size());
        System.out.println("+15: " + inventory.getItems().stream().filter(g -> g.getEnhance() == 15).count());
        System.out.println("CSV: " + output.toAbsolutePath());
    }

    // Use abbreviated stat names in CSV for consistency (optional but recommended)
    private static String formatSubstats(List<Substat> substats) {
        if (substats == null || substats.isEmpty()) {
            return "\"\"";
        }

        String formatted = substats.stream()
                .map(s -> {
                    StatType st = StatType.fromString(s.getType());
                    String typeName = st == null ? s.getType() : st.abbreviation();
                    double val = s.getValue();
                    String valStr = (val % 1 == 0) ? String.format("%d", (long) val) : String.format("%.1f", val);
                    return String.format("%s=%s(r%d%s)",
                            typeName,
                            valStr,
                            s.getRolls(),
                            s.isModified() ? "*" : "");
                })
                .collect(Collectors.joining("; "));

        return "\"" + formatted + "\"";
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
