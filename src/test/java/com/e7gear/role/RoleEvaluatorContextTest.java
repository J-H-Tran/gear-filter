package com.e7gear.role;

import com.e7gear.app.role.Role;
import com.e7gear.app.role.RoleEvaluator;
import com.e7gear.app.role.RoleScore;
import com.e7gear.config.FilterConfig;
import com.e7gear.gear.Gear;
import com.e7gear.gear.MainStat;
import com.e7gear.gear.Substat;
import com.e7gear.app.scorer.GearScorer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoleEvaluatorContextTest {

    private RoleEvaluator evaluator;

    @BeforeEach
    void setUp() {
        FilterConfig config = FilterConfig.defaults();
        evaluator = new RoleEvaluator(new GearScorer(), config);
    }

    @Test
    void dpsNecklaceUsesDpsAccessoryRecommendations() {
        Gear gear = gear("Necklace", "CriticalHitDamagePercent",
                sub("AttackPercent", 16),
                sub("CriticalHitChancePercent", 12),
                sub("CriticalHitDamagePercent", 20),
                sub("Speed", 8));

        RoleScore dps = evaluator.evaluate(gear).scoreFor(Role.DPS);

        assertEquals(4, dps.usefulStatCount());
        assertEquals(3, dps.slotPreferredStatCount());
        assertTrue(dps.mainStatPreferred());
        assertTrue(dps.viable());
    }

    @Test
    void supportRingRecognizesEffectResistanceAsSlotPreferred() {
        Gear gear = gear("Ring", "EffectResistancePercent",
                sub("HealthPercent", 20),
                sub("DefensePercent", 15),
                sub("EffectResistancePercent", 20),
                sub("Speed", 8));

        RoleScore support = evaluator.evaluate(gear).scoreFor(Role.SUPPORT);

        assertEquals(4, support.usefulStatCount());
        assertEquals(3, support.slotPreferredStatCount());
        assertTrue(support.mainStatPreferred());
    }

    @Test
    void debufferRingPrefersEffectivenessAndHealth() {
        Gear gear = gear("Ring", "EffectivenessPercent",
                sub("EffectivenessPercent", 30),
                sub("HealthPercent", 15),
                sub("DefensePercent", 10),
                sub("Speed", 8));

        RoleScore debuffer = evaluator.evaluate(gear).scoreFor(Role.DEBUFFER);

        assertEquals(4, debuffer.usefulStatCount());
        assertEquals(2, debuffer.slotPreferredStatCount());
        assertTrue(debuffer.mainStatPreferred());
    }

    @Test
    void bruiserBootsRecognizeHpAndSpeedAsPreferred() {
        Gear gear = gear("Boots", "HealthPercent",
                sub("HealthPercent", 20),
                sub("DefensePercent", 15),
                sub("CriticalHitChancePercent", 12),
                sub("Speed", 10));

        RoleScore bruiser = evaluator.evaluate(gear).scoreFor(Role.BRUISER);

        assertEquals(4, bruiser.usefulStatCount());
        assertEquals(3, bruiser.slotPreferredStatCount());
        assertTrue(bruiser.mainStatPreferred());
    }

    @Test
    void nonAccessoryGearStillUsesRoleSubstatsWithoutInventingSlotRules() {
        Gear gear = gear("Weapon", "Attack",
                sub("AttackPercent", 16),
                sub("CriticalHitChancePercent", 12),
                sub("CriticalHitDamagePercent", 20),
                sub("Speed", 8));

        RoleScore dps = evaluator.evaluate(gear).scoreFor(Role.DPS);

        assertEquals(4, dps.usefulStatCount());
        assertEquals(0, dps.slotPreferredStatCount());
        assertFalse(dps.mainStatPreferred());
        assertTrue(dps.viable());
    }

    @Test
    void setMultiplierAffectsRoleScore() {
        // Create gear with Speed set and DPS stats.
        Gear gear = gear("Weapon", "Attack",
                sub("AttackPercent", 10),
                sub("Speed", 10));
        gear.setSet("SpeedSet");

        // Without multiplier, raw DPS score = 10 + 10*2 = 30.
        // With multiplier 1.3, adjusted = 39.
        RoleScore dps = evaluator.evaluate(gear).scoreFor(Role.DPS);
        // Since config has SpeedSet multiplier 1.3, score should be 39.
        assertEquals(39.0, dps.score(), 1e-9);
    }

    // Helper methods
    private static Gear gear(String slot, String mainType, Substat... subs) {
        Gear gear = new Gear();
        gear.setGear(slot);
        MainStat main = new MainStat();
        main.setType(mainType);
        main.setValue(0);
        gear.setMain(main);
        gear.setSubstats(List.of(subs));
        gear.setEnhance(15);
        return gear;
    }

    private static Substat sub(String type, double value) {
        Substat s = new Substat();
        s.setType(type);
        s.setValue(value);
        s.setRolls(1);
        return s;
    }
}
