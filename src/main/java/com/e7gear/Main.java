package com.e7gear;

import com.e7gear.engine.Decision;
import com.e7gear.engine.DecisionEngine;
import com.e7gear.gear.Gear;
import com.e7gear.gear.GearInventory;
import com.e7gear.gear.Quality;
import com.e7gear.role.Role;
import com.e7gear.role.RoleEvaluation;
import com.e7gear.role.RoleEvaluator;
import com.e7gear.role.RoleScore;
import com.e7gear.scorer.GearScore;
import com.e7gear.scorer.GearScorer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws IOException {
        Path inputPath = args.length > 0 ? Path.of(args[0]) : Path.of("gear.txt");
        Path outputPath = args.length > 1 ? Path.of(args[1]) : Path.of("gear-analysis.csv");

        System.out.println("E7 Gear Inventory Analysis");
        System.out.println("==========================");

        // 1. Load gear.txt
        String json = Files.readString(inputPath);

        // 2. Deserialize GearInventory
        ObjectMapper mapper = new ObjectMapper();
        GearInventory inventory = mapper.readValue(json, GearInventory.class);
        List<Gear> items = inventory.getItems();

        if (items == null) {
            throw new IllegalStateException("Gear inventory contains no items");
        }

        // 3. GearScorer
        GearScorer gearScorer = new GearScorer();

        // 4. RoleEvaluator
        RoleEvaluator roleEvaluator = new RoleEvaluator();

        // 5. DecisionEngine
        DecisionEngine decisionEngine = new DecisionEngine();

        List<AnalysisResult> results = items.stream()
                .filter(gear -> gear.getEnhance() == 15)
                .map(gear -> {
                    GearScore gearScore = gearScorer.score(gear);
                    RoleEvaluation roleEvaluation = roleEvaluator.evaluate(gear);
                    Decision decision = decisionEngine.decide(
                            gear, gearScore, roleEvaluation);
                    return new AnalysisResult(gear, decision);
                })
                .toList();

        // 6. Print summary
        Map<Quality, Long> qualityCounts = results.stream()
                .collect(Collectors.groupingBy(
                        result -> result.decision().quality(),
                        Collectors.counting()
                ));

        System.out.println("Inventory: " + items.size());
        System.out.println("+15: " + results.size());
        System.out.println("KEEP: " + qualityCounts.getOrDefault(Quality.KEEP, 0L));
        System.out.println("REVIEW: " + qualityCounts.getOrDefault(Quality.REVIEW, 0L));
        System.out.println("DELETE_CANDIDATE: "
                + qualityCounts.getOrDefault(Quality.DELETE_CANDIDATE, 0L));

        // 7. Write CSV report
        writeCsv(outputPath, results);

        System.out.println("CSV: " + outputPath.toAbsolutePath());
    }

    private static void writeCsv(
            Path outputPath,
            List<AnalysisResult> results
    ) throws IOException {
        StringBuilder csv = new StringBuilder();

        csv.append(String.join(",",
                "id",
                "ingameId",
                "gear",
                "type",
                "set",
                "rank",
                "level",
                "enhance",
                "mainStatType",
                "mainStatValue",
                "gearScore",
                "dScore",
                "sScore",
                "cScore",
                "maxEnhancementRolls",
                "totalEnhancementRolls",
                "hasSpike",
                "hasModified",
                "bestRole",
                "usefulStatCount",
                "slotPreferredStatCount",
                "mainStatPreferred",
                "quality",
                "reason"
        )).append('\n');

        for (AnalysisResult result : results) {
            Gear gear = result.gear();
            Decision decision = result.decision();
            RoleEvaluation role = decision.roleEvaluation();
            RoleScore bestRole = role.bestRole() == Role.NONE
                    ? null
                    : role.scoreFor(role.bestRole());
            GearScore score = decision.gearScore();

            csv.append(csvRow(
                    gear.getId(),
                    gear.getIngameId(),
                    gear.getGear(),
                    gear.getType(),
                    gear.getSet(),
                    gear.getRank(),
                    gear.getLevel(),
                    gear.getEnhance(),
                    gear.getMainStatType(),
                    gear.getMainStatValue(),
                    score.score(),
                    score.dScore(),
                    score.sScore(),
                    score.cScore(),
                    score.maxEnhancementRolls(),
                    score.totalEnhancementRolls(),
                    score.hasSpike(),
                    score.hasModified(),
                    role.bestRole(),
                    bestRole == null ? 0 : bestRole.usefulStatCount(),
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
        if (value == null) {
            return "";
        }

        String text = String.valueOf(value);

        if (text.contains(",") || text.contains("\"")
                || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }

        return text;
    }

    private record AnalysisResult(Gear gear, Decision decision) {
    }
}
