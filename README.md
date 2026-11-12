# Mandate (working title)

An American political strategy game. Win the 2028 presidential election, govern from 2029 to 2032 alongside Congress, the economy and foreign crises, and then run for re-election on the record you built.

This repository is the **foundation**. It contains a deterministic, UI-free simulation core that plays the whole arc, plus a text front end and balance tools. The design is drawn from three earlier games: Battleground 2028, Resolute Desk and First Island Chain. Those repositories are kept untouched; see `docs/SOURCE_AUDIT.md`.

## Documents

| | |
|---|---|
| [PRODUCT_VISION.md](PRODUCT_VISION.md) | What the game is and is not, the pillars, the audience |
| [FEATURE_PRIORITY.md](FEATURE_PRIORITY.md) | Every source mechanic classified A-F, feature cards, and MVP / 1.0 / expansion scope |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Modules, command model, determinism, formulas, porting plan, ADRs |
| [RISK_REGISTER.md](RISK_REGISTER.md) | Risks, scores, mitigations |
| [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md) | Milestones, release gates, prioritised backlog |
| [GAMEPLAY_DESIGN.md](GAMEPLAY_DESIGN.md) | Loop audits, the core loop, the 12-week governing prototype, mechanic change log, probe evidence, playtest plan |
| [docs/SOURCE_AUDIT.md](docs/SOURCE_AUDIT.md) | Source commit SHAs, build checks, licensing, what is actually implemented |

## Build and run

Requires Java 21 and Maven 3.9.

```bash
mvn verify
```

This builds the code and runs the 63 tests.

Play interactively in the terminal:

```bash
./mandate play
```

Watch the autopilot play one full game:

```bash
./mandate auto 7 D
```

Run a balance report over many autopilot games:

```bash
./mandate batch 1000 TOSS_UP
```

`batch` also accepts the `BLUE_ENVIRONMENT` and `RED_ENVIRONMENT` scenarios.

Play the 12-week governing prototype, or compare scripted strategies across seeds:

```bash
./mandate weeks 5 D unified
```

```bash
./mandate compare 300
```

Report the operational war model's calibration:

```bash
./mandate war 300
```

## What the foundation plays today

- **2028 general election** in weekly turns. Each week you choose three stops (rallies or fundraisers), spend money on ads and field by state, and pick a message. There are three debates where you choose a posture after reading the opponent's prep notes. Events can interrupt. State polls carry a hidden systematic error, and the result is drawn with correlated error.
- **Transition.** Choose three agenda priorities.
- **Governing** in monthly turns. You get three attention points a month. You can push bills by party line, a bipartisan deal, reconciliation (once a year, with a unified Congress) or executive order (which courts can block). You can also address the nation, bolster Pacific deterrence or try diplomacy. Each bill shows a bloc-by-bloc whip count before you commit. The game also runs:
  - a monthly economy (output gap, Okun's law, Phillips curve, a Taylor-rule Fed, debt) and approval;
  - three flashpoints, including a Taiwan escalation ladder that ends in an abstract war resolver;
  - events.
- **Midterms in 2030**, using the real Class I Senate map.
- **Re-election in 2032**, where the national environment is your record.
- **Legacy score**, with each component listed.

## Engine

There is one authoritative `WorldState`. Each part of it is owned by one engine: economy, politics, Congress, the executive, diplomacy, military and elections. Engines change each other only through `Effect` transitions. `GameEngine` routes every transition to its owner and records it in the consequence ledger. The pieces:
- a master `SimulationClock` (monthly, weekly, daily and operational turns);
- `EventScheduler` and `DecisionResolver`;
- `DeterministicRandomManager`, whose random state is saveable;
- `SaveManager`: version-2 JSON snapshots, with version-1 command-log saves still readable.

All balancing constants live in `data/tuning.properties`. See [ARCHITECTURE.md](ARCHITECTURE.md) sections 2-6.

## Layout

```
mandate-core/   simulation (no UI imports, enforced by a test)
mandate-cli/    text play, autoplay, balance batch
docs/           audit and supporting notes
```
