package com.e7gear;

import com.e7gear.app.role.RoleEvaluation;
import com.e7gear.app.scorer.GearScore;

import java.util.concurrent.ConcurrentHashMap;

public final class GearCache {
    private final ConcurrentHashMap<Long, GearScore> scoreCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, RoleEvaluation> roleCache = new ConcurrentHashMap<>();

    public GearScore getScore(long gearId) {
        return scoreCache.get(gearId);
    }

    public void putScore(long gearId, GearScore score) {
        scoreCache.put(gearId, score);
    }

    public RoleEvaluation getRole(long gearId) {
        return roleCache.get(gearId);
    }

    public void putRole(long gearId, RoleEvaluation role) {
        roleCache.put(gearId, role);
    }

    public void clear() {
        scoreCache.clear();
        roleCache.clear();
    }
}
