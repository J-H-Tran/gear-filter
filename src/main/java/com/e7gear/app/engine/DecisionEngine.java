package com.e7gear.app.engine;

import com.e7gear.config.FilterConfig;
import com.e7gear.gear.Gear;
import com.e7gear.gear.Quality;
import com.e7gear.gear.Substat;
import com.e7gear.app.role.Role;
import com.e7gear.app.role.RoleEvaluation;
import com.e7gear.app.role.RoleEvaluator;
import com.e7gear.app.role.RoleScore;
import com.e7gear.app.scorer.GearScore;
import com.e7gear.app.scorer.GearScorer;
import com.e7gear.stats.StatType;

import java.util.*;
import java.util.stream.Collectors;

public final class DecisionEngine {

    // ---- Slot-aware allowed substats (from official substat tables) ----
    private static final Map<String, Set<StatType>> ALLOWED_SUBSTATS = Map.of(
            "Weapon", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED,
                    StatType.FLAT_HEALTH
            ),
            "Helmet", EnumSet.of(
                    StatType.HEALTH_PERCENT, StatType.ATTACK_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED,
                    StatType.FLAT_ATTACK, StatType.FLAT_DEFENSE
            ),
            "Armor", EnumSet.of(
                    StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED, StatType.FLAT_HEALTH
            ),
            "Necklace", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED,
                    StatType.FLAT_ATTACK, StatType.FLAT_HEALTH, StatType.FLAT_DEFENSE
            ),
            "Ring", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED,
                    StatType.FLAT_ATTACK, StatType.FLAT_HEALTH, StatType.FLAT_DEFENSE
            ),
            "Boots", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED,
                    StatType.FLAT_ATTACK, StatType.FLAT_HEALTH, StatType.FLAT_DEFENSE
            )
    );

    private final FilterConfig config;
    private final GearScorer gearScorer;

    public DecisionEngine(FilterConfig config, GearScorer gearScorer) {
        if (config == null || gearScorer == null) {
            throw new IllegalArgumentException("Config and GearScorer must not be null");
        }
        this.config = config;
        this.gearScorer = gearScorer;
    }

    public Decision decide(Gear gear, GearScore score, RoleEvaluation roles) {
        if (gear == null) {
            return new Decision(Quality.DELETE_CANDIDATE, "Null gear", score, roles);
        }
        if (score == null || roles == null) {
            throw new IllegalArgumentException("score and roles must not be null");
        }

        // 1. Exceptional Speed Safety Rule (Opener bypass)
        if (hasExceptionalSpeed(gear)) {
            return keep("Exceptional Speed safety rule (Opener)", score, roles);
        }

        // 2. Modified substat with acceptable Gear Score
        if (score.hasModified() && score.score() >= config.reviewScore()) {
            return keep("Modified substat with acceptable Gear Score", score, roles);
        }

        // 3. Left-side Gear (Weapon, Helmet, Armor) – high score is enough
        if (isLeftSideSlot(gear.getGear()) && score.score() >= config.keepScore()) {
            return keep("High Gear Score on left-side slot", score, roles);
        }

        RoleScore best = bestRole(roles);
        if (best == null) {
            return lowQualityDecision(score, roles);
        }

        int strongCore = config.strongCoreStats();
        int reviewCore = config.reviewCoreStats();
        double effectiveKeep = getEffectiveKeepScore(gear, best);
        double effectiveReview = getEffectiveReviewScore(gear);

        // 4. Role Fit + Gear Score Rules
        if (best.slotCompatible() && best.coreStatCount() >= strongCore && best.mainStatPreferred()) {
            return keep("Strong slot/main-stat role fit", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= strongCore && score.score() >= effectiveKeep) {
            return keep("Strong slot role fit with high Gear Score", score, roles);
        }

        // 5. Stat Spike Rule
        if (score.hasSpike()
                && best.slotCompatible()
                && best.coreStatCount() >= reviewCore
                && (best.mainStatPreferred() || score.score() >= effectiveReview)) {
            return keep("Spike with coherent role fit", score, roles);
        }

        // 6. Mod-gem potential (salvageable piece) – ENHANCED VERSION
        Decision modDecision = checkModGemPotential(gear, score, roles, best);
        if (modDecision != null) {
            return modDecision;
        }

        // ---- 7. Reforge potential ----
        if (gear.getLevel() == 85 && gear.getEnhance() == 15) {
            double reforgeScore = simulateReforgeScore(gear);
            if (reforgeScore >= config.reforgeThreshold()) {
                return new Decision(
                        Quality.REFORGE_CANDIDATE,
                        "Becomes viable after reforging (score " + String.format("%.1f", reforgeScore) + ")",
                        score,
                        roles
                );
            }
        }

        // 8. Manual Review Fallbacks
        if (score.score() >= effectiveKeep) {
            return review("High Gear Score but incomplete role signal", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= reviewCore && best.mainStatPreferred()) {
            return review("One core stat with preferred main stat", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= 2) {
            return review("Multiple slot-preferred stats but incomplete main-stat fit", score, roles);
        }

        if (best.usefulStatCount() >= 2 && score.score() >= effectiveReview) {
            return review("Broad role fit with borderline Gear Score", score, roles);
        }

        if (score.score() >= effectiveReview) {
            return review("Borderline statistical quality", score, roles);
        }

        // 9. Delete Candidates
        if (!best.slotCompatible() && best.usefulStatCount() <= 2 && !score.hasSpike()) {
            return delete("Low score with no coherent role fit", score, roles);
        }

        return delete("Low-quality spread with weak role signal", score, roles);
    }

    // ---------- Helpers ----------

    private boolean isRightSideSlot(String gearType) {
        if (gearType == null) return false;
        String g = gearType.trim().toLowerCase();
        return g.equals("necklace") || g.equals("neck") || g.equals("ring") || g.equals("boots") || g.equals("boot");
    }

    private double getEffectiveKeepScore(Gear gear, RoleScore best) {
        double base = config.keepScore();
        if (isRightSideSlot(gear.getGear()) && best != null && best.mainStatPreferred()) {
            base -= config.rightSidePenalty();
        }
        return base;
    }

    private double getEffectiveReviewScore(Gear gear) {
        return config.reviewScore();
    }

    private boolean isLeftSideSlot(String gearType) {
        if (gearType == null) return false;
        String g = gearType.trim().toLowerCase();
        return g.equals("weapon") || g.equals("helmet") || g.equals("helm") || g.equals("armor");
    }

    private Decision lowQualityDecision(GearScore score, RoleEvaluation roles) {
        if (score.score() >= getEffectiveReviewScore(null)) {
            return review("No role fit but borderline/high Gear Score", score, roles);
        }
        return delete("No role fit and low Gear Score", score, roles);
    }

    private RoleScore bestRole(RoleEvaluation roles) {
        if (roles.bestRole() == Role.NONE) {
            return null;
        }
        return roles.scoreFor(roles.bestRole());
    }

    private Decision keep(String reason, GearScore score, RoleEvaluation roles) {
        return new Decision(Quality.KEEP, reason, score, roles);
    }

    private Decision review(String reason, GearScore score, RoleEvaluation roles) {
        return new Decision(Quality.REVIEW, reason, score, roles);
    }

    private Decision delete(String reason, GearScore score, RoleEvaluation roles) {
        return new Decision(Quality.DELETE_CANDIDATE, reason, score, roles);
    }

    private boolean hasExceptionalSpeed(Gear gear) {
        if (gear.getSubstats() == null) return false;
        int threshold = config.openerSpeedThreshold();
        return gear.getSubstats().stream()
                .filter(s -> s != null)
                .anyMatch(s -> {
                    StatType st = StatType.fromString(s.getType());
                    return st == StatType.SPEED && s.getValue() >= threshold;
                });
    }

    // ----- Mod-gem potential (ENHANCED) -----
    private Decision checkModGemPotential(Gear gear, GearScore originalScore, RoleEvaluation roles, RoleScore best) {
        if (best == null || gear.getSubstats() == null || gear.getSubstats().size() < 4) {
            return null;
        }

        // Ensure the piece has at least 2 useful stats for its best role.
        if (best.usefulStatCount() < 2) {
            return null;
        }

        String slot = normalizeSlot(gear.getGear());
        Set<StatType> allowedStats = ALLOWED_SUBSTATS.getOrDefault(slot, Collections.emptySet());
        if (allowedStats.isEmpty()) {
            return null; // unknown slot
        }

        // Determine which stats are already present
        Set<StatType> presentStats = gear.getSubstats().stream()
                .map(s -> StatType.fromString(s.getType()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Main stat (cannot duplicate)
        StatType mainStat = gear.getMain() != null ? StatType.fromString(gear.getMain().getType()) : null;

        // Find dead stats: not useful for the best role AND not modified
        Set<StatType> roleStats = RoleEvaluator.getRoleStats(best.role());
        List<Substat> deadStats = gear.getSubstats().stream()
                .filter(s -> {
                    StatType st = StatType.fromString(s.getType());
                    return st != null && !roleStats.contains(st) && !s.isModified();
                })
                .toList();

        if (deadStats.isEmpty()) {
            return null;
        }

        double bestPotentialScore = originalScore.score();
        Substat bestDead = null;
        StatType bestReplacement = null;
        double bestImprovement = 0.0;

        // Try every dead stat with every allowed replacement
        for (Substat dead : deadStats) {
            StatType deadType = StatType.fromString(dead.getType());
            if (deadType == null) continue;

            for (StatType candidate : allowedStats) {
                // Skip if already present or same as main stat
                if (presentStats.contains(candidate) || candidate == mainStat) continue;

                String levelKey = String.valueOf(gear.getLevel());
                Map<String, Integer> levelMap = config.modGemMax().get(candidate.displayName());
                if (levelMap == null) continue;

                // Get the max gem value for this stat (from config)
                Integer maxValue = levelMap.get(levelKey);
                if (maxValue == null) {
                    // Fallback: try 90 → 88 → 85
                    maxValue = levelMap.get("90");
                    if (maxValue == null) maxValue = levelMap.get("88");
                    if (maxValue == null) maxValue = levelMap.get("85");
                    if (maxValue == null) continue;
                }
                double potential = computePotentialScore(gear, dead, candidate, maxValue.doubleValue());
                double improvement = potential - originalScore.score();

                // Only consider if it improves score by at least 2.0 and beats current best
                if (potential > bestPotentialScore && improvement > 2.0) {
                    bestPotentialScore = potential;
                    bestDead = dead;
                    bestReplacement = candidate;
                    bestImprovement = improvement;
                }
            }
        }

        if (bestDead != null && bestPotentialScore >= config.modGemThreshold()) {
            StatType deadStat = StatType.fromString(bestDead.getType());
            String deadAbbr = deadStat != null ? deadStat.abbreviation() : bestDead.getType();

            // If bestReplacement is already a StatType, use bestReplacement.abbreviation() directly;
            // otherwise, look it up via StatType.fromString(bestReplacement.displayName())
            StatType replaceStat = StatType.fromString(bestReplacement.displayName());
            String replaceAbbr = replaceStat != null ? replaceStat.abbreviation() : bestReplacement.displayName();

            return new Decision(Quality.KEEP_MOD_CANDIDATE,
                    String.format("Salvageable with mod: replace %s with %s (+%.1f score)",
                            deadAbbr, replaceAbbr, bestImprovement),
                    originalScore, roles);
        }

        return null;
    }

    private double computePotentialScore(Gear gear, Substat toReplace, StatType newStat, double newValue) {
        List<Substat> modifiedSubstats = gear.getSubstats().stream()
                .map(s -> {
                    if (s == toReplace) {
                        Substat newSub = new Substat();
                        newSub.setType(newStat.displayName());
                        newSub.setValue(newValue);
                        newSub.setRolls(s.getRolls());
                        newSub.setModified(s.isModified());
                        return newSub;
                    }
                    return s;
                })
                .collect(Collectors.toList());

        Gear tempGear = new Gear();
        tempGear.setSubstats(modifiedSubstats);
        tempGear.setLevel(gear.getLevel());
        tempGear.setMain(gear.getMain());

        GearScore potentialScore = gearScorer.score(tempGear);
        return potentialScore.score();
    }

    private double simulateReforgeScore(Gear gear) {
        if (gear.getSubstats() == null || gear.getSubstats().isEmpty()) {
            return 0.0;
        }

        List<Substat> reforgedSubs = gear.getSubstats().stream()
                .map(s -> {
                    Substat newS = new Substat();
                    newS.setType(s.getType());
                    newS.setValue(s.getValue() * 1.2);
                    newS.setRolls(s.getRolls());
                    newS.setModified(s.isModified());
                    return newS;
                })
                .collect(Collectors.toList());

        Gear tempGear = new Gear();
        tempGear.setSubstats(reforgedSubs);
        return gearScorer.score(tempGear).score();
    }

    private String normalizeSlot(String slot) {
        if (slot == null) return "";
        switch (slot.toLowerCase()) {
            case "weapon": return "Weapon";
            case "helmet":
            case "helm": return "Helmet";
            case "armor": return "Armor";
            case "necklace":
            case "neck": return "Necklace";
            case "ring": return "Ring";
            case "boots":
            case "boot": return "Boots";
            default: return slot;
        }
    }
}
