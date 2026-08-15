package com.e7gear.report;

import com.e7gear.Main.AnalysisResult;
import com.e7gear.app.engine.Decision;
import com.e7gear.gear.Gear;
import com.e7gear.gear.Quality;
import com.e7gear.stats.StatType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class HtmlReportGenerator {

    private static final String TEMPLATE_PATH = "/templates/gear-report-template.html";
    private static final String IMAGE_PATH = "images/";

    // Map slot names to image filenames
    private static final Map<String, String> SLOT_IMAGES = Map.of(
            "Weapon", "weapon-item.png",
            "Helmet", "helmet-item.png",
            "Armor", "armor-item.png",
            "Necklace", "necklace-item.png",
            "Ring", "ring-item.png",
            "Boots", "boot-item.png"
    );

    // Map set names (cleaned) to image filenames
    private static final Map<String, String> SET_IMAGES = Map.ofEntries(
            Map.entry("Attack", "attack-set.png"),
            Map.entry("Critical", "crit-set.png"),
            Map.entry("Speed", "speed-set.png"),
            Map.entry("Counter", "counter-set.png"),
            Map.entry("Defense", "defense-set.png"),
            Map.entry("Destruction", "destruction-set.png"), // match asset list
            Map.entry("Fervor", "fervor-set.png"),
            Map.entry("Health", "health-set.png"),
            Map.entry("Hit", "hit-set.png"),
            Map.entry("Immunity", "immunity-set.png"),
            Map.entry("Injury", "injury-set.png"),
            Map.entry("Lifesteal", "lifesteal-set.png"),
            Map.entry("Penetration", "penetration-set.png"),
            Map.entry("Protection", "protection-set.png"),
            Map.entry("Pursuit", "pursuit-set.png"),
            Map.entry("Rage", "rage-set.png"),
            Map.entry("Resist", "resistance-set.png"),
            Map.entry("Revenge", "revenge-set.png"),
            Map.entry("Reversal", "reversal-set.png"),
            Map.entry("Riposte", "riposte-set.png"),
            Map.entry("Torrent", "torrent-set.png"),
            Map.entry("Unity", "unity-set.png"),
            Map.entry("Warfare", "warfare-set.png"),
            Map.entry("Weakening", "weakening-set.png")
    );

    // Map stat abbreviations to image filenames
    private static final Map<String, String> STAT_IMAGES = Map.ofEntries(
            Map.entry("Atk%", "patk-stat.png"),
            Map.entry("Def%", "pdef-stat.png"),
            Map.entry("Hp%", "php-stat.png"),
            Map.entry("CC%", "critcha-stat.png"),
            Map.entry("CDMG%", "critdmg-stat.png"),
            Map.entry("Eff%", "eff-stat.png"),
            Map.entry("ER%", "effres-stat.png"),
            Map.entry("fAtk", "atk-stat.png"),
            Map.entry("fDef", "fdef-stat.png"),
            Map.entry("fHp", "fhp-stat.png"),
            Map.entry("Spd", "spd-stat.png")
    );

    public static void generate(Path outputPath, List<AnalysisResult> results) throws IOException {
        Path outputDir = outputPath.getParent();
        if (outputDir != null && !Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        String template = loadTemplate();

        List<AnalysisResult> enhanced = results.stream()
                .filter(r -> r.gear().getEnhance() == 15)
                .toList();

        long total = results.size();
        long enhancedCount = enhanced.size();

        Map<Quality, Long> qualityCounts = enhanced.stream()
                .collect(Collectors.groupingBy(
                        r -> r.decision().quality(),
                        Collectors.counting()
                ));

        long keep = qualityCounts.getOrDefault(Quality.KEEP, 0L);
        long mod = qualityCounts.getOrDefault(Quality.KEEP_MOD_CANDIDATE, 0L);
        long reforge = qualityCounts.getOrDefault(Quality.REFORGE_CANDIDATE, 0L);
        long review = qualityCounts.getOrDefault(Quality.REVIEW, 0L);
        long del = qualityCounts.getOrDefault(Quality.DELETE_CANDIDATE, 0L);

        Map<String, Map<Quality, Long>> slotStats = enhanced.stream()
                .collect(Collectors.groupingBy(
                        r -> normalizeSlot(r.gear().getGear()),
                        Collectors.groupingBy(
                                r -> r.decision().quality(),
                                Collectors.counting()
                        )
                ));

        Map<String, Map<Quality, Long>> setStats = enhanced.stream()
                .collect(Collectors.groupingBy(
                        r -> normalizeSet(r.gear().getSet()),
                        Collectors.groupingBy(
                                r -> r.decision().quality(),
                                Collectors.counting()
                        )
                ));

        Map<String, List<AnalysisResult>> deleteBySlot = enhanced.stream()
                .filter(r -> r.decision().quality() == Quality.DELETE_CANDIDATE)
                .collect(Collectors.groupingBy(
                        r -> normalizeSlot(r.gear().getGear()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparingDouble(r -> r.decision().gearScore().score()))
                                        .limit(20)
                                        .toList()
                        )
                ));

        Map<String, List<AnalysisResult>> reforgeBySet = enhanced.stream()
                .filter(r -> r.decision().quality() == Quality.REFORGE_CANDIDATE)
                .collect(Collectors.groupingBy(
                        r -> normalizeSet(r.gear().getSet()),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> list.stream()
                                        .sorted(Comparator.comparingDouble(r -> r.decision().gearScore().score()))
                                        .limit(20)
                                        .toList()
                        )
                ));

        List<String> slotOrder = List.of("Weapon", "Helmet", "Armor", "Necklace", "Ring", "Boots");

        List<AnalysisResult> borderlineReview = enhanced.stream()
                .filter(r -> r.decision().quality() == Quality.REVIEW)
                .sorted(Comparator.comparingDouble(r -> r.decision().gearScore().score()))
                .limit(20)
                .toList();

        List<AnalysisResult> allEnhancedSorted = enhanced.stream()
                .sorted(Comparator.comparingDouble(r -> r.decision().gearScore().score()))
                .toList();

        // Build sections
        String summaryCards = generateSummaryCards(total, enhancedCount, keep, mod, reforge, review, del);
        String qualityLabels = jsonArray(List.of("KEEP", "MOD CANDIDATE", "REFORGE CANDIDATE", "REVIEW", "DELETE CANDIDATE"));
        String qualityData = jsonArrayLong(List.of(keep, mod, reforge, review, del));

        List<String> slotLabels = new ArrayList<>(slotStats.keySet());
        Collections.sort(slotLabels);
        String slotLabelsJson = jsonArray(slotLabels);
        String slotKeep = jsonArray(slotLabels, slotStats, Quality.KEEP);
        String slotMod = jsonArray(slotLabels, slotStats, Quality.KEEP_MOD_CANDIDATE);
        String slotReforge = jsonArray(slotLabels, slotStats, Quality.REFORGE_CANDIDATE);
        String slotReview = jsonArray(slotLabels, slotStats, Quality.REVIEW);
        String slotDelete = jsonArray(slotLabels, slotStats, Quality.DELETE_CANDIDATE);

        List<String> setLabels = setStats.keySet().stream()
                .sorted((a, b) -> Long.compare(
                        setStats.getOrDefault(b, Map.of()).values().stream().mapToLong(Long::longValue).sum(),
                        setStats.getOrDefault(a, Map.of()).values().stream().mapToLong(Long::longValue).sum()
                ))
                .limit(10)
                .toList();
        String setLabelsJson = jsonArray(setLabels);
        String setKeep = jsonArray(setLabels, setStats, Quality.KEEP);
        String setMod = jsonArray(setLabels, setStats, Quality.KEEP_MOD_CANDIDATE);
        String setReforge = jsonArray(setLabels, setStats, Quality.REFORGE_CANDIDATE);
        String setReview = jsonArray(setLabels, setStats, Quality.REVIEW);
        String setDelete = jsonArray(setLabels, setStats, Quality.DELETE_CANDIDATE);

        String deleteSections = generateDeleteSections(deleteBySlot, slotOrder);
        String reforgeSections = generateReforgeSections(reforgeBySet);
        String reviewTable = generateReviewTable(borderlineReview);
        String allTable = generateAllTable(allEnhancedSorted);

        String finalHtml = template
                .replace("{{TITLE}}", "E7 Gear Filter Report")
                .replace("{{TIMESTAMP}}", new Date().toString())
                .replace("{{SUMMARY_CARDS}}", summaryCards)
                .replace("{{QUALITY_LABELS}}", qualityLabels)
                .replace("{{QUALITY_DATA}}", qualityData)
                .replace("{{SLOT_LABELS}}", slotLabelsJson)
                .replace("{{SLOT_KEEP}}", slotKeep)
                .replace("{{SLOT_MOD}}", slotMod)
                .replace("{{SLOT_REFORGE}}", slotReforge)
                .replace("{{SLOT_REVIEW}}", slotReview)
                .replace("{{SLOT_DELETE}}", slotDelete)
                .replace("{{SET_LABELS}}", setLabelsJson)
                .replace("{{SET_KEEP}}", setKeep)
                .replace("{{SET_MOD}}", setMod)
                .replace("{{SET_REFORGE}}", setReforge)
                .replace("{{SET_REVIEW}}", setReview)
                .replace("{{SET_DELETE}}", setDelete)
                .replace("{{DELETE_SECTIONS}}", deleteSections)
                .replace("{{REFORGE_SECTIONS}}", reforgeSections)
                .replace("{{REVIEW_TABLE}}", reviewTable)
                .replace("{{ALL_TABLE}}", allTable);

        Files.writeString(outputPath, finalHtml);
    }

    private static String loadTemplate() throws IOException {
        URL resource = HtmlReportGenerator.class.getResource(TEMPLATE_PATH);
        if (resource == null) {
            throw new IOException("Template not found: " + TEMPLATE_PATH);
        }
        try (InputStream is = resource.openStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ----- Section generators (unchanged) -----

    private static String generateSummaryCards(long total, long enhancedCount, long keep, long mod, long reforge, long review, long del) {
        return String.format("""
                <div class="summary">
                  <div class="card"><div class="label">Total Items</div><div class="value">%d</div></div>
                  <div class="card"><div class="label">+15 Items</div><div class="value">%d</div></div>
                  <div class="card"><div class="label">KEEP</div><div class="value keep">%d</div></div>
                  <div class="card"><div class="label">MOD CANDIDATE</div><div class="value mod">%d</div></div>
                  <div class="card"><div class="label">REFORGE CANDIDATE</div><div class="value reforge">%d</div></div>
                  <div class="card"><div class="label">REVIEW</div><div class="value review">%d</div></div>
                  <div class="card"><div class="label">DELETE CANDIDATE</div><div class="value delete">%d</div></div>
                  <div class="card"><div class="label">UNENHANCED</div><div class="value unenhanced">%d</div></div>
                </div>
                """, total, enhancedCount, keep, mod, reforge, review, del, total - enhancedCount);
    }

    private static String generateDeleteSections(Map<String, List<AnalysisResult>> deleteBySlot, List<String> slotOrder) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>🗑️ Top DELETE_CANDIDATE by Slot (Lowest Score)</h2>");
        boolean hasDeletes = false;
        for (String slot : slotOrder) {
            List<AnalysisResult> slotDeletes = deleteBySlot.getOrDefault(slot, List.of());
            if (slotDeletes.isEmpty()) continue;
            hasDeletes = true;
            sb.append("<h3>").append(slotIcon(slot)).append("</h3>");
            sb.append("<div class=\"table-wrap\">");
            sb.append("<table><thead><tr><th>Set</th><th>Main</th><th>Substats</th><th>Score</th><th>Reason</th></tr></thead><tbody>");
            for (AnalysisResult r : slotDeletes) {
                sb.append(rowHtmlWithoutSlot(r));
            }
            sb.append("</tbody></table></div>");
        }
        if (!hasDeletes) {
            sb.append("<p class=\"no-data\">No DELETE_CANDIDATE items found.</p>");
        }
        return sb.toString();
    }

    private static String generateReforgeSections(Map<String, List<AnalysisResult>> reforgeBySet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>🔧 Top REFORGE_CANDIDATE by Set (Lowest Current Score, Reforge makes them viable)</h2>");
        boolean hasReforges = false;
        List<String> setOrder = reforgeBySet.keySet().stream().sorted().toList();
        for (String set : setOrder) {
            List<AnalysisResult> setReforges = reforgeBySet.get(set);
            if (setReforges.isEmpty()) continue;
            hasReforges = true;
            sb.append("<h3>").append(setIcon(set)).append("</h3>");
            sb.append("<div class=\"table-wrap\">");
            sb.append("<table><thead><tr><th>Slot</th><th>Main</th><th>Substats</th><th>Current Score</th><th>Reason</th></tr></thead><tbody>");
            for (AnalysisResult r : setReforges) {
                sb.append(rowHtmlForReforge(r));
            }
            sb.append("</tbody></table></div>");
        }
        if (!hasReforges) {
            sb.append("<p class=\"no-data\">No REFORGE_CANDIDATE items found.</p>");
        }
        return sb.toString();
    }

    private static String generateReviewTable(List<AnalysisResult> borderlineReview) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>📋 Borderline REVIEW (Lowest Score, at risk of deletion)</h2>");
        sb.append("<div class=\"table-wrap\">");
        sb.append("<table><thead><tr><th>Slot</th><th>Set</th><th>Main</th><th>Substats</th><th>Score</th><th>Reason</th></tr></thead><tbody>");
        if (borderlineReview.isEmpty()) {
            sb.append("<tr><td colspan=\"6\" style=\"text-align:center;color:#8b949e;\">No REVIEW items found.</td></tr>");
        } else {
            for (AnalysisResult r : borderlineReview) {
                sb.append(rowHtml(r));
            }
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String generateAllTable(List<AnalysisResult> allEnhancedSorted) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>📊 All +15 Items (sorted by Score ascending)</h2>");
        sb.append("<div class=\"table-wrap\" style=\"max-height:600px;\">");
        sb.append("<table><thead><tr><th>Slot</th><th>Set</th><th>Main</th><th>Substats</th><th>Score</th><th>Quality</th><th>Reason</th></tr></thead><tbody>");
        for (AnalysisResult r : allEnhancedSorted) {
            sb.append(rowHtml(r));
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    // ----- Row helpers (with fallback images) -----

    private static String rowHtml(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        return "      <tr>" +
                "<td>" + slotIcon(g.getGear()) + "</td>" +
                "<td>" + setIcon(g.getSet()) + "</td>" +
                "<td>" + formatMainStat(g) + "</td>" +
                "<td style=\"font-size:12px;\">" + formatSubstatsWithIcons(g) + "</td>" +
                "<td>" + String.format("%.1f", d.gearScore().score()) + "</td>" +
                "<td><span class=\"badge " + qualityClass(d.quality()) + "\">" + d.quality() + "</span></td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
    }

    private static String rowHtmlWithoutSlot(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        return "      <tr>" +
                "<td>" + setIcon(g.getSet()) + "</td>" +
                "<td>" + formatMainStat(g) + "</td>" +
                "<td style=\"font-size:12px;\">" + formatSubstatsWithIcons(g) + "</td>" +
                "<td>" + String.format("%.1f", d.gearScore().score()) + "</td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
    }

    private static String rowHtmlForReforge(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        return "      <tr>" +
                "<td>" + slotIcon(g.getGear()) + "</td>" +
                "<td>" + formatMainStat(g) + "</td>" +
                "<td style=\"font-size:12px;\">" + formatSubstatsWithIcons(g) + "</td>" +
                "<td>" + String.format("%.1f", d.gearScore().score()) + "</td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
    }

    // ----- Formatting helpers with fallback -----

    private static String slotIcon(String slotName) {
        String normalized = normalizeSlot(slotName);
        String filename = SLOT_IMAGES.get(normalized);
        if (filename == null) return normalized;
        return iconHtml(IMAGE_PATH + filename, normalized, 24);
    }

    private static String setIcon(String setRaw) {
        String normalized = normalizeSet(setRaw);
        String filename = SET_IMAGES.get(normalized);
        if (filename == null) return normalized;
        return iconHtml(IMAGE_PATH + filename, normalized, 24);
    }

    private static String statIcon(String abbr) {
        String filename = STAT_IMAGES.get(abbr);
        if (filename == null) return abbr;
        return iconHtml(IMAGE_PATH + filename, abbr, 18);
    }

    private static String iconHtml(String src, String alt, int size) {
        // Show image, but if it fails, display the alt text.
        return "<span style=\"display:inline-flex;align-items:center;gap:2px;\">" +
                "<img src=\"" + src + "\" alt=\"" + alt + "\" style=\"width:" + size + "px;height:" + size + "px;vertical-align:middle;\" onerror=\"this.style.display='none';\">" +
                "</span>";
    }

    private static String formatMainStat(Gear g) {
        String type = g.getMainStatType();
        double value = g.getMainStatValue();
        if (type == null) return "";
        String abbr = abbreviateStat(type);
        String valStr = (value % 1 == 0) ? String.format("%d", (long) value) : String.format("%.1f", value);
        return statIcon(abbr) + " " + valStr;
    }

    private static String formatSubstatsWithIcons(Gear g) {
        if (g.getSubstats() == null || g.getSubstats().isEmpty()) return "";
        return g.getSubstats().stream()
                .map(s -> {
                    StatType stat = StatType.fromString(s.getType());
                    String typeAbbr = stat != null ? stat.abbreviation() : s.getType();
                    String val = s.getValue() % 1 == 0 ? String.format("%d", (long) s.getValue()) : String.format("%.1f", s.getValue());
                    String rolls = s.getRolls() + (s.isModified() ? "★" : "");
                    return statIcon(typeAbbr) + " " + val + "(" + rolls + ")";
                })
                .collect(Collectors.joining(" "));
    }

    // ----- Normalization helpers (unchanged) -----

    private static String normalizeSlot(String slot) {
        if (slot == null) return "Unknown";
        return switch (slot.toLowerCase()) {
            case "neck", "necklace" -> "Necklace";
            case "ring" -> "Ring";
            case "boot", "boots" -> "Boots";
            case "weapon" -> "Weapon";
            case "helmet", "helm" -> "Helmet";
            case "armor" -> "Armor";
            default -> slot;
        };
    }

    private static String normalizeSet(String setRaw) {
        if (setRaw == null) return "Unknown";
        String cleaned = setRaw.replace("Set", "");
        return switch (cleaned) {
            case "Resist" -> "Resist";
            case "Counter" -> "Counter";
            case "Penetration" -> "Penetration";
            case "Torrent" -> "Torrent";
            case "Revenge" -> "Revenge";
            case "Riposte" -> "Riposte";
            case "Warfare" -> "Warfare";
            case "Weakening" -> "Weakening";
            case "Fervor" -> "Fervor";
            case "Pursuit" -> "Pursuit";
            case "Protection" -> "Protection";
            case "Immunity" -> "Immunity";
            case "Injury" -> "Injury";
            case "Unity" -> "Unity";
            case "Rage" -> "Rage";
            case "Reversal" -> "Reversal";
            default -> cleaned;
        };
    }

    private static String abbreviateStat(String type) {
        if (type == null) return "";
        StatType st = StatType.fromString(type);
        return st != null ? st.abbreviation() : type;
    }

    private static String qualityClass(Quality q) {
        return switch (q) {
            case KEEP -> "keep";
            case KEEP_MOD_CANDIDATE -> "mod";
            case REFORGE_CANDIDATE -> "reforge";
            case REVIEW -> "review";
            case DELETE_CANDIDATE -> "delete";
            case UNENHANCED -> "unenhanced";
        };
    }

    // ----- JSON helpers (unchanged) -----

    private static String jsonArray(List<String> strings) {
        return strings.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static String jsonArrayLong(List<Long> numbers) {
        return numbers.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private static String jsonArray(List<String> labels, Map<String, Map<Quality, Long>> stats, Quality quality) {
        List<Long> values = labels.stream()
                .map(label -> stats.getOrDefault(label, Map.of()).getOrDefault(quality, 0L))
                .toList();
        return jsonArrayLong(values);
    }
}
