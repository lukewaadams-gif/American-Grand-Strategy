# Modding American Grand Strategy

Almost everything in the game is data: the leaders, factions, units, ideas, doctrines, laws, the calendar and every event card. A **mod** is one JSON file that changes that data. Players install it from **Mods** on the title screen. It applies when the page loads, before the simulation starts.

Start from the example, [`mods/hanging-chads.json`](../mods/hanging-chads.json). It adds a card in September 2000 and a gentler difficulty, and it retitles the debate. Players can also download it from the Mods card.

## The file

```json
{
  "name": "Hanging Chads",
  "version": "1.0",
  "author": "You",
  "about": "One line shown in the Mods list.",
  "files": {
    "events":     [ { "id": "butterfly_ballot", "kind": "card", "title": "The butterfly ballot", "choices": [ … ] } ],
    "calendar":   { "turns": [ { "title": "Sep 18, 2000", "open": ["third_party_watch", "butterfly_ballot"] } ] },
    "difficulty": [ { "id": "canvasser", "name": "Canvasser", "note": "A walk in the park", "shift": 5, "_before": "intern" } ]
  }
}
```

`name` and `files` are required. Each key under `files` names a data file in [`data/`](../data). The game refuses a mod that names a file it does not have.

## How a mod changes the data

| The data is… | The mod… |
|---|---|
| A list of things with a key (`id`; `line` for units; `code` for states; `title` for calendar turns) | **Patches** the item with the same key, field by field. You only write the fields you change: `{ "id": "debate", "title": "…" }` retitles the debate and keeps the rest. |
| | **Adds** an item with a new key: at the end, or before the item named in `"_before"`. |
| | **Removes** an item: `{ "id": "mayor", "remove": true }`. |
| An object | Merges key by key, with the same rules all the way down. |
| Anything else: a number, a string, or a list without keys (such as `"open": [...]` or a card's `"choices"`) | **Replaces** it. Write the whole list. |

Mods apply in the order shown on the Mods card, so a later mod can patch an earlier one's additions.

## Safety

- **The simulation checks the data.** If it refuses a modded set, for example because a field has the wrong type, the game says why and runs unmodded for that session. It never half-applies a mod.
- **A save remembers its mods,** recorded as names, versions and a fingerprint of their contents. It only loads under the same mod set, because replaying a game on different data would quietly change it. To finish a modded game, keep its mods on.
- **Determinism holds.** The same seed, setup and mods always give the same game.

## The data files

| File | What it holds |
|---|---|
| `states` | Each state: `code`, `name`, `region`, `urban` share, electoral votes per census (`ev`). The map's hexes are built from real geography and are not moddable. |
| `factions` | The nine blocs: their `affinity` by region and urbanism, the friendship `bonus`, their `quest`, and the `third_party` they form when both parties ignore them. |
| `leaders` | The six playable leaders: stats, an `ability` (named modifiers) and a unique `unit`. |
| `units` | Unit lines (`line`) with their `eras`: name, moves, cost, upkeep, power and actions. |
| `ideas` | The think tank's tree: `requires`, `cost`, a year gate (`from`) and `mods`. |
| `doctrines` | Five trees of policies with `mods`. |
| `buildings` | What an HQ can build, with `requires`. |
| `laws` | The laws a president can build like wonders, with their effects. |
| `planks` | Platform planks with their Influence cost, the factions they please, and forecasts. |
| `demands` | What each faction asks of you, by era and by term. |
| `calendar` | The turns in order: `title`, `label`, `kind` (quarter, fortnight, election, office, end), and `open` and `close` card lists. |
| `events` | Every card. See below. |
| `difficulty` | Levels: `shift` moves the starting map toward you; the rival's strength scales from it. |
| `powers`, `century`, `names`, `terrain` | The world board, the fifty-year technology track, name pools, terrain movement costs. |

### Modifiers

Abilities, doctrines and ideas share one vocabulary of named modifiers, summed per side. The full set the simulation reads: `ads_pct`, `ads_spill`, `ally_pull`, `buy_pct`, `canvass_pct`, `canvass_rings`, `city_pct`, `doctrine_cost_pct`, `election_close`, `election_office`, `friend_decay_pct`, `hq_reach`, `hq_vol`, `law_pct`, `national`, `office_pct`, `organizer_moves`, `phonebank_vol`, `plank_cost_pct`, `poll_ideas`, `poll_reach`, `rally_pct`, `scandal_pct`, `unit_moves`. An unknown name does nothing.

## Event cards

```json
{ "id": "butterfly_ballot", "kind": "card", "art": "ledger", "focus": "FL",
  "kicker": "Palm Beach County, Florida", "title": "The butterfly ballot",
  "body": "Text with {variables}.", "advice": "What your adviser says.",
  "if": { "won": false },
  "choices": [
    { "label": "Sue to change the design", "text": "What it costs and does.", "effects": [ { "money": -4 }, { "state": "FL", "add": 0.6 } ] },
    { "label": "Fight it out", "text": "A gamble.", "odds": { "base": 0.45, "stat": "cha", "per": 0.0125 },
      "win": [ { "nat": 0.4 } ], "lose": [ { "nat": -0.4 } ],
      "result": { "win": "It worked", "lose": "It did not", "body_win": "…", "body_lose": "…" } }
  ] }
```

- **`kind`**: `card` (choices), `headline` (a newspaper front page with `sub`), or `roll` (a forecast decides between two events: `{ "forecast": "threat", "hit": "attack", "miss": "foiled" }`). The `intro`, `mate` and `cabinet` kinds are special screens.
- **`art`**: an engraved scene. One of: `dawn`, `capitol`, `ledger`, `ticker`, `snow`, `convention`, `debate`, `oval`, `blackout`, `radar`, `globe`, `court`, `sorrow`.
- **`focus`**: a state code; the board pans there when the card opens.
- **`forecast`**: a card of ranges shown beside the choices: `{ "title", "tag", "lines": [ { "label", "lo", "mid", "hi", "unit", "good": "up"|"down" } ] }`.
- **`if`** (on a card or a choice): all of these must hold.
  - `won` (bool): whether you won the election.
  - `flag`, `not_flag` (string): a flag set by an earlier effect.
  - `third_party` (bool): whether a third party is running.
  - `mod` (string): you hold that modifier.
  - `all`: a list of conditions.
- **`odds`**: `fixed` (0–1), or `base` + (your leader's `stat` − 50, or minus the challenger's with `"vs": "challenger"`) × `per`. The stat is one of `cha`, `pol`, `deal`, `integ`. The result is clamped to 5–95%.

### Effects

Each effect is an object; a list of them runs in order.

| Effect | Does |
|---|---|
| `{ "nat": 0.5 }` | Moves every hex: positive is toward you, in points of margin. |
| `{ "scandal": -0.8 }` | Like `nat`, scaled by `scandal_pct`. |
| `{ "state": "FL", "add": 0.6 }` | Moves one state. |
| `{ "friend": "labor", "add": 10 }` | Faction friendship. Also accepts `$allies`, `$weakest` and `$third_faction_id`. |
| `{ "money": -4 }`, `{ "influence": 5 }`, `{ "ideas": 3 }` | Your resources. |
| `{ "momentum": 1 }`, `{ "rival_momentum": -1 }` | Momentum. |
| `{ "approval": 2 }` | Approval, in office. |
| `{ "flag": "promise" }` | Sets a flag for later conditions. |
| `{ "forecast": "threat", "add": -12 }` | Moves a forecast (2–98). |
| `{ "century": "broadband", "add": 1 }` | Pulls a technology on the century track (ids in `century`). |
| `{ "attitude": "China", "add": -1 }` | A great power's attitude (−2…+2), by its name in `powers`. |
| `{ "pledge": "rxbenefit", "free": true }` | Adopts a platform plank. |
| `{ "law_start": "energy", "progress": 10 }` | Starts a law, in office. |
| `{ "third_cut": 0.5 }` | Changes the third party's cut. |

### Text variables

Write `{name}` in any title, body, kicker, label or text:

- **Always:** `leader`, `leader_last`, `leader_state`, `party`, `rival`, `rival_last`, `rival_party`.
- **After the election:** `ev_you`, `senate`, `house`.
- **Forecasts:** `threat`.
- **Context cards** (`"context": ["weakest", "challenger", "mate", "third"]`):
  - `weakest`: `weakest_name`, `weakest_demand`, `weakest_demand_name`.
  - `challenger`: `challenger`, `challenger_last`, `challenger_office`.
  - `mate`: `mate`, `mate_first`, `mate_last`.
  - `third`: `third_name`, `third_faction`, `third_share`.

An unknown variable prints as written, which makes typos easy to spot.

## Testing a mod

1. Install it from the Mods card and reload. If the simulation refuses it, the toast and the Mods card say why.
2. Start a game on a fixed seed (the setup's third step) and play to your card's turn.
3. Developers can run the same checks the project uses (see the README): `node test/mods.cjs` installs the example mod and plays to its card.
