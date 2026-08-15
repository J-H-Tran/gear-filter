package com.e7gear.app.scorer;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Substat;
import com.e7gear.stats.StatType;

public final class GearScorer {

    public GearScore score(Gear gear) {
        if (gear == null || gear.getSubstats() == null || gear.getSubstats().isEmpty()) {
            return new GearScore(0.0, 0.0, 0.0, 0.0, 0, 0, false, false);
        }

        double score = 0.0;
        double dScore = 0.0;
        double sScore = 0.0;
        double cScore = 0.0;

        int maxEnhancementRolls = 0;
        int totalEnhancementRolls = 0;
        boolean hasModified = false;

        for (Substat substat : gear.getSubstats()) {
            if (substat == null) continue;

            int enhancementRolls = Math.max(0, substat.getRolls() - 1);
            totalEnhancementRolls += enhancementRolls;
            maxEnhancementRolls = Math.max(maxEnhancementRolls, enhancementRolls);
            hasModified |= substat.isModified();

            StatType statType = StatType.fromString(substat.getType());
            if (statType == null) continue;  // unknown stat (should not happen)

            double statScore = calculateStatScore(statType, substat.getValue());
            score += statScore;

            if (isDpsStat(statType)) dScore += statScore;
            if (isSupportStat(statType)) sScore += statScore;
            if (isCombatStat(statType)) cScore += statScore;
        }

        return new GearScore(score, dScore, sScore, cScore,
                maxEnhancementRolls, totalEnhancementRolls,
                maxEnhancementRolls >= 3,
                hasModified);
    }

    public double calculateStatScore(StatType statType, double value) {
        if (statType == null) return 0.0;
        return value * statType.weight();
    }

    // Helper methods using StatType
    private boolean isDpsStat(StatType stat) {
        return stat == StatType.ATTACK_PERCENT
                || stat == StatType.CRIT_CHANCE
                || stat == StatType.CRIT_DAMAGE
                || stat == StatType.SPEED;
    }

    private boolean isSupportStat(StatType stat) {
        return stat == StatType.HEALTH_PERCENT
                || stat == StatType.DEFENSE_PERCENT
                || stat == StatType.EFFECT_RESISTANCE
                || stat == StatType.SPEED;
    }

    private boolean isCombatStat(StatType stat) {
        return stat != StatType.EFFECTIVENESS
                && stat != StatType.EFFECT_RESISTANCE
                && isSupportedStat(stat);
    }

    private boolean isSupportedStat(StatType stat) {
        return stat != null && (stat.isPercentage() || stat.isFlat() || stat == StatType.SPEED);
    }
}
