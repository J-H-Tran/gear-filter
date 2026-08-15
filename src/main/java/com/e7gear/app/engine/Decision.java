package com.e7gear.app.engine;

import com.e7gear.gear.Quality;
import com.e7gear.app.role.RoleEvaluation;
import com.e7gear.app.scorer.GearScore;

public record Decision(
        Quality quality,
        String reason,
        GearScore gearScore,
        RoleEvaluation roleEvaluation
) {
}
