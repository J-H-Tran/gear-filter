package com.e7gear.app.engine;

import com.e7gear.app.role.Role;
import com.e7gear.app.role.RoleEvaluation;
import com.e7gear.app.role.RoleScore;
import com.e7gear.app.scorer.GearScore;
import com.e7gear.app.scorer.GearScorer;
import com.e7gear.config.FilterConfig;
import com.e7gear.gear.Gear;
import com.e7gear.gear.Quality;
import com.e7gear.gear.Slot;
import com.e7gear.gear.Substat;
import com.e7gear.stats.StatType;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DecisionEngine {

    private static final Map<String, Set<StatType>> ALLOWED_SUBSTATS = Map.of(
            "Weapon", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED, StatType.FLAT_HEALTH
            ),
            "Helmet", EnumSet.of(
                    StatType.HEALTH_PERCENT, StatType.ATTACK_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED, StatType.FLAT_ATTACK, StatType.FLAT_DEFENSE
            ),
            "Armor", EnumSet.of(
                    StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED, StatType.FLAT_HEALTH
            ),
            "Necklace", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED, StatType.FLAT_ATTACK, StatType.FLAT_HEALTH, StatType.FLAT_DEFENSE
            ),
            "Ring", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED, StatType.FLAT_ATTACK, StatType.FLAT_HEALTH, StatType.FLAT_DEFENSE
            ),
            "Boots", EnumSet.of(
                    StatType.ATTACK_PERCENT, StatType.HEALTH_PERCENT, StatType.DEFENSE_PERCENT,
                    StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.EFFECTIVENESS,
                    StatType.EFFECT_RESISTANCE, StatType.SPEED, StatType.FLAT_ATTACK, StatType.FLAT_HEALTH, StatType.FLAT_DEFENSE
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

        if (hasExceptionalSpeed(gear)) {
            return keep("Exceptional Speed safety rule (Opener)", score, roles);
        }

        if (score.hasModified() && score.score() >= config.reviewScore()) {
            return keep("Modified substat with acceptable Gear Score", score, roles);
        }

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

        if (best.slotCompatible() && best.coreStatCount() >= strongCore && best.mainStatPreferred()) {
            return keep("Strong slot/main-stat role fit", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= strongCore && score.score() >= effectiveKeep) {
            return keep("Strong slot role fit with high Gear Score", score, roles);
        }

        if (score.hasSpike()
                && best.slotCompatible()
                && best.coreStatCount() >= reviewCore
                && (best.mainStatPreferred() || score.score() >= effectiveReview)) {
            return keep("Spike with coherent role fit", score, roles);
        }

        Decision modDecision = checkModGemPotential(gear, score, roles, best);
        if (modDecision != null) {
            return modDecision;
        }

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

        if (!best.slotCompatible() && best.usefulStatCount() <= 2 && !score.hasSpike()) {
            return delete("Low score with no coherent role fit", score, roles);
        }

        return delete("Low-quality spread with weak role signal", score, roles);
    }

    private double getModGemCap(StatType stat, int itemLevel) {
        String levelKey = String.valueOf(itemLevel);
        Map<String, Integer> caps = config.modGemMax().get(stat.name());
        if (caps != null && caps.containsKey(levelKey)) {
            return caps.get(levelKey);
        }
        return stat.getMaxModValue();
    }

    private Decision checkModGemPotential(Gear gear, GearScore score, RoleEvaluation roles, RoleScore bestRole) {
        if (gear == null || gear.getSubstats() == null || gear.getSubstats().isEmpty()) {
            return null;
        }
        if (score.hasModified()) {
            return null;
        }

        String normalizedSlot = normalizeSlot(gear.getGear());
        Set<StatType> allowedStats = ALLOWED_SUBSTATS.getOrDefault(normalizedSlot, EnumSet.noneOf(StatType.class));

        Substat candidateToReplace = findWorstSubstat(gear);
        if (candidateToReplace == null) {
            return null;
        }

        StatType candidateStat = StatType.fromString(candidateToReplace.getType());
        double candidateWeight = candidateStat != null ? candidateStat.weight() : 0.0;
        double currentSubstatScore = candidateToReplace.getValue() * candidateWeight;

        StatType bestReplacement = null;
        double maxGain = 0.0;

        for (StatType targetStat : allowedStats) {
            if (gear.hasStat(targetStat)) continue;

            double modCap = getModGemCap(targetStat, gear.getGearLevel());
            double potentialModValue = modCap * targetStat.weight();
            double gain = potentialModValue - currentSubstatScore;

            if (gain > maxGain) {
                maxGain = gain;
                bestReplacement = targetStat;
            }
        }

        if (bestReplacement != null) {
            double potentialScore = score.score() + maxGain;
            if (potentialScore >= config.modGemThreshold()) {
                return new Decision(
                        Quality.KEEP_MOD_CANDIDATE,
                        String.format("Mod-gem candidate: replace %s with %s (potential score %.1f)",
                                candidateToReplace.getType(), bestReplacement.displayName(), potentialScore),
                        score,
                        roles
                );
            }
        }
        return null;
    }

    private Substat findWorstSubstat(Gear gear) {
        if (gear.getSubstats() == null || gear.getSubstats().isEmpty()) return null;
        Substat worst = null;
        double minVal = Double.MAX_VALUE;
        for (Substat s : gear.getSubstats()) {
            if (s == null) continue;
            StatType st = StatType.fromString(s.getType());
            double weight = st != null ? st.weight() : 0.0;
            double val = s.getValue() * weight;
            if (val < minVal) {
                minVal = val;
                worst = s;
            }
        }
        return worst;
    }

    public double simulateReforgeScore(Gear gear) {
        return simulateReforgeScore(gear, this.gearScorer);
    }

    public double simulateReforgeScore(Gear gear, GearScorer scorer) {
        if (gear == null || gear.getGearLevel() < 85 || gear.isReforged()) {
            return scorer.score(gear).score();
        }

        List<Substat> simulatedSubstats = gear.getSubstats().stream().map(s -> {
            double currentValue = s.getValue();
            double addedValue = getReforgeIncrement(StatType.fromString(s.getType()), currentValue, s.getRolls());
            return new Substat(s.getType(), currentValue + addedValue, s.getRolls(), s.isModified());
        }).collect(Collectors.toList());

        Gear tempGear = gear.toBuilder()
                .substats(simulatedSubstats)
                .reforged(true)
                .build();

        return scorer.score(tempGear).score();
    }

    private double getReforgeIncrement(StatType type, double value, int rolls) {
        if (type == null) return 0.0;
        return switch (type) {
            case SPEED -> (rolls >= 5) ? 5 : Math.max(1, rolls);
            case CRIT_CHANCE -> Math.min(5, rolls);
            case CRIT_DAMAGE -> Math.min(7, rolls + 2);
            case ATTACK_PERCENT, HEALTH_PERCENT, DEFENSE_PERCENT, EFFECTIVENESS, EFFECT_RESISTANCE -> Math.min(8, rolls + 2);
            case FLAT_ATTACK -> 35;
            case FLAT_HEALTH -> 200;
            case FLAT_DEFENSE -> 20;
        };
    }

    private boolean isRightSideSlot(String slotRaw) {
        Slot slot = Slot.fromString(slotRaw);
        return slot != null && slot.isRightSide();
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

    private boolean isLeftSideSlot(String slotRaw) {
        Slot slot = Slot.fromString(slotRaw);
        return slot != null && slot.isLeftSide();
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
                .filter(Objects::nonNull)
                .anyMatch(s -> StatType.fromString(s.getType()) == StatType.SPEED && s.getValue() >= threshold);
    }

    private String normalizeSlot(String slot) {
        if (slot == null) return "";
        return switch (slot.toLowerCase()) {
            case "weapon" -> "Weapon";
            case "helmet", "helm" -> "Helmet";
            case "armor" -> "Armor";
            case "necklace", "neck" -> "Necklace";
            case "ring" -> "Ring";
            case "boots", "boot" -> "Boots";
            default -> slot;
        };
    }
}
