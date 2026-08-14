package com.e7gear.scorer;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Substat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearScorerTest {

    private final GearScorer scorer = new GearScorer();

    @Test
    void scoresEverySupportedStatUsingGearScoreFormula() {
        Gear gear = gear(
                sub("AttackPercent", 10, 1, false),
                sub("DefensePercent", 10, 1, false),
                sub("HealthPercent", 10, 1, false),
                sub("EffectivenessPercent", 10, 1, false),
                sub("EffectResistancePercent", 10, 1, false),
                sub("Speed", 10, 1, false),
                sub("CriticalHitDamagePercent", 10, 1, false),
                sub("CriticalHitChancePercent", 10, 1, false),
                sub("Attack", 39, 1, false),
                sub("Defense", 31, 1, false),
                sub("Health", 174, 1, false)
        );

        GearScore result = scorer.score(gear);

        double expected =
                10 + 10 + 10 + 10 + 10
                + 10 * 8.0 / 4.0
                + 10 * 8.0 / 7.0
                + 10 * 8.0 / 5.0
                + 39 * 3.46 / 39.0
                + 31 * 4.99 / 31.0
                + 174 * 3.09 / 174.0;

        assertEquals(expected, result.score(), 1e-9);
    }

    @Test
    void dScoreContainsOnlyDpsStats() {
        Gear gear = gear(
                sub("AttackPercent", 10, 1, false),
                sub("CriticalHitChancePercent", 5, 1, false),
                sub("CriticalHitDamagePercent", 7, 1, false),
                sub("Speed", 8, 1, false),
                sub("HealthPercent", 20, 1, false),
                sub("EffectivenessPercent", 20, 1, false)
        );

        GearScore result = scorer.score(gear);

        double expected = 10 + 5 * 8.0 / 5.0 + 7 * 8.0 / 7.0 + 8 * 8.0 / 4.0;
        assertEquals(expected, result.dScore(), 1e-9);
    }

    @Test
    void sScoreContainsOnlySupportStats() {
        Gear gear = gear(
                sub("HealthPercent", 20, 1, false),
                sub("DefensePercent", 10, 1, false),
                sub("EffectResistancePercent", 30, 1, false),
                sub("Speed", 8, 1, false),
                sub("AttackPercent", 20, 1, false),
                sub("EffectivenessPercent", 30, 1, false)
        );

        GearScore result = scorer.score(gear);

        double expected = 20 + 10 + 30 + 8 * 8.0 / 4.0;
        assertEquals(expected, result.sScore(), 1e-9);
    }

    @Test
    void cScoreExcludesEffectivenessAndEffectResistance() {
        Gear gear = gear(
                sub("AttackPercent", 10, 1, false),
                sub("DefensePercent", 10, 1, false),
                sub("HealthPercent", 10, 1, false),
                sub("Speed", 8, 1, false),
                sub("CriticalHitChancePercent", 5, 1, false),
                sub("CriticalHitDamagePercent", 7, 1, false),
                sub("Attack", 39, 1, false),
                sub("EffectivenessPercent", 50, 1, false),
                sub("EffectResistancePercent", 50, 1, false)
        );

        GearScore result = scorer.score(gear);

        double expected =
                10 + 10 + 10
                + 8 * 8.0 / 4.0
                + 5 * 8.0 / 5.0
                + 7 * 8.0 / 7.0
                + 39 * 3.46 / 39.0;

        assertEquals(expected, result.cScore(), 1e-9);
    }

    @Test
    void calculatesEnhancementRollMetadata() {
        Gear gear = gear(
                sub("AttackPercent", 10, 1, false),
                sub("Speed", 15, 4, false),
                sub("CriticalHitChancePercent", 12, 3, false)
        );

        GearScore result = scorer.score(gear);

        // rolls includes the initial substat, so enhancement rolls = rolls - 1.
        assertEquals(3, result.maxEnhancementRolls());
        assertEquals(3 + 2, result.totalEnhancementRolls());
        assertTrue(result.hasSpike());
    }

    @Test
    void supportsFiveRollStatWithoutClampingItToThree() {
        Gear gear = gear(
                sub("Speed", 18, 5, false)
        );

        GearScore result = scorer.score(gear);

        assertEquals(4, result.maxEnhancementRolls());
        assertEquals(4, result.totalEnhancementRolls());
        assertTrue(result.hasSpike());
    }

    @Test
    void detectsModifiedSubstat() {
        Gear gear = gear(
                sub("AttackPercent", 10, 2, true)
        );

        GearScore result = scorer.score(gear);

        assertTrue(result.hasModified());
    }

    @Test
    void nullGearProducesZeroScore() {
        GearScore result = scorer.score(null);

        assertEquals(0.0, result.score());
        assertEquals(0.0, result.dScore());
        assertEquals(0.0, result.sScore());
        assertEquals(0.0, result.cScore());
        assertEquals(0, result.maxEnhancementRolls());
        assertEquals(0, result.totalEnhancementRolls());
        assertFalse(result.hasSpike());
        assertFalse(result.hasModified());
    }

    @Test
    void emptySubstatsProduceZeroScore() {
        Gear gear = gear();

        GearScore result = scorer.score(gear);

        assertEquals(0.0, result.score());
        assertEquals(0.0, result.dScore());
        assertEquals(0.0, result.sScore());
        assertEquals(0.0, result.cScore());
    }

    @Test
    void unknownStatDoesNotAffectScore() {
        Gear gear = gear(
                sub("NotARealStat", 999, 5, false),
                sub("AttackPercent", 10, 1, false)
        );

        GearScore result = scorer.score(gear);

        assertEquals(10.0, result.score(), 1e-9);
    }

    private static Gear gear(Substat... substats) {
        Gear gear = new Gear();
        gear.setEnhance(15);
        gear.setSubstats(List.of(substats));
        return gear;
    }

    private static Substat sub(String type, double value, int rolls, boolean modified) {
        Substat substat = new Substat();
        substat.setType(type);
        substat.setValue(value);
        substat.setRolls(rolls);
        substat.setModified(modified);
        return substat;
    }
}
