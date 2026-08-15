package com.e7gear.report;

import com.e7gear.Main.AnalysisResult;
import com.e7gear.app.engine.Decision;
import com.e7gear.gear.Gear;
import com.e7gear.gear.Quality;
import com.e7gear.stats.StatType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class HtmlReportGenerator {

    public static void generate(Path outputPath, List<AnalysisResult> results) throws IOException {
        // Separate +15 items for decision stats
        List<AnalysisResult> enhanced = results.stream()
                .filter(r -> r.gear().getEnhance() == 15)
                .toList();

        long total = results.size();
        long enhancedCount = enhanced.size();

        // Quality counts (only +15)
        Map<Quality, Long> qualityCounts = enhanced.stream()
                .collect(Collectors.groupingBy(
                        r -> r.decision().quality(),
                        Collectors.counting()
                ));

        long keep = qualityCounts.getOrDefault(Quality.KEEP, 0L);
        long mod = qualityCounts.getOrDefault(Quality.KEEP_MOD_CANDIDATE, 0L);
        long review = qualityCounts.getOrDefault(Quality.REVIEW, 0L);
        long del = qualityCounts.getOrDefault(Quality.DELETE_CANDIDATE, 0L);

        // Slot breakdown (for +15, by quality)
        Map<String, Map<Quality, Long>> slotStats = enhanced.stream()
                .collect(Collectors.groupingBy(
                        r -> normalizeSlot(r.gear().getGear()),
                        Collectors.groupingBy(
                                r -> r.decision().quality(),
                                Collectors.counting()
                        )
                ));

        // Set breakdown (for +15, by quality)
        Map<String, Map<Quality, Long>> setStats = enhanced.stream()
                .collect(Collectors.groupingBy(
                        r -> r.gear().getSet() != null ? r.gear().getSet().replace("Set", "") : "Unknown",
                        Collectors.groupingBy(
                                r -> r.decision().quality(),
                                Collectors.counting()
                        )
                ));

        // ---- DELETE grouped by slot ----
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

        List<String> slotOrder = List.of("Weapon", "Helmet", "Armor", "Necklace", "Ring", "Boots");

        // Borderline REVIEW (global, lowest score)
        List<AnalysisResult> borderlineReview = enhanced.stream()
                .filter(r -> r.decision().quality() == Quality.REVIEW)
                .sorted(Comparator.comparingDouble(r -> r.decision().gearScore().score()))
                .limit(20)
                .toList();

        // All +15 items for the main table
        List<AnalysisResult> allEnhancedSorted = enhanced.stream()
                .sorted(Comparator.comparingDouble(r -> r.decision().gearScore().score()))
                .toList();

        // Build HTML
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("  <title>E7 Gear Filter Report</title>\n");
        html.append("  <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n");
        html.append("  <style>\n");
        html.append("    body { background: #0d1117; color: #c9d1d9; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 20px; }\n");
        html.append("    .container { max-width: 1400px; margin: 0 auto; }\n");
        html.append("    h1, h2, h3 { color: #f0f6fc; }\n");
        html.append("    h3 { margin-top: 24px; margin-bottom: 8px; }\n");
        html.append("    .summary { display: flex; flex-wrap: wrap; gap: 16px; margin-bottom: 24px; }\n");
        html.append("    .card { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 16px 24px; min-width: 120px; flex: 1 0 auto; }\n");
        html.append("    .card .label { font-size: 14px; color: #8b949e; }\n");
        html.append("    .card .value { font-size: 28px; font-weight: 600; }\n");
        html.append("    .card .value.keep { color: #2ea043; }\n");
        html.append("    .card .value.mod { color: #d29922; }\n");
        html.append("    .card .value.review { color: #f0883e; }\n");
        html.append("    .card .value.delete { color: #f85149; }\n");
        html.append("    .card .value.unenhanced { color: #8b949e; }\n");
        html.append("    .chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 30px; }\n");
        html.append("    .chart-box { background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 16px; }\n");
        html.append("    .chart-box h3 { margin-top: 0; }\n");
        html.append("    .full-width { grid-column: 1 / -1; }\n");
        html.append("    table { width: 100%; border-collapse: collapse; margin-top: 8px; }\n");
        html.append("    th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #30363d; font-size: 13px; }\n");
        html.append("    th { background: #161b22; color: #8b949e; font-weight: 600; position: sticky; top: 0; }\n");
        html.append("    tr:hover { background: #1c2128; }\n");
        html.append("    .badge { display: inline-block; padding: 2px 10px; border-radius: 12px; font-size: 11px; font-weight: 500; }\n");
        html.append("    .badge.keep { background: #2ea04333; color: #2ea043; border: 1px solid #2ea04366; }\n");
        html.append("    .badge.mod { background: #d2992233; color: #d29922; border: 1px solid #d2992266; }\n");
        html.append("    .badge.review { background: #f0883e33; color: #f0883e; border: 1px solid #f0883e66; }\n");
        html.append("    .badge.delete { background: #f8514933; color: #f85149; border: 1px solid #f8514966; }\n");
        html.append("    .badge.unenhanced { background: #8b949e33; color: #8b949e; border: 1px solid #8b949e66; }\n");
        html.append("    .table-wrap { max-height: 400px; overflow-y: auto; margin-bottom: 16px; }\n");
        html.append("    @media (max-width: 800px) { .chart-grid { grid-template-columns: 1fr; } }\n");
        html.append("  </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("<div class=\"container\">\n");

        // ---- Header ----
        html.append("  <h1>⚔️ Epic Seven Gear Report</h1>\n");
        html.append("  <p style=\"color:#8b949e;\">Generated: ").append(new Date()).append("</p>\n");

        // ---- Summary Cards ----
        html.append("  <div class=\"summary\">\n");
        html.append("    <div class=\"card\"><div class=\"label\">Total Items</div><div class=\"value\">").append(total).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"label\">+15 Items</div><div class=\"value\">").append(enhancedCount).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"label\">KEEP</div><div class=\"value keep\">").append(keep).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"label\">MOD CANDIDATE</div><div class=\"value mod\">").append(mod).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"label\">REVIEW</div><div class=\"value review\">").append(review).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"label\">DELETE CANDIDATE</div><div class=\"value delete\">").append(del).append("</div></div>\n");
        html.append("    <div class=\"card\"><div class=\"label\">UNENHANCED</div><div class=\"value unenhanced\">").append(total - enhancedCount).append("</div></div>\n");
        html.append("  </div>\n");

        // ---- Charts ----
        html.append("  <div class=\"chart-grid\">\n");
        html.append("    <div class=\"chart-box\">\n");
        html.append("      <h3>Quality Distribution (+15)</h3>\n");
        html.append("      <canvas id=\"qualityChart\" width=\"400\" height=\"300\"></canvas>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"chart-box\">\n");
        html.append("      <h3>Quality by Slot (+15)</h3>\n");
        html.append("      <canvas id=\"slotChart\" width=\"400\" height=\"300\"></canvas>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"chart-box full-width\">\n");
        html.append("      <h3>Quality by Set (+15)</h3>\n");
        html.append("      <canvas id=\"setChart\" width=\"800\" height=\"300\"></canvas>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");

        // ---- DELETE tables grouped by slot ----
        html.append("  <h2>🗑️ Top DELETE_CANDIDATE by Slot (Lowest Score)</h2>\n");
        boolean hasDeletes = false;
        for (String slot : slotOrder) {
            List<AnalysisResult> slotDeletes = deleteBySlot.getOrDefault(slot, List.of());
            if (slotDeletes.isEmpty()) continue;
            hasDeletes = true;
            html.append("  <h3>⚔️ ").append(slot).append("</h3>\n");
            html.append("  <div class=\"table-wrap\">\n");
            html.append("    <table>\n");
            html.append("      <thead><tr><th>Set</th><th>Main</th><th>Substats</th><th>Score</th><th>Reason</th></tr></thead>\n");
            html.append("      <tbody>\n");
            for (AnalysisResult r : slotDeletes) {
                html.append(rowHtmlWithoutSlot(r));
            }
            html.append("      </tbody>\n");
            html.append("    </table>\n");
            html.append("  </div>\n");
        }
        if (!hasDeletes) {
            html.append("  <p style=\"color:#8b949e;\">No DELETE_CANDIDATE items found.</p>\n");
        }

        // ---- Borderline REVIEW ----
        html.append("  <h2>📋 Borderline REVIEW (Lowest Score, at risk of deletion)</h2>\n");
        html.append("  <div class=\"table-wrap\">\n");
        html.append("    <table>\n");
        html.append("      <thead><tr><th>Slot</th><th>Set</th><th>Main</th><th>Substats</th><th>Score</th><th>Reason</th></tr></thead>\n");
        html.append("      <tbody>\n");
        for (AnalysisResult r : borderlineReview) {
            html.append(rowHtml(r));
        }
        if (borderlineReview.isEmpty()) {
            html.append("      <tr><td colspan=\"6\" style=\"text-align:center;color:#8b949e;\">No REVIEW items found.</td></tr>\n");
        }
        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </div>\n");

        // ---- All +15 items ----
        html.append("  <h2>📊 All +15 Items (sorted by Score ascending)</h2>\n");
        html.append("  <div class=\"table-wrap\" style=\"max-height:600px;\">\n");
        html.append("    <table>\n");
        html.append("      <thead><tr><th>Slot</th><th>Set</th><th>Main</th><th>Substats</th><th>Score</th><th>Quality</th><th>Reason</th></tr></thead>\n");
        html.append("      <tbody>\n");
        for (AnalysisResult r : allEnhancedSorted) {
            html.append(rowHtml(r));
        }
        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </div>\n");

        html.append("</div>\n");

        // ---- JavaScript for Charts ----
        html.append("<script>\n");
        // Quality chart
        html.append("const qualityCtx = document.getElementById('qualityChart').getContext('2d');\n");
        html.append("new Chart(qualityCtx, {\n");
        html.append("  type: 'doughnut',\n");
        html.append("  data: {\n");
        html.append("    labels: ['KEEP', 'MOD CANDIDATE', 'REVIEW', 'DELETE CANDIDATE'],\n");
        html.append("    datasets: [{\n");
        html.append("      data: [").append(keep).append(", ").append(mod).append(", ").append(review).append(", ").append(del).append("],\n");
        html.append("      backgroundColor: ['#2ea043', '#d29922', '#f0883e', '#f85149'],\n");
        html.append("      borderColor: '#0d1117',\n");
        html.append("      borderWidth: 2\n");
        html.append("    }]\n");
        html.append("  },\n");
        html.append("  options: { responsive: true, plugins: { legend: { position: 'bottom', labels: { color: '#c9d1d9' } } } }\n");
        html.append("});\n");

        // Slot chart
        List<String> slotLabels = new ArrayList<>(slotStats.keySet());
        Collections.sort(slotLabels);
        html.append("const slotCtx = document.getElementById('slotChart').getContext('2d');\n");
        html.append("new Chart(slotCtx, {\n");
        html.append("  type: 'bar',\n");
        html.append("  data: {\n");
        html.append("    labels: ").append(jsonArray(slotLabels)).append(",\n");
        html.append("    datasets: [\n");
        html.append("      { label: 'KEEP', data: ").append(jsonArray(slotLabels, slotStats, Quality.KEEP)).append(", backgroundColor: '#2ea043' },\n");
        html.append("      { label: 'MOD CANDIDATE', data: ").append(jsonArray(slotLabels, slotStats, Quality.KEEP_MOD_CANDIDATE)).append(", backgroundColor: '#d29922' },\n");
        html.append("      { label: 'REVIEW', data: ").append(jsonArray(slotLabels, slotStats, Quality.REVIEW)).append(", backgroundColor: '#f0883e' },\n");
        html.append("      { label: 'DELETE CANDIDATE', data: ").append(jsonArray(slotLabels, slotStats, Quality.DELETE_CANDIDATE)).append(", backgroundColor: '#f85149' }\n");
        html.append("    ]\n");
        html.append("  },\n");
        html.append("  options: { responsive: true, plugins: { legend: { position: 'bottom', labels: { color: '#c9d1d9' } } }, scales: { x: { ticks: { color: '#8b949e' } }, y: { ticks: { color: '#8b949e', stepSize: 1 } } } }\n");
        html.append("});\n");

        // Set chart
        List<String> setLabels = setStats.keySet().stream()
                .sorted((a, b) -> Long.compare(
                        setStats.getOrDefault(b, Map.of()).values().stream().mapToLong(Long::longValue).sum(),
                        setStats.getOrDefault(a, Map.of()).values().stream().mapToLong(Long::longValue).sum()
                ))
                .limit(10)
                .toList();
        html.append("const setCtx = document.getElementById('setChart').getContext('2d');\n");
        html.append("new Chart(setCtx, {\n");
        html.append("  type: 'bar',\n");
        html.append("  data: {\n");
        html.append("    labels: ").append(jsonArray(setLabels)).append(",\n");
        html.append("    datasets: [\n");
        html.append("      { label: 'KEEP', data: ").append(jsonArray(setLabels, setStats, Quality.KEEP)).append(", backgroundColor: '#2ea043' },\n");
        html.append("      { label: 'MOD CANDIDATE', data: ").append(jsonArray(setLabels, setStats, Quality.KEEP_MOD_CANDIDATE)).append(", backgroundColor: '#d29922' },\n");
        html.append("      { label: 'REVIEW', data: ").append(jsonArray(setLabels, setStats, Quality.REVIEW)).append(", backgroundColor: '#f0883e' },\n");
        html.append("      { label: 'DELETE CANDIDATE', data: ").append(jsonArray(setLabels, setStats, Quality.DELETE_CANDIDATE)).append(", backgroundColor: '#f85149' }\n");
        html.append("    ]\n");
        html.append("  },\n");
        html.append("  options: { responsive: true, plugins: { legend: { position: 'bottom', labels: { color: '#c9d1d9' } } }, scales: { x: { ticks: { color: '#8b949e' } }, y: { ticks: { color: '#8b949e', stepSize: 1 } } } }\n");
        html.append("});\n");
        html.append("</script>\n");
        html.append("</body>\n");
        html.append("</html>");

        Files.writeString(outputPath, html.toString());
    }

    // Helper to build a JSON array from a list of keys and a map of maps
    private static String jsonArray(List<String> labels, Map<String, Map<Quality, Long>> stats, Quality quality) {
        List<Long> values = labels.stream()
                .map(label -> stats.getOrDefault(label, Map.of()).getOrDefault(quality, 0L))
                .toList();
        return values.toString();
    }

    private static String jsonArray(List<String> labels) {
        return labels.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(",", "[", "]"));
    }

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

    // Row with Slot column (used for REVIEW and ALL tables)
    private static String rowHtml(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        return "      <tr>" +
                "<td>" + normalizeSlot(g.getGear()) + "</td>" +
                "<td>" + (g.getSet() != null ? g.getSet().replace("Set", "") : "") + "</td>" +
                "<td>" + (g.getMainStatType() != null ? abbreviateStat(g.getMainStatType()) : "") + " " + (g.getMainStatValue() != 0 ? g.getMainStatValue() : "") + "</td>" +
                "<td style=\"font-size:12px;\">" + formatSubstatsShort(g) + "</td>" +
                "<td>" + String.format("%.1f", d.gearScore().score()) + "</td>" +
                "<td><span class=\"badge " + qualityClass(d.quality()) + "\">" + d.quality() + "</span></td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
    }

    // Row WITHOUT Slot column (used for slot-grouped DELETE tables)
    private static String rowHtmlWithoutSlot(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        return "      <tr>" +
                "<td>" + (g.getSet() != null ? g.getSet().replace("Set", "") : "") + "</td>" +
                "<td>" + (g.getMainStatType() != null ? abbreviateStat(g.getMainStatType()) : "") + " " + (g.getMainStatValue() != 0 ? g.getMainStatValue() : "") + "</td>" +
                "<td style=\"font-size:12px;\">" + formatSubstatsShort(g) + "</td>" +
                "<td>" + String.format("%.1f", d.gearScore().score()) + "</td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
    }

    private static String qualityClass(Quality q) {
        return switch (q) {
            case KEEP -> "keep";
            case KEEP_MOD_CANDIDATE -> "mod";
            case REVIEW -> "review";
            case DELETE_CANDIDATE -> "delete";
            case UNENHANCED -> "unenhanced";
        };
    }

    private static String abbreviateStat(String type) {
        if (type == null) return "";
        StatType st = StatType.fromString(type);
        return st != null ? st.abbreviation() : type;
    }

    private static String formatSubstatsShort(Gear g) {
        if (g.getSubstats() == null || g.getSubstats().isEmpty()) return "";
        return g.getSubstats().stream()
                .map(s -> {
                    StatType stat = StatType.fromString(s.getType());
                    String typeAbbr = stat != null ? stat.abbreviation() : s.getType();
                    String val = s.getValue() % 1 == 0 ? String.format("%d", (long) s.getValue()) : String.format("%.1f", s.getValue());
                    return typeAbbr + "=" + val + "(" + s.getRolls() + (s.isModified() ? "★" : "") + ")";
                })
                .collect(Collectors.joining(" "));
    }
}
