# DecisionEngine Policy

## Source-derived signals

The 2026 equipment guide explicitly favors multiple favorable substats and provides role/slot recommendations. It lists:

- DPS: ATK%, Crit Chance%, Crit Damage%, Speed
- Bruiser: HP%, DEF%, Crit Chance%, Crit Damage%, Speed
- Support: HP%, DEF%, Effect Resistance%, Speed
- Debuffer: HP%, DEF%, Effectiveness%, Speed

Accessory recommendations are slot-specific:

- Necklace: DPS -> CD/CC/ATK; Bruiser -> HP/CC/CD/DEF; Support -> HP/DEF; Debuffer -> HP/DEF/ATK
- Ring: DPS -> ATK; Bruiser -> HP/DEF/ATK; Support -> HP/DEF/ER; Debuffer -> Effectiveness/HP
- Boots: DPS -> Speed/ATK; Bruiser -> Speed/HP/DEF; Support -> Speed/HP; Debuffer -> Speed

The guide also emphasizes that gear with multiple favorable substats is more broadly useful and that Speed is an important target. These are source-derived principles, not deletion thresholds.

## Decision policy

The exact numeric thresholds below are **initial project heuristics**, not values stated by the source guide. They are configurable and should be validated against the actual inventory and manual decisions.

```text
Modified substat                  -> KEEP
Speed >= 15                       -> KEEP
3+ useful role stats              -> KEEP
3+ enhancement rolls + 2+ role stats -> KEEP
Score >= 66 with no strong role   -> REVIEW
2 useful role stats               -> REVIEW
Score >= 58                        -> REVIEW
Otherwise                         -> DELETE_CANDIDATE
```

Deletion is intentionally narrow. A piece is not a deletion candidate merely because it lacks a spike.

## Why these rules are separated

GearScorer answers:

> How statistically strong are the rolls?

RoleEvaluator answers:

> Which role/build categories can use this piece, considering its slot and main stat?

DecisionEngine answers:

> Is there enough evidence that this piece is safe to delete?

This prevents the original `maxBoosts < 3 -> Low Quality` rule from being recreated under a different class name.
