package com.e7gear.role;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Substat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleEvaluatorTest {

    private final RoleEvaluator evaluator = new RoleEvaluator();

    @Test
    void identifiesPureDpsGear() {
        Gear gear = gear(
                sub("AttackPercent", 10),
                sub("CriticalHitChancePercent", 10),
                sub("CriticalHitDamagePercent", 10),
                sub("Speed", 10)
        );

        RoleEvaluation result = evaluator.evaluate(gear);

        assertEquals(Role.DPS, result.bestRole());
        assertEquals(4, result.scoreFor(Role.DPS).usefulStatCount());
        assertTrue(result.scoreFor(Role.DPS).score() > 0);
    }

    @Test
    void identifiesBruiserGear() {
        Gear gear = gear(
                sub("HealthPercent", 20),
                sub("DefensePercent", 10),
                sub("CriticalHitChancePercent", 10),
                sub("CriticalHitDamagePercent", 10)
        );

        RoleEvaluation result = evaluator.evaluate(gear);

        assertEquals(Role.BRUISER, result.bestRole());
        assertEquals(4, result.scoreFor(Role.BRUISER).usefulStatCount());
    }

    @Test
    void identifiesSupportGear() {
        Gear gear = gear(
                sub("HealthPercent", 20),
                sub("DefensePercent", 15),
                sub("EffectResistancePercent", 20),
                sub("Speed", 10)
        );

        RoleEvaluation result = evaluator.evaluate(gear);

        assertEquals(Role.SUPPORT, result.bestRole());
        assertEquals(4, result.scoreFor(Role.SUPPORT).usefulStatCount());
    }

    @Test
    void identifiesDebufferGear() {
        Gear gear = gear(
                sub("HealthPercent", 20),
                sub("DefensePercent", 15),
                sub("EffectivenessPercent", 20),
                sub("Speed", 10)
        );

        RoleEvaluation result = evaluator.evaluate(gear);

        assertEquals(Role.DEBUFFER, result.bestRole());
        assertEquals(4, result.scoreFor(Role.DEBUFFER).usefulStatCount());
    }

    @Test
    void speedContributesToEveryRoleThatUsesIt() {
        Gear gear = gear(sub("Speed", 10));

        RoleEvaluation result = evaluator.evaluate(gear);

        assertTrue(result.scoreFor(Role.DPS).score() > 0);
        assertTrue(result.scoreFor(Role.BRUISER).score() > 0);
        assertTrue(result.scoreFor(Role.SUPPORT).score() > 0);
        assertTrue(result.scoreFor(Role.DEBUFFER).score() > 0);
        assertEquals(1, result.scoreFor(Role.DPS).usefulStatCount());
        assertEquals(1, result.scoreFor(Role.BRUISER).usefulStatCount());
        assertEquals(1, result.scoreFor(Role.SUPPORT).usefulStatCount());
        assertEquals(1, result.scoreFor(Role.DEBUFFER).usefulStatCount());
    }

    @Test
    void irrelevantStatsDoNotContributeToRole() {
        Gear gear = gear(
                sub("Attack", 100),
                sub("Defense", 100),
                sub("Health", 100),
                sub("EffectivenessPercent", 20)
        );

        RoleEvaluation result = evaluator.evaluate(gear);

        assertEquals(0.0, result.scoreFor(Role.DPS).score());
        assertEquals(0.0, result.scoreFor(Role.BRUISER).score());
        assertEquals(0.0, result.scoreFor(Role.SUPPORT).score());
        assertEquals(20.0, result.scoreFor(Role.DEBUFFER).score());
        assertEquals(Role.DEBUFFER, result.bestRole());
    }

    @Test
    void emptyGearHasNoRole() {
        Gear gear = gear();

        RoleEvaluation result = evaluator.evaluate(gear);

        assertEquals(Role.NONE, result.bestRole());
        for (Role role : List.of(Role.DPS, Role.BRUISER, Role.SUPPORT, Role.DEBUFFER)) {
            assertEquals(0.0, result.scoreFor(role).score());
            assertEquals(0, result.scoreFor(role).usefulStatCount());
        }
    }

    @Test
    void roleScoresUseSameStatNormalizationAsGearScorer() {
        Gear gear = gear(
                sub("Speed", 10),
                sub("AttackPercent", 10)
        );

        RoleEvaluation result = evaluator.evaluate(gear);

        double expectedDps = 10 * 8.0 / 4.0 + 10;
        assertEquals(expectedDps, result.scoreFor(Role.DPS).score(), 1e-9);
    }

    @Test
    void roleEvaluatorDoesNotTreatThreeStatsAsAnAutomaticDecision() {
        Gear gear = gear(
                sub("AttackPercent", 1),
                sub("CriticalHitChancePercent", 1),
                sub("CriticalHitDamagePercent", 1)
        );

        RoleEvaluation result = evaluator.evaluate(gear);

        assertEquals(Role.DPS, result.bestRole());
        assertEquals(3, result.scoreFor(Role.DPS).usefulStatCount());

        // No KEEP/DELETE property exists in RoleEvaluator. It only reports
        // suitability; DecisionEngine will make the eventual decision.
        assertTrue(result.scoreFor(Role.DPS).score() > 0);
    }

    private static Gear gear(Substat... substats) {
        Gear gear = new Gear();
        gear.setEnhance(15);
        gear.setSubstats(List.of(substats));
        return gear;
    }

    private static Substat sub(String type, double value) {
        Substat substat = new Substat();
        substat.setType(type);
        substat.setValue(value);
        substat.setRolls(1);
        return substat;
    }
}
