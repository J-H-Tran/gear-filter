package com.e7gear;

import com.e7gear.gear.Gear;
import com.e7gear.gear.MainStat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GearTest {

    @Test
    void mainStatConvenienceAccessorsDelegateToMainStat() {
        Gear gear = new Gear();
        MainStat main = new MainStat();
        main.setType("CriticalHitDamagePercent");
        main.setValue(65.0);
        gear.setMain(main);

        assertEquals("CriticalHitDamagePercent", gear.getMainStatType());
        assertEquals(65.0, gear.getMainStatValue());
    }

    @Test
    void mainStatConvenienceAccessorsHandleMissingMainStat() {
        Gear gear = new Gear();

        assertNull(gear.getMainStatType());
        assertEquals(0.0, gear.getMainStatValue());
    }
}
