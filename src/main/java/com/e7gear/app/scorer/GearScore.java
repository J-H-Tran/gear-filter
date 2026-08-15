package com.e7gear.app.scorer;

/**
 * Statistical score produced by GearScorer.
 *
 * GearScore intentionally contains no role-specific or keep/delete decisions.
 * Those responsibilities belong to RoleEvaluator and DecisionEngine.
 */
public record GearScore(
        double score,
        double dScore,
        double sScore,
        double cScore,
        int maxEnhancementRolls,
        int totalEnhancementRolls,
        boolean hasSpike,
        boolean hasModified
) {
}
