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
    public static final int DEFAULT_STRONG_ROLE_STATS = 3;
    public static final int DEFAULT_SPIKE_ROLLS = 3;
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

        if (gear == null) {
            return new Decision(Quality.DELETE_CANDIDATE, "Null gear", score, roles);
        }

        if (score.hasModified()) {
            return new Decision(Quality.KEEP, "Modified substat (safe)", score, roles);
        }

        if (hasExceptionalSpeed(gear)) {
            return new Decision(Quality.KEEP, "Exceptional Speed safety rule", score, roles);
        }

        RoleScore best = roles.bestRole() == Role.NONE ? null : roles.scoreFor(roles.bestRole());

        // Strong role fit is enough to keep even when global score is mediocre.
        // The guide explicitly favors pieces with multiple favorable substats.
        if (best != null && best.usefulStatCount() >= DEFAULT_STRONG_ROLE_STATS) {
            return new Decision(Quality.KEEP, "Strong multi-stat role fit", score, roles);
        }

        // A spike is evidence of concentrated enhancement quality, but it is
        // not sufficient by itself. Require at least some role applicability.
        if (score.maxEnhancementRolls() >= DEFAULT_SPIKE_ROLLS
                && best != null
                && best.usefulStatCount() >= 2) {
            return new Decision(Quality.KEEP, "Spike with viable role fit", score, roles);
        }

        // High statistical score gets review rather than unconditional keep;
        // role/main-stat context still matters for final inventory decisions.
        if (score.score() >= keepScore) {
            return new Decision(Quality.REVIEW, "High Gear Score but no strong role signal", score, roles);
        }

        if (best != null && best.usefulStatCount() >= 2) {
            return new Decision(Quality.REVIEW, "Viable role fit but below keep threshold", score, roles);
        }

        if (score.score() >= reviewScore) {
            return new Decision(Quality.REVIEW, "Borderline statistical quality", score, roles);
        }

        // DELETE_CANDIDATE is deliberately narrow: no modified stat, no
        // exceptional Speed, no multi-stat role fit, no spike+role signal,
        // and low statistical score.
        return new Decision(Quality.DELETE_CANDIDATE, "Low score with no meaningful role fit", score, roles);
    }

    private boolean hasExceptionalSpeed(Gear gear) {
        if (gear.getSubstats() == null) return false;
        return gear.getSubstats().stream()
                .filter(s -> s != null && "Speed".equals(s.getType()))
                .anyMatch(s -> s.getValue() >= DEFAULT_HIGH_SPEED);
    }
}
