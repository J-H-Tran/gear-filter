package com.e7gear.legacy;

import com.e7gear.gear.Gear;
import com.e7gear.gear.Substat;

import java.util.*;
import java.util.stream.Collectors;

public class GearAnalyzer {

    private final int maxCapacity;
    private final double targetThresholdPct;

    public GearAnalyzer(int maxCapacity, double targetThresholdPct) {
        this.maxCapacity = maxCapacity;
        this.targetThresholdPct = targetThresholdPct;
    }

    public static class GearReport {
        public final Gear gear;
        public final int maxBoosts;
        public final int totalBoosts;
        public final double gearScore;
        public final boolean lowQuality;
        public final boolean hasModified;
        public final String reason;
        public final String bestRole; // NEW: DPS, Support, Tank, Debuffer, or None

        public GearReport(Gear gear, int maxBoosts, int totalBoosts,
                          double gearScore, boolean lowQuality, boolean hasModified, String reason, String bestRole) {
            this.gear = gear;
            this.maxBoosts = maxBoosts;
            this.totalBoosts = totalBoosts;
            this.gearScore = gearScore;
            this.lowQuality = lowQuality;
            this.hasModified = hasModified;
            this.reason = reason;
            this.bestRole = bestRole;
        }
    }

    public List<GearReport> analyze(List<Gear> items) {
        return items.stream()
                .filter(g -> g.getEnhance() == 15)
                .map(this::evaluate)
                // Sort by Gear Score Ascending (Worst items first)
                .sorted(Comparator.comparingDouble((GearReport r) -> r.gearScore))
                .collect(Collectors.toList());
    }

    private GearReport evaluate(Gear gear) {
        List<Substat> subs = gear.getSubstats();
        if (subs == null || subs.isEmpty()) {
            return new GearReport(gear, 0, 0, 0.0, true, false, "No substats", "None");
        }

        int maxBoosts = 0;
        int totalBoosts = 0;
        double score = 0.0;
        boolean hasModified = false;

        // Role counters based on 2026 Guide definitions
        int dpsStats = 0;
        int supportStats = 0;
        int tankStats = 0;
        int debufferStats = 0;

        for (Substat s : subs) {
            int boosts = Math.max(0, s.getRolls() - 1);
            totalBoosts += boosts;
            if (boosts > maxBoosts) {
                maxBoosts = boosts;
            }
            if (s.isModified()) {
                hasModified = true;
            }

            // 1. Calculate Gear Score (from gear-score.txt)
            score += calculateStatScore(s.getType(), s.getValue());

            // 2. Role Suitability Check (from 2026 Guide)
            String type = s.getType();

            // DPS: ATK%, CC%, CD%, SPEED
            if (type.equals("AttackPercent") || type.equals("CriticalHitChancePercent") ||
                    type.equals("CriticalHitDamagePercent") || type.equals("Speed")) {
                dpsStats++;
            }

            // Support: HP%, DEF%, EFF RES%, SPEED
            if (type.equals("HealthPercent") || type.equals("DefensePercent") ||
                    type.equals("EffectResistancePercent") || type.equals("Speed")) {
                supportStats++;
            }

            // Tank/Bruiser: HP%, DEF%, CC%, CD%, SPEED
            if (type.equals("HealthPercent") || type.equals("DefensePercent") ||
                    type.equals("CriticalHitChancePercent") || type.equals("CriticalHitDamagePercent") ||
                    type.equals("Speed")) {
                tankStats++;
            }

            // Debuffer: HP%, DEF%, EFFECT%, SPEED
            if (type.equals("HealthPercent") || type.equals("DefensePercent") ||
                    type.equals("EffectivenessPercent") || type.equals("Speed")) {
                debufferStats++;
            }
        }

        // Determine Best Role (Need 3+ stats to count as a role fit)
        String bestRole = "None";
        int maxRoleCount = Math.max(Math.max(dpsStats, supportStats), Math.max(tankStats, debufferStats));

        if (maxRoleCount >= 3) {
            if (dpsStats == maxRoleCount) bestRole = "DPS";
            else if (supportStats == maxRoleCount) bestRole = "Support";
            else if (tankStats == maxRoleCount) bestRole = "Tank";
            else if (debufferStats == maxRoleCount) bestRole = "Debuffer";
        }

        // Spike Logic:
        // Low Quality = No single substat has 3+ upgrades (rolls < 4)
        boolean hasSpike = maxBoosts >= 3;
        boolean isLowQuality = !hasSpike;

        // Refine Low Quality based on Role Suitability
        // If it's spread (max 2 boosts) BUT fits a role perfectly (3+ stats), it might be worth keeping
        // However, for inventory cleanup, we still flag it as low quality but the Role tag helps user decide.
        // If it has NO spike AND NO role fit, it is definitely trash.

        String reason;
        if (hasModified) {
            reason = "Modified Substat (Safe)";
            isLowQuality = false; // Force safe
        } else if (isLowQuality) {
            if (!bestRole.equals("None")) {
                reason = "Spread (Max " + maxBoosts + ") [Role: " + bestRole + "]";
            } else {
                reason = "Spread (Max " + maxBoosts + ") [No Role]";
            }
        } else {
            reason = "Spike (Max " + maxBoosts + ")";
        }

        return new GearReport(gear, maxBoosts, totalBoosts, score, isLowQuality, hasModified, reason, bestRole);
    }

    private double calculateStatScore(String type, double value) {
        if (type == null) return 0.0;

        switch (type) {
            case "AttackPercent":
            case "DefensePercent":
            case "HealthPercent":
            case "EffectivenessPercent":
            case "EffectResistancePercent":
                return value;

            case "Speed":
                // PDF confirms Speed is hardest to roll at Lv.88
                return value * (8.0 / 4.0); // 2.0

            case "CriticalHitDamagePercent":
                return value * (8.0 / 7.0); // ~1.14

            case "CriticalHitChancePercent":
                return value * (8.0 / 5.0); // 1.6

            case "Attack":
                return value * 3.46 / 39.0;

            case "Defense":
                return value * 4.99 / 31.0;

            case "Health":
                return value * 3.09 / 174.0;

            default:
                return 0.0;
        }
    }
}
