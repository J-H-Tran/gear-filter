package com.e7gear.app.role;

import java.util.List;

public record RoleEvaluation(
        List<RoleScore> scores,
        Role bestRole
) {
    public RoleEvaluation {
        scores = List.copyOf(scores);
    }

    public RoleScore scoreFor(Role role) {
        return scores.stream()
                .filter(score -> score.role() == role)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No score for role: " + role));
    }
}
