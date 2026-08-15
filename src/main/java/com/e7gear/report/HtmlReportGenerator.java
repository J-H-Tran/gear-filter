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
    // ---- Icon mappings (relative to HTML output folder, e.g., "images/") ----
    private static final Map<String, String> SET_ICON_MAP = Map.ofEntries(
            Map.entry("AttackSet", "attack-set.png"),
            Map.entry("SpeedSet", "speed-set.png"),
            Map.entry("CriticalSet", "crit-set.png"),
            Map.entry("DestructionSet", "destruction-set.png"),
            Map.entry("HitSet", "hit-set.png"),
            Map.entry("HealthSet", "health-set.png"),
            Map.entry("DefenseSet", "defense-set.png"),
            Map.entry("CounterSet", "counter-set.png"),
            Map.entry("LifestealSet", "lifesteal-set.png"),
            Map.entry("ImmunitySet", "immunity-set.png"),
            Map.entry("PenetrationSet", "penetration-set.png"),
            Map.entry("TorrentSet", "torrent-set.png"),
            Map.entry("ProtectionSet", "protection-set.png"),
            Map.entry("ResistSet", "resistance-set.png"),
            Map.entry("RageSet", "rage-set.png"),
            Map.entry("RevengeSet", "revenge-set.png"),
            Map.entry("InjurySet", "injury-set.png"),
            Map.entry("ReversalSet", "reversal-set.png"),
            Map.entry("RiposteSet", "riposte-set.png"),
            Map.entry("WarfareSet", "warfare-set.png"),
            Map.entry("PursuitSet", "pursuit-set.png"),
            Map.entry("WeakeningSet", "weakening-set.png"),
            Map.entry("FervorSet", "fervor-set.png"),
            Map.entry("UnitySet", "unity-set.png")
    );

    private static final Map<StatType, String> STAT_ICON_MAP = Map.ofEntries(
            Map.entry(StatType.ATTACK_PERCENT, "patk-stat.png"),
            Map.entry(StatType.DEFENSE_PERCENT, "pdef-stat.png"),
            Map.entry(StatType.HEALTH_PERCENT, "php-stat.png"),
            Map.entry(StatType.CRIT_CHANCE, "critcha-stat.png"),
            Map.entry(StatType.CRIT_DAMAGE, "critdmg-stat.png"),
            Map.entry(StatType.EFFECTIVENESS, "eff-stat.png"),
            Map.entry(StatType.EFFECT_RESISTANCE, "effres-stat.png"),
            Map.entry(StatType.FLAT_ATTACK, "atk-stat.png"),
            Map.entry(StatType.FLAT_DEFENSE, "fdef-stat.png"),
            Map.entry(StatType.FLAT_HEALTH, "fhp-stat.png"),
            Map.entry(StatType.SPEED, "spd-stat.png")
    );

    private static final Map<String, String> SLOT_ICON_MAP = Map.of(
            "Weapon", "weapon-item.png",
            "Helmet", "helmet-item.png",
            "Armor", "armor-item.png",
            "Necklace", "necklace-item.png",
            "Ring", "ring-item.png",
            "Boots", "boot-item.png"
    );

    private static final String ICON_IMG_TEMPLATE = "<img src=\"images/%s\" alt=\"%s\" class=\"stat-icon\">";

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
        long reforge = qualityCounts.getOrDefault(Quality.REFORGE_CANDIDATE, 0L);
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

        // Set breakdown (for +15, by quality) – keep stripped for chart labels
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

        // ---- REFORGE grouped by set (use full set name for icon lookup) ----
        Map<String, List<AnalysisResult>> reforgeBySet = enhanced.stream()
                .filter(r -> r.decision().quality() == Quality.REFORGE_CANDIDATE)
                .collect(Collectors.groupingBy(
                        r -> r.gear().getSet() != null ? r.gear().getSet() : "Unknown",
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

        // --- Collect unique values for filters ---
        Set<String> uniqueSlots = enhanced.stream()
                .map(r -> normalizeSlot(r.gear().getGear()))
                .filter(s -> !s.equals("Unknown"))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> uniqueSets = enhanced.stream()
                .map(r -> r.gear().getSet() != null ? r.gear().getSet().replace("Set", "") : "Unknown")
                .filter(s -> !s.equals("Unknown"))
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> qualityOptions = Arrays.stream(Quality.values())
                .filter(q -> q != Quality.UNENHANCED)
                .map(Enum::name)
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
        html.append("    .card .value.reforge { color: #58a6ff; }\n");
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
        html.append("    .badge.reforge { background: #58a6ff33; color: #58a6ff; border: 1px solid #58a6ff66; }\n");
        html.append("    .badge.review { background: #f0883e33; color: #f0883e; border: 1px solid #f0883e66; }\n");
        html.append("    .badge.delete { background: #f8514933; color: #f85149; border: 1px solid #f8514966; }\n");
        html.append("    .badge.unenhanced { background: #8b949e33; color: #8b949e; border: 1px solid #8b949e66; }\n");
        html.append("    .table-wrap { max-height: 400px; overflow-y: auto; margin-bottom: 16px; }\n");
        html.append("    @media (max-width: 800px) { .chart-grid { grid-template-columns: 1fr; } }\n");
        html.append("    .stat-icon {\n");
        html.append("      width: 25px;\n");
        html.append("      height: 25px;\n");
        html.append("      vertical-align: middle;\n");
        html.append("      margin-right: 2px;\n");
        html.append("    }\n");
        html.append("    .filter-bar {\n");
        html.append("      display: flex;\n");
        html.append("      flex-wrap: wrap;\n");
        html.append("      gap: 10px;\n");
        html.append("      background: #161b22;\n");
        html.append("      border: 1px solid #30363d;\n");
        html.append("      border-radius: 8px;\n");
        html.append("      padding: 16px;\n");
        html.append("      margin-bottom: 16px;\n");
        html.append("      align-items: center;\n");
        html.append("    }\n");
        html.append("    .filter-bar select, .filter-bar input {\n");
        html.append("      background: #0d1117;\n");
        html.append("      color: #c9d1d9;\n");
        html.append("      border: 1px solid #30363d;\n");
        html.append("      border-radius: 4px;\n");
        html.append("      padding: 4px 8px;\n");
        html.append("      font-size: 13px;\n");
        html.append("    }\n");
        html.append("    .filter-bar label {\n");
        html.append("      font-size: 13px;\n");
        html.append("      color: #8b949e;\n");
        html.append("    }\n");
        html.append("    .filter-bar .reset-btn {\n");
        html.append("      background: #21262d;\n");
        html.append("      border: 1px solid #30363d;\n");
        html.append("      color: #c9d1d9;\n");
        html.append("      padding: 4px 12px;\n");
        html.append("      border-radius: 4px;\n");
        html.append("      cursor: pointer;\n");
        html.append("    }\n");
        html.append("    .filter-bar .reset-btn:hover {\n");
        html.append("      background: #30363d;\n");
        html.append("    }\n");
        html.append("    .filter-bar input[type=\"number\"] {\n");
        html.append("      width: 60px;\n");
        html.append("    }\n");
        html.append("    .filter-bar .substat-search {\n");
        html.append("      width: 150px;\n");
        html.append("    }\n");
        html.append("    .no-results {\n");
        html.append("      color: #8b949e;\n");
        html.append("      text-align: center;\n");
        html.append("      padding: 20px;\n");
        html.append("    }\n");
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
        html.append("    <div class=\"card\"><div class=\"label\">REFORGE CANDIDATE</div><div class=\"value reforge\">").append(reforge).append("</div></div>\n");
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
            // Header with slot icon
            html.append("  <h3>⚔️ ").append(formatSlotWithIcon(slot)).append("</h3>\n");
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

        // ---- REFORGE tables grouped by set (full set name used as key) ----
        html.append("  <h2>🔧 Top REFORGE_CANDIDATE by Set (Lowest Current Score, Reforge makes them viable)</h2>\n");
        boolean hasReforges = false;
        // Sort by the stripped set name for readability
        List<String> fullSetOrder = reforgeBySet.keySet().stream()
                .sorted(Comparator.comparing(s -> s.replace("Set", "")))
                .toList();
        for (String fullSet : fullSetOrder) {
            List<AnalysisResult> setReforges = reforgeBySet.get(fullSet);
            if (setReforges.isEmpty()) continue;
            hasReforges = true;
            // Header with set icon
            String stripped = fullSet.replace("Set", "");
            html.append("  <h3>⚡ ").append(formatSetWithIcon(fullSet)).append("</h3>\n");
            html.append("  <div class=\"table-wrap\">\n");
            html.append("    <table>\n");
            html.append("      <thead><tr><th>Slot</th><th>Main</th><th>Substats</th><th>Current Score</th><th>Reason</th></tr></thead>\n");
            html.append("      <tbody>\n");
            for (AnalysisResult r : setReforges) {
                html.append(rowHtmlForReforge(r));
            }
            html.append("      </tbody>\n");
            html.append("    </table>\n");
            html.append("  </div>\n");
        }
        if (!hasReforges) {
            html.append("  <p style=\"color:#8b949e;\">No REFORGE_CANDIDATE items found.</p>\n");
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

        // ---- All +15 items with filter bar ----
        html.append("  <h2>📊 All +15 Items (sorted by Score ascending)</h2>\n");

        // ---- Filter Bar ----
        html.append("  <div class=\"filter-bar\" id=\"filterBar\">\n");
        html.append("    <label>Quality:</label>\n");
        html.append("    <select id=\"filterQuality\"><option value=\"\">All</option>");
        for (String q : qualityOptions) {
            html.append("<option value=\"").append(q).append("\">").append(q).append("</option>");
        }
        html.append("    </select>\n");

        html.append("    <label>Slot:</label>\n");
        html.append("    <select id=\"filterSlot\"><option value=\"\">All</option>");
        for (String s : uniqueSlots) {
            html.append("<option value=\"").append(s).append("\">").append(s).append("</option>");
        }
        html.append("    </select>\n");

        html.append("    <label>Set:</label>\n");
        html.append("    <select id=\"filterSet\"><option value=\"\">All</option>");
        for (String s : uniqueSets) {
            html.append("<option value=\"").append(s).append("\">").append(s).append("</option>");
        }
        html.append("    </select>\n");

        html.append("    <label>Min Score:</label>\n");
        html.append("    <input type=\"number\" id=\"filterMinScore\" min=\"0\" step=\"0.1\" placeholder=\"0\">\n");
        html.append("    <label>Max Score:</label>\n");
        html.append("    <input type=\"number\" id=\"filterMaxScore\" min=\"0\" step=\"0.1\" placeholder=\"999\">\n");

        html.append("    <label>Substat search:</label>\n");
        html.append("    <input type=\"text\" id=\"filterSubstat\" class=\"substat-search\" placeholder=\"e.g. Speed\">\n");

        html.append("    <button class=\"reset-btn\" id=\"resetFilters\">Reset Filters</button>\n");
        html.append("  </div>\n");

        // ---- Table ----
        html.append("  <div class=\"table-wrap\" style=\"max-height:600px;\">\n");
        html.append("    <table id=\"allItemsTable\">\n");
        html.append("      <thead><tr><th>Slot</th><th>Set</th><th>Main</th><th>Substats</th><th>Score</th><th>Quality</th><th>Reason</th></tr></thead>\n");
        html.append("      <tbody id=\"allItemsBody\">\n");
        for (AnalysisResult r : allEnhancedSorted) {
            html.append(rowHtml(r));
        }
        html.append("      </tbody>\n");
        html.append("    </table>\n");
        html.append("  </div>\n");

        html.append("</div>\n");

        // ---- JavaScript for Charts & Filtering ----
        html.append("<script>\n");
        // Quality chart
        html.append("const qualityCtx = document.getElementById('qualityChart').getContext('2d');\n");
        html.append("new Chart(qualityCtx, {\n");
        html.append("  type: 'doughnut',\n");
        html.append("  data: {\n");
        html.append("    labels: ['KEEP', 'MOD CANDIDATE', 'REFORGE CANDIDATE', 'REVIEW', 'DELETE CANDIDATE'],\n");
        html.append("    datasets: [{\n");
        html.append("      data: [").append(keep).append(", ").append(mod).append(", ").append(reforge).append(", ").append(review).append(", ").append(del).append("],\n");
        html.append("      backgroundColor: ['#2ea043', '#d29922', '#58a6ff', '#f0883e', '#f85149'],\n");
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
        html.append("      { label: 'REFORGE CANDIDATE', data: ").append(jsonArray(slotLabels, slotStats, Quality.REFORGE_CANDIDATE)).append(", backgroundColor: '#58a6ff' },\n");
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
        html.append("      { label: 'REFORGE CANDIDATE', data: ").append(jsonArray(setLabels, setStats, Quality.REFORGE_CANDIDATE)).append(", backgroundColor: '#58a6ff' },\n");
        html.append("      { label: 'REVIEW', data: ").append(jsonArray(setLabels, setStats, Quality.REVIEW)).append(", backgroundColor: '#f0883e' },\n");
        html.append("      { label: 'DELETE CANDIDATE', data: ").append(jsonArray(setLabels, setStats, Quality.DELETE_CANDIDATE)).append(", backgroundColor: '#f85149' }\n");
        html.append("    ]\n");
        html.append("  },\n");
        html.append("  options: { responsive: true, plugins: { legend: { position: 'bottom', labels: { color: '#c9d1d9' } } }, scales: { x: { ticks: { color: '#8b949e' } }, y: { ticks: { color: '#8b949e', stepSize: 1 } } } }\n");
        html.append("});\n");

        // ---- Filtering Logic ----
        html.append("(function() {\n");
        html.append("  const rows = document.querySelectorAll('#allItemsBody tr');\n");
        html.append("  const qualityFilter = document.getElementById('filterQuality');\n");
        html.append("  const slotFilter = document.getElementById('filterSlot');\n");
        html.append("  const setFilter = document.getElementById('filterSet');\n");
        html.append("  const minScoreFilter = document.getElementById('filterMinScore');\n");
        html.append("  const maxScoreFilter = document.getElementById('filterMaxScore');\n");
        html.append("  const substatFilter = document.getElementById('filterSubstat');\n");
        html.append("  const resetBtn = document.getElementById('resetFilters');\n");
        html.append("\n");
        html.append("  function filterRows() {\n");
        html.append("    const quality = qualityFilter.value;\n");
        html.append("    const slot = slotFilter.value;\n");
        html.append("    const setVal = setFilter.value;\n");
        html.append("    const minScore = parseFloat(minScoreFilter.value) || 0;\n");
        html.append("    const maxScore = parseFloat(maxScoreFilter.value) || 999;\n");
        html.append("    const substat = substatFilter.value.toLowerCase();\n");
        html.append("\n");
        html.append("    let visibleCount = 0;\n");
        html.append("    rows.forEach(row => {\n");
        html.append("      const rowQuality = row.dataset.quality || '';\n");
        html.append("      const rowSlot = row.dataset.slot || '';\n");
        html.append("      const rowSet = row.dataset.set || '';\n");
        html.append("      const rowScore = parseFloat(row.dataset.score) || 0;\n");
        html.append("      const rowSubstats = row.dataset.substats || '';\n");
        html.append("\n");
        html.append("      let show = true;\n");
        html.append("      if (quality && rowQuality !== quality) show = false;\n");
        html.append("      if (slot && rowSlot !== slot) show = false;\n");
        html.append("      if (setVal && rowSet !== setVal) show = false;\n");
        html.append("      if (rowScore < minScore || rowScore > maxScore) show = false;\n");
        html.append("      if (substat && !rowSubstats.toLowerCase().includes(substat)) show = false;\n");
        html.append("\n");
        html.append("      row.style.display = show ? '' : 'none';\n");
        html.append("      if (show) visibleCount++;\n");
        html.append("    });\n");
        html.append("\n");
        html.append("    let noResults = document.getElementById('noResultsMessage');\n");
        html.append("    if (!noResults) {\n");
        html.append("      noResults = document.createElement('tr');\n");
        html.append("      noResults.id = 'noResultsMessage';\n");
        html.append("      noResults.innerHTML = '<td colspan=\"7\" class=\"no-results\">No items match the filters.</td>';\n");
        html.append("      document.getElementById('allItemsBody').appendChild(noResults);\n");
        html.append("    }\n");
        html.append("    if (visibleCount === 0) {\n");
        html.append("      noResults.style.display = '';\n");
        html.append("    } else {\n");
        html.append("      noResults.style.display = 'none';\n");
        html.append("    }\n");
        html.append("  }\n");
        html.append("\n");
        html.append("  qualityFilter.addEventListener('change', filterRows);\n");
        html.append("  slotFilter.addEventListener('change', filterRows);\n");
        html.append("  setFilter.addEventListener('change', filterRows);\n");
        html.append("  minScoreFilter.addEventListener('input', filterRows);\n");
        html.append("  maxScoreFilter.addEventListener('input', filterRows);\n");
        html.append("  substatFilter.addEventListener('input', filterRows);\n");
        html.append("\n");
        html.append("  resetBtn.addEventListener('click', function() {\n");
        html.append("    qualityFilter.value = '';\n");
        html.append("    slotFilter.value = '';\n");
        html.append("    setFilter.value = '';\n");
        html.append("    minScoreFilter.value = '';\n");
        html.append("    maxScoreFilter.value = '';\n");
        html.append("    substatFilter.value = '';\n");
        html.append("    filterRows();\n");
        html.append("  });\n");
        html.append("})();\n");
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

    private static String formatSlotWithIcon(String slot) {
        if (slot == null || slot.equals("Unknown")) return slot;
        String iconFile = SLOT_ICON_MAP.get(slot);
        if (iconFile != null) {
            return String.format(ICON_IMG_TEMPLATE, iconFile, slot);
        }
        return slot;
    }

    // Row with Slot column (used for REVIEW and ALL tables)
    private static String rowHtml(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        String setIcon = formatSetWithIcon(g.getSet());
        String mainIcon = g.getMainStatType() != null ? formatStatWithIcon(StatType.fromString(g.getMainStatType())) : "";
        String mainVal = g.getMainStatValue() != 0 ? String.format("%.0f", g.getMainStatValue()) : "";
        String substats = formatSubstatsShort(g);
        String quality = d.quality().name();
        String slot = normalizeSlot(g.getGear());
        String set = g.getSet() != null ? g.getSet().replace("Set", "") : "";
        double score = d.gearScore().score();
        String slotIcon = formatSlotWithIcon(slot);

        return "      <tr data-quality=\"" + quality + "\" data-slot=\"" + slot + "\" data-set=\"" + set + "\" data-score=\"" + score + "\" data-substats=\"" + escapeAttr(stripHtml(substats)) + "\">" +
                "<td>" + slotIcon + "</td>" +
                "<td>" + setIcon + "</td>" +
                "<td>" + mainIcon + " " + mainVal + "</td>" +
                "<td style=\"font-size:12px;\">" + substats + "</td>" +
                "<td>" + String.format("%.1f", score) + "</td>" +
                "<td><span class=\"badge " + qualityClass(d.quality()) + "\">" + quality + "</span></td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
    }

    // Row WITHOUT Slot column (used for slot-grouped DELETE tables)
    private static String rowHtmlWithoutSlot(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        String setIcon = formatSetWithIcon(g.getSet());
        String mainIcon = g.getMainStatType() != null ? formatStatWithIcon(StatType.fromString(g.getMainStatType())) : "";
        String mainVal = g.getMainStatValue() != 0 ? String.format("%.0f", g.getMainStatValue()) : "";
        String substats = formatSubstatsShort(g);

        return "      <tr>" +
                "<td>" + setIcon + "</td>" +
                "<td>" + mainIcon + " " + mainVal + "</td>" +
                "<td style=\"font-size:12px;\">" + substats + "</td>" +
                "<td>" + String.format("%.1f", d.gearScore().score()) + "</td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
    }

    // Row for REFORGE tables (shows Slot, no Set column because it's grouped by set)
    private static String rowHtmlForReforge(AnalysisResult r) {
        Gear g = r.gear();
        Decision d = r.decision();
        String slot = normalizeSlot(g.getGear());
        String slotIcon = formatSlotWithIcon(slot);
        String mainIcon = g.getMainStatType() != null ? formatStatWithIcon(StatType.fromString(g.getMainStatType())) : "";
        String mainVal = g.getMainStatValue() != 0 ? String.format("%.0f", g.getMainStatValue()) : "";
        String substats = formatSubstatsShort(g);

        return "      <tr>" +
                "<td>" + slotIcon + "</td>" +
                "<td>" + mainIcon + " " + mainVal + "</td>" +
                "<td style=\"font-size:12px;\">" + substats + "</td>" +
                "<td>" + String.format("%.1f", d.gearScore().score()) + "</td>" +
                "<td style=\"font-size:12px;color:#8b949e;\">" + d.reason() + "</td>" +
                "</tr>\n";
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

    private static String formatSubstatsShort(Gear g) {
        if (g.getSubstats() == null || g.getSubstats().isEmpty()) return "";
        return g.getSubstats().stream()
                .map(s -> {
                    StatType stat = StatType.fromString(s.getType());
                    String iconHtml = stat != null ? formatStatWithIcon(stat) : s.getType();
                    String val = s.getValue() % 1 == 0 ? String.format("%d", (long) s.getValue()) : String.format("%.1f", s.getValue());
                    String rolls = s.getRolls() + (s.isModified() ? "★" : "");
                    return iconHtml + "=" + val + "(" + rolls + ")";
                })
                .collect(Collectors.joining(" "));
    }

    private static String formatSetWithIcon(String set) {
        if (set == null) return "";
        // If it doesn't end with "Set", assume it's already stripped; we need the full name for lookup
        String key = set.endsWith("Set") ? set : set + "Set";
        String iconFile = SET_ICON_MAP.get(key);
        if (iconFile != null) {
            return String.format(ICON_IMG_TEMPLATE, iconFile, key.replace("Set", ""));
        }
        return key.replace("Set", ""); // fallback to text
    }

    private static String formatStatWithIcon(StatType stat) {
        if (stat == null) return "";
        String iconFile = STAT_ICON_MAP.get(stat);
        if (iconFile != null) {
            return String.format(ICON_IMG_TEMPLATE, iconFile, stat.abbreviation());
        }
        return stat.abbreviation(); // fallback
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("\"", "&quot;");
    }
}
