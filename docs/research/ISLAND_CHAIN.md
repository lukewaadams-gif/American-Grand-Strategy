# First Island Chain: how the game works, from the code

Everything is in one file: `/home/user/src/IslandChain/IslandChain.java` (12,450 lines; lines 533–796 are encoded map data). There is also `/home/user/src/IslandChain/README.md` and six screenshots in `/home/user/src/IslandChain/screenshots/`. I looked at `theatre.png`, and it matches the code below. Line numbers refer to `IslandChain.java`.

Two corrections to your brief:
- **There is no real-time clock and no pause.** The game is turn-based. Each turn's resolution is then played back as an animation, and the only speed controls are an animation toggle and a speed multiplier.
- **"24H" in the top bar is the turn length,** not a speed setting.

## 1. Time

- **Ticks.** `Sim.tick` advances one game hour (l.3074). The order inside a tick is fixed: arrivals, ship moves, sub moves, `Air.hour`, land-battery fires, naval fires, missile resolution, submarines, amphibious, ground war every 6 h (`if (g.hour % 6 == 0) Ground.step`), shipping, intelligence, repairs, then `hour++`. `World.daily` runs at every midnight.
- **Turn length** (l.2125):
  ```
  if (phase != PH_WAR) return 24;  if (w < 24) return 1;  if (w < 168) return 6;  return 12;
  ```
  So turns are one day before the war, one hour for the first war day, six hours until D+7, then twelve hours. If H-hour falls inside a turn, the turn stops at H-hour (`turnEnd = g.hour`, l.10125) and the player gets control back.
- **Phases.** `PH_PREWAR` (shown as "CRISIS"), `PH_WAR`, `PH_OVER`.
  - The war starts when `hour >= launchHour` (China) or `hour >= blueStrikeHour` (a US pre-emptive strike) (l.3077).
  - If China has not attacked after day 110, the game ends "Deterrence holds" (l.7149).
- **Calendar.** Day 1 is Monday 1 March 2027. `clock()` gives "HH00" and the UI adds "L", so 0000L.
  - `dLabel` (l.2190) shows "DAY n" before the war. The Chinese player sees "H-HOUR IN Nh"; after the war starts it shows "D+n hh00H".
  - H-hour is always 0500 on D-day: `launchHour = (D-1)*24+5`.
- **Turn cycle** (`Turns`, l.10023):
  1. END TURN or Space calls `endTurn`. The AI turn runs now (`Staff.aiTurn`), so the AI never sees the human's orders.
  2. `animate` advances the clock at `speed` game-hours per real second: 7 for 24-h turns, 3.5 for 12 h, 2 for 6 h, 0.55 for 1 h, multiplied by the animation speed. It calls `Sim.tick` as the animated clock passes each hour.
  3. Space during playback skips to the end (`fastForward`).
  4. `finish` increments the turn and autosaves to `~/IslandChain-autosave.sav`.
  5. `startOrders` runs the human's staff automation, then builds a SITUATION REPORT pop-up from the log (alerts, sinkings, entries into the war, ceasefires).
- **Speed controls.** Only in the STAFF/LOG tab: ANIMATION ON/OFF (off sets speed to 1000) and ANIM SPEED, which cycles ×1 → ×2 → ×4 → ×0.5.
- **Hot-seat.** A "HAND OVER TO THE … COMMANDER" screen sits between the two players.

## 2. The map

- **Projection.** Mercator. World x is longitude; world y is `mercY(lat) = -deg(ln tan(π/4+φ/2))`, which grows southward (l.344).
  - Screen position: `sx = mapX + w/2 + (lon-camX)*zoom` and `sy = MY0 + MH0/2 + (mercY(lat)-camY)*zoom` (l.9044).
  - `zoom` is pixels per degree, clamped to 9–420. The camera stays inside the bounds 88–162°E, 16°S–48°N.
  - The game opens centred on 24.0N 126.5E at zoom 24.
- **Layout.** Design resolution 1600×900, letterboxed. Map area x=340, y=44, 910×808; "wide" mode uses the full 1600 width.
- **Land data** (`MapData`, `MapLayers.load` l.479). Natural Earth 1:10m.
  - Two resolutions, `LAND_COARSE` and `LAND_FINE`, each with 31 country rows: TWN JPN CHN KOR PRK PHL VNM USA RUS IDN MYS SGP BRN AUS PNG THA KHM LAO MMR IND BGD MNG TLS SLB PLW FSM BTN NPL SPR SCR OTH. SCR and OTH are empty.
  - Points are delta-coded text (`Geo.decode` l.354). The alphabet is ASCII 35–126 minus backslash. The first 45 characters end a digit and the next 45 continue it (base 45). Deltas are zigzag-encoded. "!" separates rings, and rings shorter than 3 points are dropped. Origin is (88E, 16S) and scale is 1/100°.
  - `BATHY` holds depth bands at 200, 1000, 3000, 5000 and 6000 m.
  - `TW_SECTORS` holds 25 Taiwan sector polygons (origin 118E 21.5N, scale 1/500°).
  - Fine coastlines are drawn at zoom ≥22.
- **Naval routing** (`Nav`, l.2879). Land is rasterised into a 0.2° water mask, with 12 narrow straits forced open. Routes use 8-way A* (east-west steps cost cos(lat)), then string-pulling so each waypoint has line of sight over water.
- **Sea areas.** 36 `AreaDef` circles (centre plus radius in km, with a "strait" flag), from Taiwan Strait (N/C/S) to the Andaman Sea (l.1334).
  - They are not drawn on the map; the coordinate readout under the cursor names the area.
  - They are the units of patrol, CAP, ASW, ISR and mining.
  - 7 off-map hubs sit at the map edge (Hawaii, Alaska, US West Coast, Diego Garcia, Europe, Southern Australia, US East Coast), each with a ship-days and air-hours delay (l.1387).
- **Rendering of the static layer** (`renderCache` l.9166). Drawn once to a bitmap and redrawn only when the camera changes.
  - Ocean (16,50,72), with the five bathymetry bands filled progressively darker.
  - Graticule every 5°, 2° or 1° depending on zoom, with "120E"/"25N" labels.
  - Countries filled by bloc: China (52,32,34), Taiwan (28,56,48), allies (30,42,56), Russia/North Korea (44,34,40), others (33,38,46). Cyan coast stroke, heavier on Taiwan.
  - Taiwan sector outlines (dashed) at zoom ≥70.
  - First and second island chains as amber dashed polylines, and a dotted strait median line.
  - Letter-spaced italic sea and land labels.
- **Dynamic layer.** At zoom ≥40, sectors are filled red with alpha 40+120×ctrl, and contested sectors get a pulsing amber outline.
  - The threat overlay (T) draws fixed enemy rings. China: SRBM 700 km, DF-16 1,000, DF-21D 1,500, DF-17/21C 1,700–2,000, DF-26 4,000. US: F-35A radius from Kadena 1,100 km, B-1B/JASSM-ER from Guam 3,000, Tomahawk 1,600.
  - A scale bar sits bottom right.
- **Right-side map buttons** (l.9148):

  | Button | Effect |
  |---|---|
  | + / − | Zoom ×1.4 or ÷1.4 |
  | TW | Centre 23.7N 120.9E at zoom 150 (ground war map) |
  | WP | Centre 23.5N 126E at zoom 26 |
  | R | Range rings for the selected unit (default on) |
  | T | Threat overlay (default off) |
  | L | Labels (default on) |
  | <> / >< | Wide map |

- **Range rings** (`Rings` l.9838). For a naval group: its longest anti-ship missile range, land-attack range, area air defence (240 km for US, 200 for China) and air-wing radius. For a squadron: radius with tankers, plus strike reach (radius + 0.85 × weapon range). For a base: the maximum combat radius of aircraft there.
- **Bases.** 136 `BaseDef`s (name, country, region, lat/lon, flags air/naval/army/missile/port/civil/C2/energy, runways, parking, hardened shelters, underground spaces) (l.1177).

## 3. Units and forces

**Data classes (l.1560–1733).**
- `Ship`: class, damage 0–1, sunk, mission-killed.
- `ShipClass` (48 classes, l.920): hit points, knots, fire channels, long- and short-range SAMs, anti-ship missiles and type, land-attack missiles and type, special magazine, ASW value, amphibious lift (battalion-equivalents), crew.
- `NavalGroup`: position and destination, path, mission (12 types `NM_*`), area, port, `mag[42]`, SAM counts, embarked squadrons (`wing`) and troops (`cargo`), emcon, weapons-free flag, `manual` (player control), days at sea, merchants under escort.
- `Sub` (14 classes, l.988): quietness 0.6–0.93, sprint and patrol knots, torpedoes, missiles, endurance (30–70 days).
- `Squadron`: aircraft count `n`, readiness 0–1, base/carrier/hub, mission (15 `AM_*` types), target, weapon, aircraft per sortie (default max(2, n/2)).
  - Aircraft types (60, l.1036) carry quality (0.1–3.0; the F-22 is 3.0), radius in km, km/h, stealth, strike load, sorties per day and a weapon list.
- `LandUnit` (28 systems, l.1118): launchers, ready rounds (launchers × rounds per launcher), reload hours, fire channels, dispersal (0.6 at start), and an `asbm` flag for missile brigades assigned to anti-ship work.
- `GroundUnit`: battalions, quality, supply, hours dug in, state (reserve, ready, moving, embarked, dead, staging).
  - Lift tonnage per battalion (`GTONS`) and combat value per battalion (`GPOW`) by type: infantry 0.9 … heavy combined-arms 1.25, reserve 0.5.
- **Munitions.** 42 types (`MUN`, l.839) with range, power (0.3–1.4), speed class (subsonic, supersonic, ballistic, hypersonic, stealth), cost and a sourced note. Stocks sit in 8 pools: US theatre, US global, Japan, Taiwan, Australia, Korea, Philippines, China (l.885).
- There is no ship readiness or fuel model.

**Order of battle at 1 March 2027 (`Setup.build*`, l.2413–2873).**

- **US in theatre:**
  - CSG-5 George Washington (CVN + 3 DDG + CG) at Yokosuka; CSG-3 Lincoln at 17.5N 136E; CSG-1 Vinson at 14.2N 117E. Each carrier air wing is 2×22 F/A-18E/F, 10 F-35C, 5 E-2D and 5 EA-18G.
  - DESRON 15 (3 DDG) at Yokosuka.
  - ESG-7 Tripoli (LHA, LPD, LSD) at Sasebo, with 10 F-35B and the 31st MEU (2 battalions).
  - LCS Division at Guam.
  - Submarines: 5 at Guam, 2 forward Virginias, 1 Ohio SSGN.
  - Aircraft: Kadena (2×18 F-15EX, 12 F-35A, tankers, E-3, P-8), Misawa (24 F-16, 12 F-35A), Iwakuni (2×16 F-35B), Osan/Kunsan (4×18 F-16), Andersen (4 B-52, 12 KC-135, MQ-4C), plus Yokota/Futenma lift.
  - Ground-based: Patriot (Kadena, Guam, Osan), THAAD and the Aegis-based Guam Defense System, NMESIS on Okinawa, Typhon at Laoag.
- **US off map (hubs):**
  - Carriers CSG-9 Roosevelt and CSG-12 Ford, a 4-DDG surface group, ESG-3 America with the 15th MEU.
  - 13 SSNs (5 if "subs withheld").
  - About 400 aircraft: B-1B, B-2, B-52, F-22, F-35A, F-15E, F-16, tankers, P-8, C-17.
  - HIMARS/PrSM, NMESIS, Typhon, Patriot and THAAD units.
  - All of this is released by mobilisation.
- **US munitions (theatre / global):** LRASM 160/400, JASSM-ER 500/2100, TLAM 120/1200, SM-6 140/500, PAC-3 240/620, and so on. There is monthly production (e.g. LRASM 12, PAC-3 45).
- **China:**
  - Three carrier groups: Liaoning, Shandong, Fujian (Fujian adds 12 J-35).
  - Five surface action/escort groups, 2 ASW groups (10×056 each), 2 missile-boat flotillas (16×022 each).
  - Amphibious Group East (075, 2×071, 10×072, 6 LSM) and Amphibious Group South (3×075, 6×071, 18×072, 14 LSM). The pessimistic preset adds a 076 group.
  - Coast guard 18/14/14, militia 30/20. The Russian Pacific Fleet is on China's side.
  - Completing the ro-ro programme adds 3×10 ro-ro ferries, a barge pier group and 12 lighters.
  - 33 submarines (6×093, 15 Yuan, 6 Kilo, 6 Song).
  - About 1,100 aircraft: 7×24 J-20, 6×24 J-16, 5×24 J-10C, and others.
  - PLA Rocket Force: 4×36 SRBM, 2×24 DF-16, 3×24 DF-17, 2×24 DF-21C, 3×24 DF-21D (anti-ship), 5×30 DF-26 (2 anti-ship), 3×24 CJ-10, 3×27 PHL-16, 2 YJ-12 coastal brigades, 10 HQ-9 SAM brigades.
  - Stocks: SRBM 1000, DF-21D 450, DF-26 560, YJ-12 1500, 20,000 mines.
  - Invasion force: 6 amphibious brigades ×4.5 battalions, 7 marine ×3.5, 6 airborne ×3, 3 air assault ×2, 3 SOF, and 24 heavy brigades in reserve.
- **Taiwan:**
  - About 400 fighters (Mirage, F-CK-1, F-16V), E-2K, P-3C, 28 AH-64E.
  - Navy: 4 Kidd, 8 Cheng Kung, 6 Kang Ding, 5 Tuo Chiang, 24 Kuang Hua VI FAC, coast guard; 2 Hai Lung subs.
  - Missiles: HF-3/HF-2/Harpoon coastal batteries, HF-2E, 2 HIMARS battalions, 3 Patriot, 6 Tien Kung III.
  - Army: 22 active brigades plus 3 garrisons, and 21 reserve brigades (quality 0.5).
- **Allies (fight only once belligerent):**
  - Japan: about 300 aircraft, 4 escort flotillas, 10 SS, Type 12 regiments, PAC-3, Type 03.
  - Korea: 120 fighters, 7 destroyers, 2 KSS-III, Patriot.
  - Philippines: 18 FA-50, 2 frigates, BrahMos.
  - Australia: 24 F-35A at Tindal, more at hub, a RAN task group, 2 Collins.
  - UK and French carrier groups and SSNs at the Europe hub.
- **Scenario presets** (`applyPreset` l.2222): base, optimistic, pessimistic, custom. They set 13 toggles (Japan basing/entry, civil airports, Philippine basing, Taiwan delay, JASSM-ER maritime, poor ship defence, late US mobilisation, subs withheld, simultaneous crisis, extra shelters, mainland strikes) and three multipliers (PRC missiles, PLA amphibious, Taiwan ground).

## 4. Orders

**Map clicks** (`Orders.defaultOrder` l.10274). Right-click gives the selected unit its default order:

| Selected | Right-click result |
|---|---|
| Naval group | Move to the point over water; a click on land picks the nearest port and returns there |
| Submarine | Patrol the nearest area |
| Squadron | Strike the nearest naval contact if it can strike; otherwise CAP the nearest area (over Taiwan if within 160 km) |
| Ground unit | Move to that sector (must be adjacent) |
| Battery | Fire at the nearest contact, base or sector |

Giving an order sets `manual`/`held`, which takes the unit away from the staff until you press RELEASE TO STAFF. Orders cost nothing; the constraints are range, weapon fit, stock and the rules of engagement.

**Order buttons in the right-hand panel:**
- **Naval group** (l.11461): MOVE TO, PATROL AREA, SCREEN AREA, QUARANTINE (Chinese coast guard), ESCORT CONVOYS (US), RETURN TO PORT, EMBARK TROOPS, ASSAULT BEACH (beach or captured port), LAND ATTACK (12 cruise missiles at a base), EMCON, WEAPONS FREE/HOLD, RELEASE TO STAFF.
- **Submarine:** PATROL AREA, RETURN TO PORT, WEAPONS FREE/HOLD.
- **Squadron** (l.11625): COMBAT AIR PATROL, STRIKE, ESCORT STRIKES, ASW PATROL, EARLY WARNING, RECONNAISSANCE, GROUND SUPPORT, AIRLIFT, AIRBORNE ASSAULT (China), TANKER SUPPORT, REBASE, HOLD/ALERT, plus a weapon picker that shows stock.
- **Battery:** FIRE MISSION, SALVO +4, HOLD FIRE.
- **Ground unit:** MOVE TO SECTOR (adjacent only), HOLD. Taiwan's army belongs to Taipei, so the US player cannot order it.
- **Contact panel:** lists every squadron and anti-ship battery in reach for a one-click strike.

**Rules of engagement.** An order that breaks them is accepted but flagged "NOT PERMITTED … will not launch"; `War.allowed` is checked again at launch (l.3866).

**How orders execute:**
- Ships move `kn×1.852` km/h along the A* path. Patrols re-wander inside the area every 10–24 h.
- Rearming takes 36 h in port in wartime (24 h in peace); light damage is repaired in port.
- Subs patrol at patrol knots and transit at 16 knots (nuclear) or 9 (diesel). Diesel boats go home at their endurance limit. Rearming takes 72 h.
- Strikes launch when hours since the last strike ≥ max(6, 24/sorties per day) (l.3847). Reach is `radius×(1+tanker bonus) + 0.9×weapon range`. Missiles are drawn from the pool: aircraft × strike load.

## 5. Combat and attrition

- **Air.**
  - CAP coverage is `available × fraction on station × quality`, with `available = n × readiness × 0.8 × base factor` and fraction on station `0.3×max(0, 1−dist/(1.15×radius×(1+tanker)))`.
  - Tanker bonus is at most 0.45.
  - CAP-vs-CAP fights run every 3 h (l.3770):
    ```
    k = limited ? 0.008 : 0.028;  lossB = k*rb*rb/(rb+bb)/1.6
    ```
  - Strike packages meet defending CAP at the release point (l.3941): `pS = 1-exp(-defOnStrikers*0.022/hard)`, where hard is 3.5 for stealth, 1.6 for 4th-gen-plus, 0.8 for bombers. Losses are capped by the defenders' shots.
  - SAM losses per aircraft are 0.01 (stealth), 0.09 (bombers/lift), 0.035 or 0.055 (others) × min(3, SAM umbrellas).
  - Readiness gains +0.004/h and loses −0.003/h on CAP or strike, never below 0.45.
- **Anti-ship salvos** (`shipDefense` l.4183, "after CSIS … ch. 2"):
  1. 15% of missiles fail at launch; JASSM-ER is ×0.6.
  2. There is a 5% chance the whole attack goes wrong and a 5% chance the whole defence does.
  3. Interceptor kill probability and number of engagement opportunities by speed class: subsonic 0.70×2, stealth 0.55×1, supersonic 0.70×1, ballistic 0.45×1, hypersonic 0.30×1.
  4. Each shot uses 2 interceptors (long-range SAMs first). Engagements per opportunity = channels × 2.
  5. Terminal defence: ballistic 0.35, hypersonic 0.25, stealth 0.55, others 0.70; then 10% guidance failures.
  - Before any of this the missiles must find the target: `pAcq = exp(-(d-5)/basket)`, basket 22/45/32 km, or 60 km for a ship in port.
- **Damage** (l.4246, 4275). Each hit adds `power×(0.35–0.65)/hp`.
  - At 0.45 damage the ship is mission-killed; at 1.0 it sinks with 78% probability.
  - Ballistic and hypersonic missiles hit carriers and amphibious ships first, with 65% probability.
  - A carrier sinking destroys 60–90% of its embarked aircraft. Troops are lost in proportion to the sunk ship's share of the lift.
- **Bases and land targets** (`intercept` l.4325):
  - SAM kill probability vs ballistic: THAAD 0.8, Aegis 0.7, Patriot 0.65, HQ-9 0.55, Type 03 0.15. Vs HGV 0.3–0.45; vs cruise missiles 0.8; rockets ×0.6. Aegis ships within 120 km and fighters on alert vs subsonic missiles also help.
  - Missiles that get through: submunitions use a parking-density model (`lethal = 7.5×power`, survival `(1-lethal/area)^missiles`, l.4454). Unitary warheads split 55% craters, 25% shelter hits, 20% fuel damage.
  - The runway closes at `craters ≥ max(2, 3×runways)`. One crater is repaired every 3 h.
  - Launchers die at 0.18×(1−dispersal) per impact.
- **Submarines.** Detection of a target within 75 km (nuclear) or 50 km (diesel) per hour is `0.28×quiet×(1.4 for amphibious/merchants)×(1−min(0.6, 0.05×ASW))`.
  - Torpedo hit probability is 0.72 (amphibious, merchants, coast guard) or 0.55, minus ASW. Each hit does `1.45×(0.7–1.2)/hp`.
  - Counter-detection `0.15+0.06×ASW`, then 50% chance the sub is killed. Being hunted by ASW kills via `threat×(1.25−quiet)` then 45%.
- **Amphibious** (l.5062).
  - Unloading rate per hour: amphibious ship 0.07×lift, LST 0.16×lift, ro-ro 0.07×lift beside a barge pier but 0.006 without one. Multiplied by beach quality × a daily sea-state roll (0.55–1.0; ×0.35 on summer typhoon days). Each beach is capped at 0.45×quality per hour.
  - Loss on the beach: `clamp(defense*0.11, 0, 0.3)`, where defense is Taiwanese power/(8+0.6×PLA power), adjusted by Taiwan's command and control, GMLRS rockets and close air support.
  - Loading takes 8 h, or 24 h if the "embark amphibious brigades" programme is unfinished.
  - Airborne: 0.14 battalions per transport (Z-20 helicopter 0.022); losses 18% into a hostile sector, 6% otherwise.
- **Ground war** (every 6 h, l.5444).
  - Terrain defence: urban 2.0, plain 1.0, mountain 2.6, island 1.3. Battalions dug in for 48 h get ×1.25.
  - Formula (l.5485):
    ```
    r = effR/effB; intensity = (r>1.4||r<1/1.4)?1:0.45; k=0.009*intensity;
    ctrl += 0.08*tanh(ln r)*intensity/sqrt(TERRAIN_DEF)
    ```
  - Ports and airfields change hands at control ≥0.6 (≥0.65 for air bases), and are demolished 55–85% on capture. They are retaken below 0.3.
  - PLA supply comes from captured facilities' kilotonnes per day × shipping, and is cut by US subs in the strait.
- **The losses table.** One `Loss` per nation (l.1878): ships, carriers, major combatants, amphibious ships, subs, aircraft lost in the air and on the ground, merchants, killed, wounded, prisoners.
  - Killed per event: aircraft shot down = crew×0.55; aircraft destroyed on the ground 0.15; battalion lost = 650×0.28; ship sunk = crew×(0.06–0.33); each missile impact on a base 0.7 killed and 2.2 wounded.
  - The situation panel shows ships / aircraft / subs / killed for the US, China, Taiwan and Japan.
- **CSIS references.**
  - The LRASM note: "all expended in week 1 of every CSIS iteration".
  - The help text: "sink the amphibious fleet - every CSIS iteration turned on it" (l.11854).
  - The after-action table compares each result with the CSIS base case: US aircraft ~270, 2 US carriers, ~3,200 US killed, 138 PRC ships (86 amphibious), and so on (l.12068).

## 6. Escalation and politics

- **Escalation ladders**, each rung 0–6, shown as "R*n* / B*n*".
  - China (l.5940): grey zone, quarantine, blockade, force in the blockade zone, strikes on Taiwan, strikes on US and allied forces, strikes beyond the theatre.
  - US: diplomatic only, presence and escort, combat near Taiwan, combat at sea anywhere, limited mainland strikes, broad mainland strikes, strategic strikes.
  - `War.allowed` (l.6049) gates every target. Examples: US bases in Japan need China at rung 5; mainland ports and air bases need the US at 4; command centres and missile bases need 5.
  - Rung 2 or higher commits US forces (`usEnter`). US entry happens automatically when US ships or territory are hit.
- **Nuclear risk** (`Esc.daily` l.6273). Each day the index moves 30% of the way toward this target:
  ```
  t = 3 + 4 (at war) + 9×max(0, blue−3) + 15 (blue 6)
      + min(22, 0.05×mainland hits) + min(18, 0.2×C2 hits)
      + (40 − PRC stability, if stability < 40) + 7×(both sides' alert levels)
      + 6–16 if the US homeland has been hit + 10 (red 6) + 5 (2 US carriers lost)
      + 8 if the invasion is failing + 4 if China holds more than 15,000 km²
  ```
  - Above 70 a "NUCLEAR CRISIS" opens for 3 turns; it eases below 55.
  - Each side can set its nuclear alert: NORMAL, RAISE, DISPERSE, THREATEN.
  - AUTHORISE NUCLEAR USE needs two confirmations and ends the game as "Nuclear catastrophe".
- **US politics** (l.6902). Support for defending Taiwan eases toward an anchor:
  ```
  46 + 14 if China struck first + 4 if Guam was hit
  − 6·ln(1 + US killed/500), halved if China struck first
  − oil and stock-market effects
  − 8 if the US struck first
  − war weariness, (weeks at war − 20 days)×0.3
  + rally effect
  ```
  It moves 10% of the way each day. Approval and Congress follow; an AUMF passes when Congress >58.
- **China.** Stability anchor is `72 − 1.4×GDP pain + 0.25×(nationalism − 62) − 3.5·ln(1 + killed/2000) ± war-progress terms`.
- **Taiwan's will** (l.6631). Target is 62 ± unity, +12 if the US is fighting, minus power-loss, import-loss, territory and casualty terms, and −22 if abandoned. It moves 12% per day.
- **Countries.** 12 actors (l.1507), each with stance −100 to 100 → 7 levels: Hostile, Leans China, Neutral, Sympathetic, Access, Basing, Belligerent.
  - Stance drifts 8% per day toward an anchor built from Chinese escalation × fear, US credibility × trust, economic pressure, and +45 if attacked.
  - Basing is withdrawn if stance falls to Neutral or below, unless the country was attacked.
- **DIPLO actions** cost diplomatic capital: start 30, +3 per day, maximum 60. Each country can be approached once per day per side.
  - US: consult 3, access/overflight 6, basing 10, combat participation 14, economic assurance 8, coordinate sanctions 6, Japanese civil airports 8, warn Moscow/Pyongyang 5 (l.6954).
  - China: reassure 5, coerce 6, threaten 6, request Russian/North Korean support 10, neutrality pact 8, influence operations 5.
  - Outcomes are rolled. Basing succeeds with probability `0.4 + (stance−40)/60 + (0.5 if attacked)` and needs level ≥4.
- **ECON.** 14 economies (l.1538).
  - The output gap closes toward a target built from W[k] weights times five channels: strait shipping, chips (lagged), sanctions, war damage, energy price. Time constant 18 days going down, 40 coming back.
  - Markets: Brent, LNG, war-risk insurance, USD/CNY, US 10-year, gold, stock indices. Quarterly GDP prints.
  - US one-time actions: targeted / broad / full sanctions, SWIFT cut, reserve freeze, chip embargo, distant blockade (needs rung 3).
  - Chinese counter-measures: rare-earth ban, boycott, Treasury sales, tourism ban, counter-sanctions, asset seizure.
- **PREP programmes.** 15 per side (l.2022–2058). Each has a duration in days and a visibility "signal"; progress is `100/days` per day, and a halted programme decays by 4 (China) or 5 (US) points per day.
  - There is no capital cost. China's cost is +0.04% to its GDP-gap target per active programme, plus 0.15 once ro-ro requisition starts.
  - Completion effects: dispersing the Rocket Force sets launcher dispersal to 0.85; ro-ro spawns ferries; mines +350; "Show of force" deterrence +7; munitions shift adds JASSM-ER 160, TLAM 250 and more; and so on.
- **Wars start** when China's H-hour arrives or the US pre-empts. A pre-emptive strike fires 12 h after ordering and costs support −10, alliance stances −12 and nationalism +15.
- **Endings** (`Outcome.check`, daily, l.7144):

  | Condition | Outcome |
  |---|---|
  | Taiwan's will <15 while squeezed | Taiwan capitulates |
  | Taipei control >0.8 and >18,000 km² held | PRC victory |
  | PRC stability <12 | China stands down |
  | US support <18 while fighting | Ceasefire |
  | Invasion: no PLA ashore (<1.5 battalions) and lift <25% or day >20; or the lodgement starves | Taiwan defended |
  | Blockade broken (throughput >0.7, power >0.85) | Taiwan defended |
  | Two of the outlying islands held, US not fighting, day >30 | Partial PRC success |
  | Day 75 of the war | Judged by territory, Taiwan's power, US losses |
  | No war by day 110 | Deterrence holds |

  - Ceasefires can also be proposed, on three sets of terms.
  - Each side gets a 0–100 score graded DECISIVE / SUCCESS / MIXED / SETBACK / DEFEAT.
- **STAFF/LOG tab.** Six automation domains (air, surface fleet, submarines, missile batteries, ground, amphibious), the animation controls, and an event log filterable ALL / COMBAT / LOSSES / DIPLO / ECON / INTEL. Clicking an entry centres the map on it.

## 7. Intelligence and fog of war

- **What you see.** Enemy units are shown only as `Contact`s: position offset by `err × (0.3–1.0)`, error ring grows 28 km/h (subs 10), identification unknown / type / positive, decoy flag.
- **Sensors** (l.5663):
  - Satellites: 0.08–0.1 per hour; error 10 km + 1.3 × speed.
  - Shore radar: Taiwan 0.7 × C2 factor within 260 km; China 0.75 near its coast, plus 0.16 over-the-horizon radar at 700–2,800 km.
  - Aircraft ISR/AEW: `(1−e^(−0.22×isr))/contested`, error 4 km.
  - Emissions intercept 0.06 if not in emcon.
  - Own ships within 110 km 0.7; own subs within 90 km 0.3.
  - Contacts expire after 48 h (groups), 10 h (subs), 24 h (batteries).
- **Strategic warning** (daily). Active enemy programmes create indicators with probability `signal × collection`. Collection quality = `0.45 + 0.25×satellites + 0.2×HUMINT`. Exercise cover multiplies signals by 0.62. There is noise, and HUMINT reports can be false (25%, or 45% after Chinese influence operations).
  ```
  B.warning = 1-exp(-score/9)   // WATCH / ELEVATED / SIGNIFICANT / HIGH / IMMINENT at .15/.35/.6/.8
  ```
  An INTSUM is logged each day.
- **INTEL tab.**
  - US: the warning gauge, most-likely course of action bars, satellite health, number of tracks, collection tasking AUTO/MANUAL (6 points spread across 6 areas), indicators with confidence %.
  - China: US readiness estimate, plus FEED FALSE REPORTS (4 diplomatic capital) and DECOY AMPHIB GROUP.

## 8. The interface

- **Top bar:** "FIRST ISLAND CHAIN" with "UNITED STATES | INDOPACOM"; the date; HH00L; the DAY/D+ label; "TURN n | 24H"; gauges WARNING (or US READINESS), ESCALATION R/B, NUCLEAR RISK, US SUPPORT (or STABILITY), TAIWAN WILL; buttons MENU, SAVE, BRIEF, SND ON/OFF, STAFF; END TURN (shows RESOLVING during playback).
- **Left panel** (340 px): 10 tabs in two rows of five, FORCES … STAFF/LOG, on keys 1–0.
  - FORCES: scrollable lists of naval groups (fit/total, ● if under your orders), subs, squadrons, batteries, ground units.
  - ARSENAL: theatre / global / used / per-month for each munition.
  - TAIWAN: energy days, blockade, ground war, requests to Taipei (mobilise, ration, disperse), charts.
  - China's PREP tab adds the course-of-action panel: 5 plans, H-hour options, landing region, blockade lethality, D-day ±, SET H-HOUR, START NEEDED PREP.
- **Right panel** (350 px): details and orders for the selection. With nothing selected it shows SITUATION (CRISIS / WAR / ENDED label, losses table, order of battle in theatre, how to play).
- **News ticker** (bottom 48 px): red NEWS tag; the last 8 headlines, upper case, "◆" separators, scrolling at 60 px/s; below it the latest log line or order toast.
- **Screens:**
  - Title: NEW CAMPAIGN, CONTINUE (AUTOSAVE), LOAD, BRIEFING LIBRARY, QUIT.
  - Setup: side (US, China, hot-seat, watch), difficulty, intelligence mode, preset and toggles, briefing text, BEGIN.
  - Hand-over screen.
  - After-action: outcome title, grades, CSIS comparison table, GDP bars, two charts, key-events timeline, VIEW FINAL MAP.
  - Briefing library: 12 topics, opened with F1.
- **Keys:** Space/Enter (end turn, skip playback, close sitrep), Esc (cancel pick → deselect → menu), Tab (wide map), +/=/− (×1.3), arrow keys (pan 60 px), T, 1–9/0, F1. The wheel zooms ×1.25 at the cursor; dragging more than 4 px pans; left-click selects within 14 px.

## 9. The art

- **Palette** ("command centre", l.125): background #05090f, panels rgba(10,18,28,.89), text #d5e6f5, dim #7f9bb3, accent #4fd1ff, amber #ffb347, green #5be37d, red #ff5a52, friendly #5aa9ff, hostile #ff6b5e, unknown #ffe066.
- **Fonts:** SansSerif bold for headings and tabs; a clean monospace for numbers and clocks.
- **Panels:** 1 px border plus cyan corner brackets, cyan section headers with a faint underline. Buttons are 6 px rounded rectangles in 8 styles (primary cyan, danger red, on green, tab, ghost).
- **Symbols** (`Units.sym` l.9449), APP-6-like, with a text glyph inside (CV, SAG, AMPH, RORO, CG, MIL, FAC, SSN, SAM, ASM, MRL, ROC/PLA):
  - Friendly: circle (sea), lower half-arc (sub), upper half-arc (air), rectangle (land).
  - Hostile: diamond, or V shapes for sub and air.
  - Unknown: yellow rounded square.
- **Bases:** air = circle with a slash, naval = square with a bar, missile = triangle, C2 = crosshair. A red X means runway closed, and a badge shows the aircraft count.
- **Contacts:** fade with age over 30 h and carry a dashed red error circle. Labels are placed by priority and skipped on overlap.
- **Effects:** missile trails drawn as a moving 35% segment, arcing for ballistic missiles; enemy salvos appear only after 55% of their flight; impact rings; sinking = red ring with an X; green "N intercepted" ping; dogfight sparks; floating "LANDING" text.
- **Title backdrop:** rotating radar sweep and scanlines.
- **What ports easily to SVG/Canvas:** pre-projected land, bathymetry and sector paths (decode once, store them as path strings); a static cached layer with a dynamic overlay; simple geometric symbols and fixed palette constants. Everything is drawn in immediate mode with no images.

## 10. AI

- **Turn structure.** `Staff.aiTurn` runs the side's strategy AI, then generic staff tasking (air, naval, subs, fires; for China also amphibious and ground), then `TaiwanAI` and the ceasefire logic (`AIDecide`).
- **China's AI** (l.7688):
  - Picks a plan by weight: invasion 0.46, blockade 0.24, quarantine 0.10, islands 0.12, strikes 0.08. D-day is day 36–53.
  - Starts each programme a fixed number of days before D (e.g. influence operations D−32, ro-ro D−14, Rocket Force D−6).
  - At D−2 it compares `deterrence()` with a threshold of 56/60/66 by difficulty. If deterred it postpones up to twice, then downgrades an invasion to a blockade, or stands down. Deterrence counts carriers near Taiwan, Japanese and Philippine basing, sanctions, Taiwan's will, US support, its own unfinished programmes, and US programmes.
  - In the war it climbs to rung 3/5 when the US enters, and its fire plan prioritises fresh carrier and amphibious tracks for anti-ship ballistic missiles.
- **US AI:** starts programmes as the warning level rises; enters combat 24/6/1 h after Chinese strikes on Taiwan (easy/medium/hard); escalates sanctions on a schedule; asks allies to join.
- **Weaknesses** (from reading the code; the code has no comments admitting any):
  - Fleet tasking is keyed on group-name strings ("East Sea SAG 1" → East of Taiwan).
  - Several decisions read ground truth rather than intelligence: China's deterrence counts actual carrier positions, its fire plan counts actual aircraft at bases and ships in port, and drop-zone and island choices use true Taiwanese strength.
  - Staff rates decoys at value 6, so they draw strikes.
  - Easy mode just shuffles the target list.
  - `polCap` accumulates but is never spent.
  - `visibleBase` and `FxView.visible` always return true.

## 11. Sound

`Sfx` (l.147) synthesises 16 effects in code from tones and filtered noise: click, hover, ping, alarm, launch, boom, splash, typing, turn, order, error, sonar, jet, big boom, alert, open. Output is 22,050 Hz mono 16-bit, at most 20 voices, soft-clipped as `s/(1+|s|*0.55)`, volume 0.7. Effects are triggered by events and by visual effects during playback. If there is no audio device the game runs silently.

## Typical session

1. Title: NEW CAMPAIGN. Choose UNITED STATES, MEDIUM, automatic intelligence, BASE CASE, then BEGIN.
2. Read the "Command assumed" pop-up. Day turns (24H): check INTEL warning and indicators; in PREP start Raise readiness, Surge subs, Shift munitions, Disperse aircraft; in DIPLO request basing from the Philippines and Japan's civil airports; in ECON perhaps impose targeted sanctions. Press END TURN each day and read the SITUATION REPORT.
3. As the warning reaches HIGH: Show of force, Declassify, Mobilise reserves; in TAIWAN request full mobilisation. Optionally ESCALATE → ORDER PRE-EMPTIVE STRIKE.
4. H-hour at 0500 on D-day: the alarm sounds, the turn stops, and turns become 1H. Set the US rung (2–4) in ESCALATE.
5. Select squadrons (FORCES or the map), then STRIKE and click the amphibious contacts. Put fighters on CAP over Taiwan. Press T and move the carriers outside the anti-ship missile rings. Set submarines to patrol the Taiwan Strait. Leave everything else to the staff.
6. Use TW to watch the sector fight; check ARSENAL stocks; propose or answer ceasefires in ESCALATE.
7. The game ends on one of the conditions in section 6, and the after-action review (outcome, grades, comparison with CSIS, economic damage, charts, timeline) appears. VIEW FINAL MAP or MAIN MENU.
