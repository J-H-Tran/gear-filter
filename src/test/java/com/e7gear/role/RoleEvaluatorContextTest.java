package com.e7gear.role;

import com.e7gear.gear.Gear;
import com.e7gear.gear.MainStat;
import com.e7gear.gear.Substat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleEvaluatorContextTest {
    private final RoleEvaluator evaluator = new RoleEvaluator();

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
