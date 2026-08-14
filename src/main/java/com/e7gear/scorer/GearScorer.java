package com.e7gear.scorer;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Substat;

/**
 * Calculates intrinsic/statistical equipment quality.
 *
 * The formulas are based on gear-score.txt:
 *
 * Score = Attack%
 *       + Defense%
 *       + HP%
 *       + Effectiveness
 *       + Effect Resistance
 *       + Speed * (8/4)
 *       + Crit Damage * (8/7)
 *       + Crit Chance * (8/5)
 *       + Flat Attack * 3.46 / 39
 *       + Flat Defense * 4.99 / 31
 *       + Flat HP * 3.09 / 174
 *
 * dScore = Score formula restricted to ATK%, Crit Chance, Crit Damage, Speed
 * sScore = Score formula restricted to HP%, DEF%, Effect Resistance, Speed
 * cScore = Score formula excluding Effectiveness and Effect Resistance
 *
 * This class does not decide whether gear should be kept or deleted.
 */
public final class GearScorer {

    // Values are intentionally expressed as the constants from gear-score.txt
    // rather than being derived from the current inventory.
    private static final double SPEED_WEIGHT = 8.0 / 4.0;
    private static final double CRIT_DAMAGE_WEIGHT = 8.0 / 7.0;
    private static final double CRIT_CHANCE_WEIGHT = 8.0 / 5.0;

    private static final double FLAT_ATTACK_WEIGHT = 3.46 / 39.0;
    private static final double FLAT_DEFENSE_WEIGHT = 4.99 / 31.0;
    private static final double FLAT_HEALTH_WEIGHT = 3.09 / 174.0;

    /**
     * Scores a piece of gear.
     *
     * Null/empty substats produce a zero statistical score and zero roll
     * metadata. The scorer does not impose an enhancement-level requirement;
     * filtering to +15 belongs to the caller/analysis pipeline.
     */
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
            if (substat == null) {
                continue;
            }

            int enhancementRolls = Math.max(0, substat.getRolls() - 1);
            totalEnhancementRolls += enhancementRolls;
            maxEnhancementRolls = Math.max(maxEnhancementRolls, enhancementRolls);

            hasModified |= substat.isModified();

            double statScore = calculateStatScore(substat.getType(), substat.getValue());
            score += statScore;

            if (isDpsStat(substat.getType())) {
                dScore += statScore;
            }

            if (isSupportStat(substat.getType())) {
                sScore += statScore;
            }

            if (isCombatStat(substat.getType())) {
                cScore += statScore;
            }
        }

        return new GearScore(
                score,
                dScore,
                sScore,
                cScore,
                maxEnhancementRolls,
                totalEnhancementRolls,
                maxEnhancementRolls >= 3,
                hasModified
        );
    }

    /**
     * Calculates the contribution of one substat according to gear-score.txt.
     */
    public double calculateStatScore(String type, double value) {
        if (type == null) {
            return 0.0;
        }

        return switch (type) {
            case "AttackPercent",
                 "DefensePercent",
                 "HealthPercent",
                 "EffectivenessPercent",
                 "EffectResistancePercent" -> value;

            case "Speed" -> value * SPEED_WEIGHT;

            case "CriticalHitDamagePercent" -> value * CRIT_DAMAGE_WEIGHT;

            case "CriticalHitChancePercent" -> value * CRIT_CHANCE_WEIGHT;

            case "Attack" -> value * FLAT_ATTACK_WEIGHT;

            case "Defense" -> value * FLAT_DEFENSE_WEIGHT;

            case "Health" -> value * FLAT_HEALTH_WEIGHT;

            default -> 0.0;
        };
    }

    private boolean isDpsStat(String type) {
        return "AttackPercent".equals(type)
                || "CriticalHitChancePercent".equals(type)
                || "CriticalHitDamagePercent".equals(type)
                || "Speed".equals(type);
    }

    private boolean isSupportStat(String type) {
        return "HealthPercent".equals(type)
                || "DefensePercent".equals(type)
                || "EffectResistancePercent".equals(type)
                || "Speed".equals(type);
    }

    /**
     * Combat Score is the complete Score formula excluding Effectiveness
     * and Effect Resistance.
     *
     * This includes flat stats, matching the source Score formula.
     */
    private boolean isCombatStat(String type) {
        return !"EffectivenessPercent".equals(type)
                && !"EffectResistancePercent".equals(type)
                && isSupportedStat(type);
    }

    private boolean isSupportedStat(String type) {
        return "AttackPercent".equals(type)
                || "DefensePercent".equals(type)
                || "HealthPercent".equals(type)
                || "Speed".equals(type)
                || "CriticalHitDamagePercent".equals(type)
                || "CriticalHitChancePercent".equals(type)
                || "Attack".equals(type)
                || "Defense".equals(type)
                || "Health".equals(type);
    }
}
