package com.e7gear.engine;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Quality;
import com.e7gear.gear.Substat;
import com.e7gear.role.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DecisionEngineTest {
    private final DecisionEngine engine = new DecisionEngine();

    @Test
    void modifiedGearIsAlwaysKept() {
        Gear gear = gear(
                sub("AttackPercent", 5, 1, true),
                sub("DefensePercent", 5, 1, false),
                sub("HealthPercent", 5, 1, false),
                sub("Speed", 2, 1, false)
        );

        Decision decision = engine.decide(gear);

        assertEquals(Quality.KEEP, decision.quality());
        assertEquals("Modified substat (safe)", decision.reason());
    }

    @Test
    void exceptionalSpeedIsKeptEvenWithoutThreeRollSpike() {
        Gear gear = gear(
                sub("Speed", 15, 2, false),
                sub("HealthPercent", 5, 1, false),
                sub("DefensePercent", 5, 1, false),
                sub("Attack", 10, 1, false)
        );

        Decision decision = engine.decide(gear);

        assertEquals(Quality.KEEP, decision.quality());
    }

    @Test
    void strongThreeStatRoleFitIsKept() {
        Gear gear = gear(
                sub("AttackPercent", 8, 1, false),
                sub("CriticalHitChancePercent", 8, 1, false),
                sub("CriticalHitDamagePercent", 8, 1, false),
                sub("DefensePercent", 3, 1, false)
        );

        Decision decision = engine.decide(gear);

        assertEquals(Quality.KEEP, decision.quality());
        assertEquals(Role.DPS, decision.roleEvaluation().bestRole());
    }

    @Test
    void spikeWithTwoUsefulStatsIsKept() {
        Gear gear = gear(
                sub("Speed", 12, 4, false),
                sub("AttackPercent", 12, 2, false),
                sub("EffectResistancePercent", 5, 1, false),
                sub("Health", 100, 1, false)
        );

        Decision decision = engine.decide(gear);

        assertEquals(Quality.KEEP, decision.quality());
    }

    @Test
    void highScoreWithoutStrongRoleFitIsReviewNotAutomaticKeep() {
        Gear gear = gear(
                sub("AttackPercent", 25, 1, false),
                sub("EffectivenessPercent", 25, 1, false),
                sub("Defense", 174, 1, false),
                sub("Health", 174, 1, false)
        );

        Decision decision = engine.decide(gear);

        assertEquals(Quality.REVIEW, decision.quality());
    }

    @Test
    void mediocreTwoStatRoleFitIsReview() {
        Gear gear = gear(
                sub("AttackPercent", 10, 1, false),
                sub("CriticalHitChancePercent", 10, 1, false),
                sub("EffectResistancePercent", 5, 1, false),
                sub("Health", 50, 1, false)
        );

        Decision decision = engine.decide(gear);

        assertEquals(Quality.REVIEW, decision.quality());
    }

    @Test
    void lowScoreWithNoRoleFitIsDeleteCandidate() {
        Gear gear = gear(
                sub("Attack", 10, 1, false),
                sub("Defense", 10, 1, false),
                sub("Health", 50, 1, false),
                sub("EffectivenessPercent", 5, 1, false)
        );

        Decision decision = engine.decide(gear);

        assertEquals(Quality.DELETE_CANDIDATE, decision.quality());
    }

    private static Gear gear(Substat... subs) {
        Gear gear = new Gear();
        gear.setGear("Weapon");
        gear.setEnhance(15);
        gear.setSubstats(List.of(subs));
        return gear;
    }

    private static Substat sub(String type, double value, int rolls, boolean modified) {
        Substat s = new Substat();
        s.setType(type);
        s.setValue(value);
        s.setRolls(rolls);
        s.setModified(modified);
        return s;
    }
}
