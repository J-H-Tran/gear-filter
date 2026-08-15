package com.e7gear.engine;

import com.e7gear.config.FilterConfig;
import com.e7gear.gear.Gear;
import com.e7gear.gear.Quality;
import com.e7gear.role.Role;
import com.e7gear.role.RoleEvaluation;
import com.e7gear.role.RoleScore;
import com.e7gear.scorer.GearScore;

/**
 * Conservative final classifier using externalized configuration.
 * All thresholds are read from FilterConfig.
 */
public final class DecisionEngine {

    private final FilterConfig config;

    public DecisionEngine(FilterConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("FilterConfig must not be null");
        }
        this.config = config;
    }

    /**
     * Evaluate a piece of gear using pre-computed evidence.
     * This is the preferred entry point (used by Main and CSV generator).
     */
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

        // 4. Role Fit + Gear Score Rules
        if (best.slotCompatible() && best.coreStatCount() >= strongCore && best.mainStatPreferred()) {
            return keep("Strong slot/main-stat role fit", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= strongCore && score.score() >= config.keepScore()) {
            return keep("Strong slot role fit with high Gear Score", score, roles);
        }

        // 5. Stat Spike Rule
        if (score.hasSpike()
                && best.slotCompatible()
                && best.coreStatCount() >= reviewCore
                && (best.mainStatPreferred() || score.score() >= config.reviewScore())) {
            return keep("Spike with coherent role fit", score, roles);
        }

        // 6. Manual Review Fallbacks
        if (score.score() >= config.keepScore()) {
            return review("High Gear Score but incomplete role signal", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= reviewCore && best.mainStatPreferred()) {
            return review("One core stat with preferred main stat", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= 2) {
            return review("Multiple slot-preferred stats but incomplete main-stat fit", score, roles);
        }

        if (best.usefulStatCount() >= 2 && score.score() >= config.reviewScore()) {
            return review("Broad role fit with borderline Gear Score", score, roles);
        }

        if (score.score() >= config.reviewScore()) {
            return review("Borderline statistical quality", score, roles);
        }

        // 7. Delete Candidates
        if (!best.slotCompatible() && best.usefulStatCount() <= 2 && !score.hasSpike()) {
            return delete("Low score with no coherent role fit", score, roles);
        }

        return delete("Low-quality spread with weak role signal", score, roles);
    }

    // ---------- Helpers ----------

    private boolean isLeftSideSlot(String gearType) {
        if (gearType == null) return false;
        String g = gearType.trim().toLowerCase();
        return g.equals("weapon") || g.equals("helmet") || g.equals("helm") || g.equals("armor");
    }

    private Decision lowQualityDecision(GearScore score, RoleEvaluation roles) {
        if (score.score() >= config.reviewScore()) {
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
                .filter(s -> s != null && "Speed".equals(s.getType()))
                .anyMatch(s -> s.getValue() >= threshold);
    }
}
