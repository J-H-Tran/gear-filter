package com.e7gear.engine;

import com.e7gear.gear.Quality;
import com.e7gear.role.RoleEvaluation;
import com.e7gear.scorer.GearScore;

public record Decision(
        Quality quality,
        String reason,
        GearScore gearScore,
        RoleEvaluation roleEvaluation
) {
}
