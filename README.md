# Epic Seven Gear Filter

A tool for analyzing Epic Seven gear inventories, scoring equipment, and generating actionable **KEEP / REVIEW / DELETE** recommendations based on the 2026 meta and community gear evaluation principles.

The logic is derived from the [2026 Epic Seven Equipment Guide](https://github.com/vikchun/e7-gear-guide) and uses the official substat enhancement probabilities.

---

## Overview

This project processes a `gear.txt` file (exported from the game via tools like [E7 Gear Exporter](https://github.com/ezzlo/e7-gear-exporter)) and produces a CSV report with:

- **Gear Score** – a weighted sum of all substats (flat and percentage).
- **Role fit** – scores for DPS, Bruiser, Support, and Debuffer.
- **Decision** – `KEEP`, `REVIEW`, `DELETE_CANDIDATE`, or `KEEP_MOD_CANDIDATE` (salvageable with a modification gem).

The tool is designed to be **conservative** – it avoids false positives (deleting potentially useful gear) at the cost of retaining some mediocre pieces.

---

## How It Works

The pipeline consists of three independent components:

| Component | Responsibility |
|-----------|----------------|
| **GearScorer** | Calculates a statistical **Gear Score** from substats, using the same formula as the community’s WSS but with flat stats normalised. |
| **RoleEvaluator** | Determines how well the piece fits each role (DPS, Bruiser, Support, Debuffer), using slot‑specific recommendations from the 2026 guide. |
| **DecisionEngine** | Applies a set of rules to produce a final **KEEP / REVIEW / DELETE** decision. All thresholds are externalised in `filter-config.json`. |

The pipeline is stateless, making it easy to run in batch mode or integrate into other tools.

---

## Metrics & Determination Factors

### Gear Score

The total **Gear Score** is calculated as:

```
Score = ATK% + DEF% + HP% + Effectiveness + EffectResistance
        + Speed * (8/4)
        + CritDamage * (8/7)
        + CritChance * (8/5)
        + FlatAttack * 3.46/39
        + FlatDefense * 4.99/31
        + FlatHealth * 3.09/174
```

Three derived scores are also provided:
- `dScore` – DPS contribution (ATK%, CC%, CDMG%, Speed).
- `sScore` – Support contribution (HP%, DEF%, ER%, Speed).
- `cScore` – Combat score (all except Effectiveness and ER).

### Role Fit

Each role has a set of **useful substats** and **slot‑specific preferred stats** (e.g., DPS necklaces value CDMG > CC > ATK). The evaluator counts:

- `usefulStatCount` – how many substats belong to the role’s stat set.
- `coreStatCount` – how many of those are slot‑preferred.
- `mainStatPreferred` – whether the main stat is recommended for the slot and role.

### Decision Rules (Summary)

1. **Speed ≥ openerSpeedThreshold** → `KEEP` (Opener bypass).
2. **Modified substat + Gear Score ≥ reviewScore** → `KEEP`.
3. **Left‑side piece (Weapon/Helmet/Armor) + score ≥ keepScore** → `KEEP`.
4. **Slot‑compatible + ≥ strongCore stats + preferred main** → `KEEP`.
5. **Slot‑compatible + ≥ strongCore stats + score ≥ keepScore** → `KEEP`.
6. **Spike (≥3 enhancement rolls) + role fit + (preferred main OR score ≥ reviewScore)** → `KEEP`.
7. **Mod‑gem potential** (3 useful stats or 2 useful + core/slot) + potential score ≥ keepScore → `KEEP_MOD_CANDIDATE`.
8. **Gear Score ≥ keepScore but weak role** → `REVIEW`.
9. **Other borderline cases** → `REVIEW`.
10. **Otherwise** → `DELETE_CANDIDATE`.

### Special Adjustments

- **Right‑side penalty**: Necklaces, Rings, and Boots with a preferred main stat have a lower effective keep threshold (`keepScore - rightSidePenalty`), reflecting the higher RNG cost of rolling them.
- **Set weighting**: Role scores are multiplied by a configurable factor (e.g., SpeedSet ×1.3, DestructionSet ×1.3) to reflect meta set value.
- **Mod‑gem potential**: The tool identifies pieces with one dead stat that could become good after a mod gem, marking them as `KEEP_MOD_CANDIDATE`.

---

## Configuration

All thresholds and weights are externalised in `filter-config.json`. If the file is missing, the tool falls back to sensible defaults.

```json
{
  "reviewScore": 58.0,
  "keepScore": 66.0,
  "strongCoreStats": 2,
  "reviewCoreStats": 1,
  "highSpeed": 15,
  "rightSidePenalty": 4.0,
  "openerSpeedThreshold": 17,
  "modGemMax": {
    "AttackPercent": 7,
    "CriticalHitChancePercent": 5,
    "CriticalHitDamagePercent": 7,
    "Speed": 4
  },
  "setMultipliers": {
    "SpeedSet": 1.3,
    "DestructionSet": 1.3,
    "PenetrationSet": 1.2,
    "TorrentSet": 1.2,
    "CounterSet": 1.2
  }
}
```

- `reviewScore` / `keepScore` – global thresholds.
- `strongCoreStats` – minimum core stats for automatic keep.
- `rightSidePenalty` – amount subtracted from `keepScore` for right‑side pieces with preferred main.
- `openerSpeedThreshold` – speed value that triggers Opener bypass.
- `modGemMax` – maximum values for mod gems (used in potential score calculation).
- `setMultipliers` – multipliers applied to role scores for specific gear sets.

---

## Usage

### Prerequisites
- Java 21
- Gradle (or use the provided `gradlew`)

### Build
```bash
./gradlew build
```

### Run
```bash
./gradlew run
```

By default, it looks for `gear.txt` in the project root and writes `gear-analysis.csv`.

You can also specify custom input/output paths:
```bash
./gradlew run --args="path/to/gear.txt path/to/output.csv"
```

### Output
The CSV report contains one row per gear piece with all scores, role data, and the final decision.

---

## Architecture & Extensibility

The code is organised into cleanly separated packages:

- `com.e7gear.config` – configuration loading.
- `com.e7gear.gear` – data models for gear and inventory.
- `com.e7gear.stats` – `StatType` enum with aliases and weights.
- `com.e7gear.scorer` – `GearScorer` for statistical scoring.
- `com.e7gear.role` – `RoleEvaluator` for role suitability.
- `com.e7gear.engine` – `DecisionEngine` for final decisions.
- `com.e7gear` – `Main` entry point and CSV generation.

New rules or metrics can be added by extending the appropriate component without affecting the others.

---

## License

MIT – use freely, modify as needed. Contributions welcome.

---

## Acknowledgments

- [2026 Epic Seven Equipment Guide](https://github.com/vikchun/e7-gear-guide) – source of role definitions and slot recommendations.
- [Epic Seven Gear Exporter](https://github.com/ezzlo/e7-gear-exporter) – for generating `gear.txt`.
- The Epic Seven community for ongoing gear theory and meta discussions.
