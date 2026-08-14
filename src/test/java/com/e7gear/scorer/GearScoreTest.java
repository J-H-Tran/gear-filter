package com.e7gear.scorer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GearScoreTest {

    @Test
    void recordStoresAllScoringAndMetadataValues() {
        GearScore score = new GearScore(
                42.5,
                31.0,
                22.0,
                35.0,
                4,
                7,
                true,
                true
        );

        assertEquals(42.5, score.score());
        assertEquals(31.0, score.dScore());
        assertEquals(22.0, score.sScore());
        assertEquals(35.0, score.cScore());
        assertEquals(4, score.maxEnhancementRolls());
        assertEquals(7, score.totalEnhancementRolls());
        assertTrue(score.hasSpike());
        assertTrue(score.hasModified());
    }

    @Test
    void recordHasValueSemantics() {
        GearScore first = new GearScore(1, 2, 3, 4, 1, 2, false, false);
        GearScore second = new GearScore(1, 2, 3, 4, 1, 2, false, false);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
