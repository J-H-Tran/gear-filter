package com.e7gear.engine;

import com.e7gear.app.engine.Decision;
import com.e7gear.app.engine.DecisionEngine;
import com.e7gear.config.FilterConfig;
import com.e7gear.gear.Gear;
import com.e7gear.gear.MainStat;
import com.e7gear.gear.Quality;
import com.e7gear.gear.Substat;
import com.e7gear.app.role.Role;
import com.e7gear.app.role.RoleEvaluator;
import com.e7gear.app.scorer.GearScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DecisionEngineTest {

    private DecisionEngine engine;
    private GearScorer scorer;
    private FilterConfig config;

    @BeforeEach
    void setUp() {
        config = FilterConfig.defaults();
        scorer = new GearScorer();
        engine = new DecisionEngine(config, scorer);
    }

    @Test
    void modifiedGearWithSufficientScoreIsKept() {
        Gear gear = gear(
                sub("AttackPercent", 20, 3, true),
                sub("CriticalHitChancePercent", 15, 2, false),
                sub("CriticalHitDamagePercent", 25, 3, false),
                sub("Speed", 10, 2, false)
        );
        Decision decision = engine.decide(gear, scorer.score(gear), new RoleEvaluator(scorer, config).evaluate(gear));
        assertEquals(Quality.KEEP, decision.quality());
        assertTrue(decision.reason().contains("Modified substat"));
    }

    @Test
    void exceptionalSpeedIsKeptEvenWithoutThreeRollSpike() {
        Gear gear = gear(
                sub("Speed", 17, 2, false),
                sub("HealthPercent", 5, 1, false),
                sub("DefensePercent", 5, 1, false),
                sub("Attack", 10, 1, false)
        );
        Decision decision = engine.decide(gear, scorer.score(gear), new RoleEvaluator(scorer, config).evaluate(gear));
        assertEquals(Quality.KEEP, decision.quality());
        assertTrue(decision.reason().contains("Opener"));
    }

    @Test
    void strongThreeStatRoleFitIsKept() {
        Gear gear = new Gear();
        gear.setGear("Necklace");
        gear.setEnhance(15);
        MainStat main = new MainStat();
        main.setType("CriticalHitDamagePercent");
        main.setValue(70);
        gear.setMain(main);
        gear.setSubstats(List.of(
                sub("AttackPercent", 20, 3, false),
                sub("CriticalHitChancePercent", 15, 3, false),
                sub("CriticalHitDamagePercent", 10, 2, false),
                sub("DefensePercent", 5, 1, false)
        ));
        Decision decision = engine.decide(gear, scorer.score(gear), new RoleEvaluator(scorer, config).evaluate(gear));
        assertEquals(Quality.KEEP, decision.quality());
        assertEquals(Role.DPS, decision.roleEvaluation().bestRole());
    }

    @Test
    void spikeWithTwoUsefulStatsIsKept() {
        Gear gear = new Gear();
        gear.setGear("Boots");
        gear.setEnhance(15);
        MainStat main = new MainStat();
        main.setType("Speed");
        main.setValue(45);
        gear.setMain(main);
        gear.setSubstats(List.of(
                sub("Speed", 16, 4, false),
                sub("AttackPercent", 8, 1, false),
                sub("EffectResistancePercent", 5, 1, false),
                sub("Health", 100, 1, false)
        ));
        Decision decision = engine.decide(gear, scorer.score(gear), new RoleEvaluator(scorer, config).evaluate(gear));
        assertEquals(Quality.KEEP, decision.quality());
    }

    @Test
    void rightSidePenaltyLowersKeepThresholdForNecklaceWithPreferredMain() {
        FilterConfig customConfig = new FilterConfig(
                58.0, 66.0, 66.0, 2, 1, 15, 20.0, 17,
                Map.of(), // modGemMax (empty map of nested maps)
                Map.of(), // setMultipliers
                60.0, 100, true
        );
        DecisionEngine customEngine = new DecisionEngine(customConfig, scorer);
        RoleEvaluator evaluator = new RoleEvaluator(scorer, customConfig);

        Gear gear = new Gear();
        gear.setGear("Necklace");
        gear.setEnhance(15);
        MainStat main = new MainStat();
        main.setType("CriticalHitDamagePercent");
        main.setValue(70);
        gear.setMain(main);
        gear.setSubstats(List.of(
                sub("AttackPercent", 15, 2, false),
                sub("CriticalHitChancePercent", 10, 2, false),
                sub("CriticalHitDamagePercent", 5, 1, false),
                sub("Speed", 8, 2, false)
        ));
        // Score ~ 15 + 10*1.6 + 5*1.142 + 8*2 ≈ 15+16+5.71+16=52.71
        // With penalty, keep = 66-20=46 → 52.71 > 46 → KEEP
        Decision decision = customEngine.decide(gear, scorer.score(gear), evaluator.evaluate(gear));
        assertEquals(Quality.KEEP, decision.quality());
        assertTrue(decision.reason().contains("slot/main-stat") || decision.reason().contains("slot role fit"));
    }

    @Test
    void highScoreWithoutStrongRoleFitIsReviewNotAutomaticKeep() {
        Gear gear = new Gear();
        gear.setGear("Ring");
        gear.setEnhance(15);
        MainStat main = new MainStat();
        main.setType("DefensePercent");
        main.setValue(60);
        gear.setMain(main);
        gear.setSubstats(List.of(
                sub("AttackPercent", 25, 1, false),
                sub("EffectivenessPercent", 25, 1, false),
                sub("Defense", 174, 1, false),
                sub("Health", 174, 1, false)
        ));
        Decision decision = engine.decide(gear, scorer.score(gear), new RoleEvaluator(scorer, config).evaluate(gear));
        assertEquals(Quality.REVIEW, decision.quality());
    }

    @Test
    void mediocreTwoStatRoleFitIsReview() {
        Gear gear = new Gear();
        gear.setGear("Ring");
        gear.setEnhance(15);
        MainStat main = new MainStat();
        main.setType("DefensePercent");
        main.setValue(60);
        gear.setMain(main);
        gear.setSubstats(List.of(
                sub("AttackPercent", 30, 1, false),
                sub("CriticalHitChancePercent", 20, 1, false),
                sub("EffectResistancePercent", 5, 1, false),
                sub("Health", 50, 1, false)
        ));
        Decision decision = engine.decide(gear, scorer.score(gear), new RoleEvaluator(scorer, config).evaluate(gear));
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
        Decision decision = engine.decide(gear, scorer.score(gear), new RoleEvaluator(scorer, config).evaluate(gear));
        assertEquals(Quality.DELETE_CANDIDATE, decision.quality());
    }

    @Test
    void modGemPotentialDetectsSalvageablePiece() {
        // Nested map for modGemMax – only AttackPercent with level 85/88/90 values
        Map<String, Map<String, Integer>> modGemMax = Map.of(
                "AttackPercent", Map.of("85", 8, "88", 9, "90", 9)
        );
        FilterConfig lowKeepConfig = new FilterConfig(
                58.0, 60.0, 60.0, 2, 1, 15, 4.0, 17,
                modGemMax,  // now a nested map
                Map.of(),   // setMultipliers
                60.0, 100, true
        );
        DecisionEngine customEngine = new DecisionEngine(lowKeepConfig, scorer);
        RoleEvaluator evaluator = new RoleEvaluator(scorer, lowKeepConfig);

        Gear gear = new Gear();
        gear.setGear("Weapon");
        gear.setEnhance(15);
        gear.setLevel(85);
        gear.setSubstats(List.of(
                sub("CriticalHitChancePercent", 10, 2, false),
                sub("CriticalHitDamagePercent", 15, 2, false),
                sub("Speed", 10, 2, false),
                sub("Health", 100, 1, false)
        ));

        Decision decision = customEngine.decide(gear, scorer.score(gear), evaluator.evaluate(gear));
        assertEquals(Quality.KEEP_MOD_CANDIDATE, decision.quality());
        assertTrue(decision.reason().contains("Salvageable with mod"));
    }

    // Helper methods
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
