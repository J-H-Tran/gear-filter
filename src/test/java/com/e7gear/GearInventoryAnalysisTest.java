package com.e7gear;

import com.e7gear.config.FilterConfig;
import com.e7gear.app.engine.Decision;
import com.e7gear.app.engine.DecisionEngine;
import com.e7gear.gear.Gear;
import com.e7gear.gear.GearInventory;
import com.e7gear.gear.Quality;
import com.e7gear.app.role.RoleEvaluation;
import com.e7gear.app.role.RoleEvaluator;
import com.e7gear.app.scorer.GearScore;
import com.e7gear.app.scorer.GearScorer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class GearInventoryAnalysisTest {

    @Test
    void deserializesRealGearInventory() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GearInventory inventory = mapper.readValue(
                Path.of("gear.txt").toFile(),
                GearInventory.class
        );

        assertNotNull(inventory);
        assertNotNull(inventory.getItems());
        assertFalse(inventory.getItems().isEmpty());

        Gear gear = inventory.getItems().get(0);

        assertNotNull(gear.getGear());
        assertNotNull(gear.getSet());
        assertNotNull(gear.getRank());
        assertNotNull(gear.getMain());
        assertNotNull(gear.getSubstats());

        assertEquals(15, gear.getEnhance());
        assertEquals("Helmet", gear.getGear());
        assertEquals("AttackSet", gear.getSet());
        assertEquals("Heroic", gear.getRank());
        assertEquals("Health", gear.getMain().getType());
        assertEquals(2835, gear.getMain().getValue());
    }

    @Test
    void analyzeRealInventory() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GearInventory inventory = mapper.readValue(
                Path.of("gear.txt").toFile(),
                GearInventory.class
        );

        List<Gear> gear = inventory.getItems();

        FilterConfig config = FilterConfig.defaults();
        GearScorer scorer = new GearScorer();
        RoleEvaluator roles = new RoleEvaluator(scorer, config);
        DecisionEngine decisions = new DecisionEngine(config, scorer);

        Map<Quality, Long> counts = gear.stream()
                .filter(g -> g.getEnhance() == 15)
                .map(g -> {
                    GearScore score = scorer.score(g);
                    RoleEvaluation role = roles.evaluate(g);
                    return decisions.decide(g, score, role);
                })
                .collect(Collectors.groupingBy(
                        Decision::quality,
                        Collectors.counting()
                ));

        System.out.println("Inventory: " + gear.size());
        System.out.println(" +15: " +
                gear.stream().filter(g -> g.getEnhance() == 15).count());

        counts.forEach((quality, count) ->
                System.out.println(quality + ": " + count));

        // We can add assertions to ensure counts are reasonable.
    }
}
