package com.e7gear;

import com.e7gear.app.engine.Decision;
import com.e7gear.app.engine.DecisionEngine;
import com.e7gear.app.role.Role;
import com.e7gear.app.role.RoleEvaluation;
import com.e7gear.app.role.RoleEvaluator;
import com.e7gear.app.role.RoleScore;
import com.e7gear.app.scorer.GearScore;
import com.e7gear.app.scorer.GearScorer;
import com.e7gear.config.ConfigLoader;
import com.e7gear.config.FilterConfig;
import com.e7gear.gear.Gear;
import com.e7gear.gear.GearInventory;
import com.e7gear.gear.Quality;
import com.e7gear.gear.Substat;
import com.e7gear.report.HtmlReportGenerator;
import com.e7gear.stats.StatType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException {
        // --- Load configuration ---
        FilterConfig config = ConfigLoader.load();

        // --- Shared components ---
        GearScorer gearScorer = new GearScorer();
        RoleEvaluator roleEvaluator = new RoleEvaluator(gearScorer, config);
        DecisionEngine decisionEngine = new DecisionEngine(config, gearScorer);

        // --- Prepare output ---
        Path outputDir = Path.of("output");
        if (!Files.exists(outputDir)) Files.createDirectories(outputDir);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        Path inputPath = args.length > 0 ? Path.of(args[0]) : Path.of("gear.txt");
        Path outputPath = args.length > 1 ? Path.of(args[1]) : outputDir.resolve("gear-analysis-" + timestamp + ".csv");

        // Backup config used
        Path configBackup = outputDir.resolve("filter-config-" + timestamp + ".json");
        Files.writeString(configBackup, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(config));

        System.out.println("E7 Gear Inventory Analysis");
        System.out.println("==========================");
        System.out.println("Config: " + configBackup.toAbsolutePath());

        // 1. Load gear.txt
        String json = Files.readString(inputPath);

        // 2. Deserialize GearInventory
        ObjectMapper mapper = new ObjectMapper();
        GearInventory inventory = mapper.readValue(json, GearInventory.class);
        List<Gear> allItems = inventory.getItems();

        if (allItems == null) {
            throw new IllegalStateException("Gear inventory contains no items");
        }

        // Process ALL items
        List<AnalysisResult> allResults = allItems.stream()
                .map(gear -> {
                    GearScore score = gearScorer.score(gear);
                    RoleEvaluation role = roleEvaluator.evaluate(gear);
                    Decision decision;
                    if (gear.getEnhance() == 15) {
                        decision = decisionEngine.decide(gear, score, role);
                    } else {
                        decision = new Decision(Quality.UNENHANCED, "Not +15", score, role);
                    }
                    return new AnalysisResult(gear, decision);
                })
                .toList();

        // Separate +15 for summary stats
        List<AnalysisResult> enhancedResults = allResults.stream()
                .filter(r -> r.gear().getEnhance() == 15)
                .toList();

        // Print summary (only for +15)
        Map<Quality, Long> qualityCounts = enhancedResults.stream()
                .collect(Collectors.groupingBy(
                        r -> r.decision().quality(),
                        Collectors.counting()
                ));

        System.out.println("Total items: " + allItems.size());
        System.out.println("+15 items: " + enhancedResults.size());
        System.out.println("KEEP: " + qualityCounts.getOrDefault(Quality.KEEP, 0L));
        System.out.println("KEEP_MOD_CANDIDATE: " + qualityCounts.getOrDefault(Quality.KEEP_MOD_CANDIDATE, 0L));
        System.out.println("REFORGE_CANDIDATE: " + qualityCounts.getOrDefault(Quality.REFORGE_CANDIDATE, 0L));
        System.out.println("REVIEW: " + qualityCounts.getOrDefault(Quality.REVIEW, 0L));
        System.out.println("DELETE_CANDIDATE: " + qualityCounts.getOrDefault(Quality.DELETE_CANDIDATE, 0L));

        // Write CSV (optional, keep it)
        writeCsv(outputPath, enhancedResults);

        // NEW: Generate HTML report for ALL items
        Path htmlPath = outputDir.resolve("gear-report-" + timestamp + ".html");
        HtmlReportGenerator.generate(htmlPath, allResults);

        System.out.println("HTML report: " + htmlPath.toAbsolutePath());
    }

    // ---------- CSV writing ----------
    private static void writeCsv(Path outputPath, List<AnalysisResult> results) throws IOException {
        StringBuilder csv = new StringBuilder();

        csv.append(String.join(",",
                "ingameId", "gear", "set", "rank", "level", "enhance",
                "mainStatType", "mainStatValue", "substats",
                "gearScore", "dScore", "sScore", "cScore",
                "maxEnhancementRolls", "hasModified",
                "bestRole", "slotPreferredStatCount", "mainStatPreferred",
                "quality", "reason"
        ));

        for (AnalysisResult result : results) {
            Gear gear = result.gear();
            Decision decision = result.decision();
            RoleEvaluation role = decision.roleEvaluation();
            RoleScore bestRole = role.bestRole() == Role.NONE
                    ? null
                    : role.scoreFor(role.bestRole());
            GearScore score = decision.gearScore();

            csv.append(csvRow(
                    gear.getIngameId(),
                    gear.getGear(),
                    gear.getSet(),
                    gear.getRank(),
                    gear.getLevel(),
                    gear.getEnhance(),
                    abbreviateStatType(gear.getMainStatType()),
                    gear.getMainStatValue(),
                    formatSubstats(gear.getSubstats()),
                    score.score(),
                    score.dScore(),
                    score.sScore(),
                    score.cScore(),
                    score.maxEnhancementRolls(),
                    score.hasModified(),
                    role.bestRole(),
                    bestRole == null ? 0 : bestRole.slotPreferredStatCount(),
                    bestRole != null && bestRole.mainStatPreferred(),
                    decision.quality(),
                    decision.reason()
            ));
        }

        Files.writeString(outputPath, csv.toString());
    }

    private static String csvRow(Object... values) {
        return Arrays.stream(values)
                .map(Main::csvEscape)
                .collect(Collectors.joining(",")) + "\n";
    }

    private static String csvEscape(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    // ---------- Stat formatting ----------
    private static String formatSubstats(List<Substat> substats) {
        if (substats == null || substats.isEmpty()) {
            return "";
        }
        return substats.stream()
                .map(s -> {
                    StatType st = StatType.fromString(s.getType());
                    String abbreviatedType = st == null ? s.getType() : st.abbreviation();
                    double val = s.getValue();
                    String formattedVal = (val % 1 == 0) ? String.format("%d", (long) val) : String.format("%.1f", val);
                    return String.format("%s=%s(r%d%s)",
                            abbreviatedType,
                            formattedVal,
                            s.getRolls(),
                            s.isModified() ? "*" : "");
                })
                .collect(Collectors.joining("; "));
    }

    private static String abbreviateStatType(String type) {
        if (type == null) return "";
        StatType st = StatType.fromString(type);
        return st == null ? type : st.abbreviation();
    }

    public record AnalysisResult(Gear gear, Decision decision) {}
}
