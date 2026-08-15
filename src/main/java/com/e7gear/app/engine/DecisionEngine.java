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

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DecisionEngine {

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

        // 6. Mod-gem potential (salvageable piece)
        Decision modDecision = checkModGemPotential(gear, score, roles, best);
        if (modDecision != null) {
            return modDecision;
        }

        // 7. Manual Review Fallbacks (renumbered)
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

        // 8. Delete Candidates
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
        // Keep review threshold unchanged for now
        return config.reviewScore();
    }

    private boolean isLeftSideSlot(String gearType) {
        if (gearType == null) return false;
        String g = gearType.trim().toLowerCase();
        return g.equals("weapon") || g.equals("helmet") || g.equals("helm") || g.equals("armor");
    }

    private Decision lowQualityDecision(GearScore score, RoleEvaluation roles) {
        if (score.score() >= getEffectiveReviewScore(null)) { // gear not needed for review threshold
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

    // ----- Mod-gem potential -----

    private Decision checkModGemPotential(Gear gear, GearScore originalScore, RoleEvaluation roles, RoleScore best) {
        if (best == null || gear.getSubstats() == null || gear.getSubstats().size() < 4) {
            return null;
        }

        // Allow mod-gem if:
        // - at least 3 useful stats (strong already, just one dead stat), OR
        // - at least 2 useful stats AND (has a core stat OR slot-compatible)
        if (best.usefulStatCount() < 2) {
            return null;
        }
        if (best.usefulStatCount() < 3 && !best.slotCompatible() && best.coreStatCount() < 1) {
            return null;
        }

        Set<StatType> roleStats = RoleEvaluator.getRoleStats(best.role());
        if (roleStats == null || roleStats.isEmpty()) {
            return null;
        }

        List<Substat> substats = gear.getSubstats();

        // Find worst substat that is NOT in roleStats (dead stat)
        Substat worstDead = null;
        double worstScore = Double.MAX_VALUE;
        for (Substat sub : substats) {
            StatType st = StatType.fromString(sub.getType());
            if (st == null) continue;
            if (!roleStats.contains(st)) {
                double statScore = gearScorer.calculateStatScore(st, sub.getValue());
                if (statScore < worstScore) {
                    worstScore = statScore;
                    worstDead = sub;
                }
            }
        }

        if (worstDead == null) {
            return null; // no dead stat to replace
        }

        // Determine which role stats are missing
        Set<StatType> presentStats = substats.stream()
                .map(s -> StatType.fromString(s.getType()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<StatType> missingStats = roleStats.stream()
                .filter(st -> !presentStats.contains(st))
                .toList();

        if (missingStats.isEmpty()) {
            return null; // no meaningful replacement
        }

        double keepThreshold = config.modGemThreshold();  // use dedicated threshold

        for (StatType replacement : missingStats) {
            Integer maxGemValue = config.modGemMax().get(replacement.displayName());
            if (maxGemValue == null) continue;

            double newValue = maxGemValue.doubleValue();
            double potentialScore = computePotentialScore(gear, worstDead, replacement, newValue);
            if (potentialScore >= keepThreshold) {
                return new Decision(Quality.KEEP_MOD_CANDIDATE,
                        String.format("Salvageable with mod: replace %s with %s", worstDead.getType(), replacement.displayName()),
                        originalScore, roles);
            }
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
}
