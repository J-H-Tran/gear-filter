package com.e7gear.engine;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Quality;
import com.e7gear.role.Role;
import com.e7gear.role.RoleEvaluation;
import com.e7gear.role.RoleEvaluator;
import com.e7gear.role.RoleScore;
import com.e7gear.scorer.GearScore;
import com.e7gear.scorer.GearScorer;

/**
 * Conservative final classifier. Thresholds are policy defaults, not claims
 * made by the source guide; keep them configurable as the inventory is
 * validated against real-world results.
 */
public final class DecisionEngine {

    public static final double DEFAULT_REVIEW_SCORE = 58.0;
    public static final double DEFAULT_KEEP_SCORE = 66.0;
    public static final int DEFAULT_STRONG_CORE_STATS = 2;
    public static final int DEFAULT_REVIEW_CORE_STATS = 1;
    public static final int DEFAULT_HIGH_SPEED = 15;

    private final double reviewScore;
    private final double keepScore;

    public DecisionEngine() {
        this(DEFAULT_REVIEW_SCORE, DEFAULT_KEEP_SCORE);
    }

    public DecisionEngine(double reviewScore, double keepScore) {
        if (reviewScore < 0 || keepScore < reviewScore) {
            throw new IllegalArgumentException("Invalid score thresholds");
        }
        this.reviewScore = reviewScore;
        this.keepScore = keepScore;
    }

    public Decision decide(Gear gear) {
        GearScorer scorer = new GearScorer();
        GearScore score = scorer.score(gear);
        RoleEvaluation roles = new RoleEvaluator(scorer).evaluate(gear);
        return decide(gear, score, roles);
    }

    /**
     * Evaluate using already-computed evidence. This is the preferred path
     * for Main and CSV generation so each component is executed exactly once.
     */
    public Decision decide(Gear gear, GearScore score, RoleEvaluation roles) {
        if (gear == null) {
            return new Decision(Quality.DELETE_CANDIDATE, "Null gear", score, roles);
        }

        if (score == null || roles == null) {
            throw new IllegalArgumentException("score and roles must not be null");
        }

        // 1. Exceptional Speed Safety Rule
        if (hasExceptionalSpeed(gear)) {
            return keep("Exceptional Speed safety rule", score, roles);
        }

        // 2. Refined Modified Substat Rule (Requires minimum GS threshold)
        if (score.hasModified() && score.score() >= reviewScore) {
            return keep("Modified substat with acceptable Gear Score", score, roles);
        }

        // 3. Left-side Gear Fix (Weapon, Helmet, Armor don't have main stat variations)
        boolean isLeftSide = isLeftSideSlot(gear.getGear());
        if (isLeftSide && score.score() >= keepScore) {
            return keep("High Gear Score on left-side slot", score, roles);
        }

        RoleScore best = bestRole(roles);
        if (best == null) {
            return lowQualityDecision(score, roles);
        }

        // 4. Role Fit + Gear Score Rules
        if (best.slotCompatible() && best.coreStatCount() >= DEFAULT_STRONG_CORE_STATS && best.mainStatPreferred()) {
            return keep("Strong slot/main-stat role fit", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= DEFAULT_STRONG_CORE_STATS && score.score() >= keepScore) {
            return keep("Strong slot role fit with high Gear Score", score, roles);
        }

        // 5. Stat Spike Rule
        if (score.hasSpike() && best.slotCompatible() && best.coreStatCount() >= DEFAULT_REVIEW_CORE_STATS && (best.mainStatPreferred() || score.score() >= reviewScore)) {
            return keep("Spike with coherent role fit", score, roles);
        }

        // 6. Manual Review Fallbacks
        if (score.score() >= keepScore) {
            return review("High Gear Score but incomplete role signal", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= DEFAULT_REVIEW_CORE_STATS && best.mainStatPreferred()) {
            return review("One core stat with preferred main stat", score, roles);
        }

        if (best.slotCompatible() && best.coreStatCount() >= 2) {
            return review("Multiple slot-preferred stats but incomplete main-stat fit", score, roles);
        }

        if (best.usefulStatCount() >= 2 && score.score() >= reviewScore) {
            return review("Broad role fit with borderline Gear Score", score, roles);
        }

        if (score.score() >= reviewScore) {
            return review("Borderline statistical quality", score, roles);
        }

        // 7. Delete Candidates
        if (!best.slotCompatible() && best.usefulStatCount() <= 2 && !score.hasSpike()) {
            return delete("Low score with no coherent role fit", score, roles);
        }

        return delete("Low-quality spread with weak role signal", score, roles);
    }

    private boolean isLeftSideSlot(String gearType) {
        if (gearType == null) return false;
        String g = gearType.trim().toLowerCase();
        return g.equals("weapon") || g.equals("helmet") || g.equals("helm") || g.equals("armor");
    }

    private Decision lowQualityDecision(GearScore score, RoleEvaluation roles) {
        if (score.score() >= reviewScore) {
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
        return gear.getSubstats().stream()
                .filter(s -> s != null && "Speed".equals(s.getType()))
                .anyMatch(s -> s.getValue() >= DEFAULT_HIGH_SPEED);
    }
}
