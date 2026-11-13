// The engine's view models, as the API writes them (mandate-web/src/main/java/game/mandate/web/api/EngineApi.java).

export type Tone = 'GOOD' | 'BAD' | 'WARN' | 'INFO';

export interface NewsItem { date: string | null; text: string; headline?: string; tone?: Tone; }

export interface Ratings { charisma: number; debate: number; fundraising: number; policy: number; discipline: number; }

export interface Side {
  name: string; id: string; party: 'D' | 'R'; partyName: string; home: string; ratings: Ratings; ideology: number;
  cash: number; momentum: number;
  orders?: { strategy: string; strategyLabel: string; rate: string; rateLabel: string; message: string };
}

export interface PollWeek { date: string; national: number; ev: number; win: number; tipping: string; movers: string[]; }

export interface StateView {
  code: string; name: string; ev: number;
  /** D minus R, as the polls read. */ margin: number;
  /** The player's lead. */ forPlayer: number;
  /** Change in the player's lead since last week's polls, or null in the first week. */ change: number | null;
  /** The player's chance of carrying the state, from the polls and the published error model. */ win: number;
  baseline: number;
  adsMe: number; adsThem: number; fieldMe: number; fieldThem: number; visitsMe: number; visitsThem: number;
}

export interface CampaignView {
  phase: string; date: string; dateLabel: string; electionDay: string; weeksLeft: number; week: number; weeks: number;
  debate: string | null; player: Side; opponent: Side; opponentPlan: string; opponentReasoning: string;
  pledges: string[]; opponentPledges: string[]; pendingDecisions: number;
  polls: PollWeek[]; errorSd: number; states: StateView[]; tipping: string | null; news: NewsItem[];
  /** Present while a campaign is running. */
  turn?: TurnInfo; staff?: Staff; trail?: Trail; schedule?: Slot[]; activities?: ActivityInfo[]; field?: Field; money?: Money; ads?: Ads;
}

/** The campaign's money. All amounts in $M; weekly amounts are per week even in the final stretch. */
export interface Money {
  cash: number; debt: number; raised: number; spent: number; raisedTotal: number; spentTotal: number; history: [number, number][];
  donorsTapped: number; donorCap: number; smallList: number; hq: number; tiers: { name: string; perWeek: number }[];
  national: { id: string; label: string; grps: number; costPerPoint: number; perWeek: number }[];
  platforms: { id: string; label: string; cpm: number; share: number; message: string; skew: string; description: string }[];
  audience: [number, number, number]; cpm: number; messages: { id: string; label: string; description: string }[];
  budget: { label: string; amount: number }[]; budgetTotal: number;
}

export interface Market {
  id: number; name: string; households: number; costPerPoint: number; grps: number; tone: 'POSITIVE' | 'CONTRAST' | 'NEGATIVE'; perWeek: number;
  /** [state, share of that state's TV homes]. */ reach: [string, number][];
}

export interface AdsState { code: string; markets: number[]; digital: number; impressions: number; tvStock: number; superPac: number; redBox: boolean; recent: number; }

export interface StudioAd {
  name: string; tone: number; issue: string; group: number; messenger: string; tested: number | null; groupScores: number[]; quotes: string[]; made: string;
}

export interface Ads {
  markets: Market[]; states: AdsState[]; library: StudioAd[]; airing: number;
  studio: { tones: string[]; issues: { id: string; label: string }[]; groups: string[]; keyGroups: string[];
    messengers: { id: string; label: string; description: string }[]; produceCost: number; testCost: number };
}

/** One state's ground game. Money in $M; persuasion and gotv as a share of their ceilings (0-1). */
export interface FieldState {
  code: string; offices: number; officeOrder: number; organizers: number; organizerOrder: number; party: number; transfer: number;
  volunteers: number; shifts: number; doors: number; calls: number; texts: number; relational: number; doorsToDate: number;
  persuasion: number; gotv: number; openCost: number; officeWeek: number; organizerWeek: number; value: number;
}

export interface Field {
  totals: { offices: number; organizers: number; party: number; volunteers: number; shifts: number; doors: number; calls: number; texts: number;
    relational: number; doorsToDate: number };
  hireCost: number; doorsByTurn: number[]; states: FieldState[];
}

/** A week, or (from two weeks out) a single day. */
export interface TurnInfo { daily: boolean; day: number; dailyFrom: string; label: string; weeksToVote: number; }

/** Which parts of the campaign the staff runs. */
export interface Staff { schedule: boolean; ads: boolean; field: boolean; }

/** The candidate on the trail. Stamina 0-100; the projection is after this turn's events. */
export interface Trail {
  stamina: number; staminaAfter: number; eventCosts: number; prep: number; location: string; locationName: string;
  enthusiasm: number; nameRecognition: number; rallies: number; bestRally: string; nextDebate: string | null;
}

export type ActivityKind = 'none' | 'crowd' | 'persuade' | 'money' | 'media' | 'groups' | 'prep' | 'rest';

/** One of the week's 21 slots (day x morning/afternoon/evening). */
export interface Slot {
  slot: number; day: number; part: number; date: string; activity: string; past: boolean;
  state: string | null; stateName: string | null; param: number; paramName: string | null;
  /** A long same-day flight: the event runs at 70% and tires the candidate. */ travel: boolean;
}

export interface ActivityChoice { name: string; audience?: string; tier?: string; views?: number; lean?: number; refusal?: string | null; }

export interface ActivityInfo {
  id: string; label: string; short: string; kind: ActivityKind; cost: number; stamina: number; local: boolean; description: string;
  choices: ActivityChoice[];
}

export interface DecisionOption { label: string; detail: string; tag: string | null; tone: Tone | null; command: object; }
export interface Decision {
  kind: string; eyebrow: string; title: string; body: string; options: DecisionOption[];
  /** In office: the option the staff would take, and the kind of stop watch mode makes for it (-1 for none). */
  recommended?: number; stop?: number;
}

// ---- the presidency ----------------------------------------------------------------------------------------------

export interface PresidencyBill { id: string; name: string; stage: string; }

/** What the Oval Office shows (EngineApi.presidency). Figures that do not exist yet (before the first reading) are null. */
export interface PresidencyView {
  phase: string; date: string; dateLabel: string;
  president: { name: string; id: string; party: 'D' | 'R'; partyName: string };
  approval: number | null; approvalChange: number | null;
  unemployment?: number | null; inflation?: number | null; growth?: number | null; gas: number | null;
  termWeek: number; termWeeks: number;
  /** The term being served, how much of a lame duck the president is (0-100), and whether the presidency is over. */
  term?: number; lameDuck?: number; over?: boolean;
  /** After an election that chose who comes next: the successor and the day of the handoff. */
  successor?: string; handoff?: string;
  pendingDecisions: number; congress: string;
  /** The length of the turn the End button plays: 'week', 'war' (an operational turn), 'transition', or null when there is none. */
  turnUnit: 'week' | 'war' | 'day' | 'transition' | null;
  /** The Strait of Hormuz crisis, while it lasts. */
  hormuz?: { day: number; rung: number; rungName: string; flow: number; normal: number; premium: number; killed: number; settled: boolean } | null;
  taiwan?: { day: number; phase: 'crisis' | 'war' | 'over'; hourOfDay: number; turnHours: number; chinaRung: number; chinaRungName: string; usRung: number;
    warning: string; chance: number; electricity: number; killed: number; territory: number; outcome: string | null } | null;
  upcoming: { date: string; what: string }[];
  bills: PresidencyBill[];
  news: NewsItem[];
  /** The countries a trip abroad can go to, nearest first (from day 100). */
  abroad?: { code: string; name: string; flight: number; rel: number; leader: string; visits: number }[];
  /** In office: the president's week and what it reads. */
  week?: PresidentWeek;
  activities?: ActivityChoice[];
  issues?: IssueStanding[];
  states?: StateStanding[];
  watch?: WatchPlanView;
  /** During the first hundred days: the business the chief of staff can run until Congress and the West Wing open. */
  hundredDays?: { week: number; weeks: number; stage: string | null; domains: { id: string; label: string; detail: string; on: boolean }[] };
}

export interface WeekSlot {
  slot: number; day: string; part: string; date: string; activity: string; label: string;
  arg: string | null; argLabel: string | null; cost: number; flight?: number;
}

export interface PresidentWeek {
  slots: WeekSlot[];
  itinerary: { slot: number; day: string; state: string; name: string; hours: number }[];
  counts: { policy: number; trips: number; recharge: number };
  stamina: number; stress: number; sleep: number; health: number; fitness: number; sick: number;
  projected: number; sleepAhead: number; age: number; location: string; locationName: string;
  week: number; delegated: boolean; warChest: number; hill: number; hillHours: number;
  lastWeek: string[]; lastStaminaChange: number; midtermSeason: boolean;
}

export interface ActivityChoice {
  id: string; label: string; description: string; stamina: number; stress: number;
  arg: 'NONE' | 'ISSUE' | 'STATE' | 'COUNTRY';
  /** Why it cannot go in each of the 21 slots, or null where it can. */
  refusals: (string | null)[];
}

export interface IssueStanding { id: string; label: string; approval: number; command: number; standing: number; salience: number; top: boolean; }
export interface StateStanding { code: string; name: string; ev: number; approval: number; flight: number; standing: number; }

export interface WatchPlanView {
  preset: number;
  presets: { label: string; description: string }[];
  priorities: { label: string; low: string; high: string; description: string; value: number }[];
  stops: { label: string; on: boolean; available: boolean }[];
  roadWeeks: number;
}

/** The Capital Ledger's front page for the week. */
export interface PaperView {
  date: string; edition: string;
  lead: { head: string; body: string; kind: string };
  photo: string;
  stories: PaperStory[]; brief: PaperStory[];
  features: { head: string; body: string }[];
  numbers: { label: string; value: string }[];
  developing?: { name: string; stage: number; stages: number; heat: number; trend: string; active: boolean }[];
}
export interface PaperStory { section: string | null; head: string; body: string | null; kind: string; }

/** The country (the windows): approval, its history, what its target is made of, the issues, the coalitions and the states. */
export interface CountryView {
  date: string; approval: number;
  history: { date: string; value: number }[];
  /** Points: base, economy, issues, appeal, honeymoon, record, events, lasting, scandal, wear, mood; then raw, rally, target. */
  parts: Record<string, number>;
  floor: number; ceiling: number; mandate: number;
  issues: IssueStanding[];
  coalitions: { id: string; label: string; approval: number; weight: number }[];
  states: StateStanding[];
  groups?: { code: string; label: string; approval: number; start: number; shown: boolean }[];
}

/** The Residence: how the president is holding up. */
export interface ResidenceView {
  date: string; reason?: string; name?: string; age?: number;
  stamina?: number; sleep?: number; stress?: number; fitness?: number; health?: number; sick?: number;
  advice?: string; warn?: boolean; delegated?: boolean;
  history?: { dates: string[]; stamina: number[]; stress: number[]; health: number[] };
}

export interface EconomyTile { id: string; label: string; unit: string; value: number; digits: number; upIsGood: boolean | null; yearAgo?: number; atOath?: number; }

/** One part of consumer prices: its share of the index and its rise over the year. */
export interface PricePart { key: string; label: string; detail: string; weight: number; value: number; }
/** One of the sectors on top of the model. */
export interface Sector { id: string; label: string; unit: string; value: number; digits: number; drivers: string; }
/** The budget, % of GDP. */
export interface BudgetView { revenue: number; mandatory: number; discretionary: number; interest: number; laws: number; outlays: number; deficit: number; debt: number; averageRate: number; }
/** What one law has done so far, from its own channels, and its channels at full effect. */
export interface LawCredit {
  label: string; since: string | null; started: boolean;
  output: number; demand: number; supply: number; unemployment: number; prices: number; deficit: number;
  fullEffect: string[];
}

/** The briefing book: the figures, more figures, what is coming, each figure's weekly history, and the structural model's detail. */
export interface EconomyView {
  date: string;
  tiles: EconomyTile[];
  more: { label: string; value: string }[];
  next: { what: string; date: string }[];
  history: { dates: string[]; series: Record<string, (number | null)[]> };
  parts: PricePart[];
  core: number;
  sectors: Sector[];
  budget: BudgetView;
  laws: { rows: LawCredit[]; fed: number };
}

/** One row of a score: a measure at one, three and ten years, its central estimate and its range. */
export interface ScoreRow { label: string; unit: string; central: number[]; low: number[]; high: number[]; }
/** The budget office's score of a policy step or a bill. */
export interface ScoreView {
  title: string;
  reading: string;
  evidence: string;
  years: number[];
  rows: ScoreRow[];
  budget: { direct: number; directLow: number; directHigh: number; total: number; deficit: number; debt: number; tenYear: number };
  prices: { label: string; value: number; low: number; high: number }[];
  sectors: { label: string; value: number; unit: string }[];
  outcomes: { label: string; value: number }[];
  basis: { policy: string; basis: string; evidence: string }[];
}

/** One policy on the table. */
export interface PolicyEntry {
  id: string; name: string; levels: string[]; level: number; start: number;
  routes: string[]; evidence: string; spread: string; lag: number; support: number; lean: string | null; signature: boolean;
  winners: string; losers: string; precedent: string; basis: string | null; scored: boolean;
  costUp: number | null; costDown: number | null;
  moves: { date: string; from: string; to: string; law: string }[];
  outcomes: { label: string; value: number }[];
}

/** The Cabinet Room: the policy table by area, this game's bills as their steps, and what the moves have done so far. */
export interface CabinetView {
  date: string;
  areas: { name: string; policies: PolicyEntry[] }[];
  bills: { id: string; name: string; law: boolean; steps: { policy: string; name: string; from: string; to: string; steps: number }[] }[];
  outcomes: { label: string; value: number }[];
}

// ---- the Roosevelt Room (Congress) ---------------------------------------------------------------------------------

export type PartyCode = 'D' | 'R';
export interface HillBloc { index: number; name: string; party: PartyCode; chamber: 'House' | 'Senate'; seats: number; relationship: number; moderate: boolean; loyalty: number; hawk: number; leaning: string; }
export interface HillMember { id: string; name: string; role: string; committee: string | null; state: string | null; party: PartyCode; chamber: 'House' | 'Senate'; bloc: string; relationship: number; price: string | null; owesFavor: boolean; leader: boolean; }
export interface HillWhip { chamber: 'House' | 'Senate'; expected: number; spread: number; need: number; chance: number; seats: number; blocs: { name: string; party: PartyCode; seats: number; yes: number }[]; }
export interface HillBill {
  id: string; source: string; name: string; description: string; ours: boolean; author: PartyCode | null; sponsorParty?: PartyCode | null;
  stage: string; stageKey: string; chamber: 'House' | 'Senate'; weeksInStage: number; stageWeeks: number;
  size: number; scope: number; payFor: number; payForLabel: string; reconciliation: boolean; budgetEligible: boolean; firstChamber: number;
  support: number; cost: number; netCost: number; strength: number;
  held: boolean; bottled: boolean; wentPublic: boolean; vetoed: boolean; note: string;
  votes: { houseYes: number; houseNo: number; senateYes: number; senateNo: number };
  whip?: HillWhip[]; dealtThisWeek?: string[];
}
export interface HillDraft { id: string; name: string; issue: string; sponsor: PartyCode | null; cost: number; reconciliation: boolean; agenda: boolean; moving: boolean; summary: string; }
export interface HillBudget {
  fiscalYear: number; request: { defense: number; other: number; tax: number; entitlements: number }; requestDeficit: number; requestSent: boolean; requestDueWeeks?: number;
  appropriations: { name: string; stage: number; label: string }[]; stopgapWeeks: number | null; shutdown: boolean; blame: number; shutdownWeeks: number;
  debtLimit: number; debtSubject: number; xDateWeeks: number | null; deadline: string; deficit: number;
}
export interface HillRace { state: string; name: string; seatClass: number; democrat: string; republican: string; holder: PartyCode; open: boolean; poll: number; result: number | null; }
export interface HillElections { date: string; label: string; days: number; racesOpen: boolean; national: number; races: HillRace[]; house: { state: string; name: string; seats: number; dem: number }[]; campaignStops: number; warChest: number; }
export interface HillView {
  date: string; open: boolean; reason?: string;
  president?: PartyCode; congress?: string; houseDem?: number; senateDem?: number; houseMajority?: PartyCode; senateMajority?: PartyCode; unified?: boolean;
  hoursLeft?: number; hoursMax?: number; appointments?: number; mandate?: number; filibusterGone?: boolean; delegated?: boolean; week?: number;
  laws?: number; vetoes?: number; overrides?: number; promisesKept?: number; promisesBroken?: number;
  blocs?: HillBloc[]; members?: HillMember[]; bills?: HillBill[]; drafts?: HillDraft[];
  pending?: { kind: string; label: string; weeksLeft: number }[]; ask?: { member: string; kind: string };
  budget?: HillBudget; elections?: HillElections;
  favors?: { member: string; memberId: string; role: string; why: string; promise: boolean; state: string | null; weeksLeft: number | null }[];
  probes?: { subject: string; status: string; chamber: string; severity: number; impeachable: boolean; contempt: boolean; subpoena: boolean }[];
  impeachment?: { status: string; houseYes: number; senateYes: number };
  record?: { date: string; what: string }[];
}

// ---- the West Wing and the Press Briefing Room -----------------------------------------------------------------------

export interface WwPerson {
  id: string; name: string; background: string; party: PartyCode; trait: string; traitDesc: string; traitRisky: boolean;
  skill: number; loyalty: number; morale: number; weeks: number; quality: number; vote?: string;
  voteIn?: number; expected?: number; chance?: number;
}
export interface WwPost { post: number; name: string; short: string; title: string; person: WwPerson | null; readiness: number; workforce: number; nominee?: WwPerson; }
export interface WwLibraryItem {
  id: string; kind: number; name: string; desc: string; issue: string; lean: PartyCode | null; support: number; legal: number;
  department: string; readiness: number; needs: string | null; refusal: string | null;
}
export interface WwAction { id: string; name: string; kind: string; status: string; statusKey: number; standing: boolean; inCourt: boolean; failed: boolean; issued: string; finalIn?: number; }
export interface WwCourts {
  justices: { name: string; age: number; party: PartyCode; lean: string }[]; vacancies: number;
  nominee?: { name: string; voteIn: number; expected: number; chance: number }; shortlist?: { name: string; age: number; what: string }[];
  district: number; circuit: number; districtVacancies: number; circuitVacancies: number; judges: number; strategy: number; strategies: string[]; strategyDesc: string[];
  cases: { title: string; action: string; stage: string; blocked: boolean; done: boolean; won: boolean; note: string; rulingIn?: number; votes?: string }[];
}
export interface WestWingView {
  date: string; open: boolean; reason?: string; president?: PartyCode;
  staff?: { role: number; title: string; does: string; person: WwPerson | null }[]; mood?: number;
  delegatedPeople?: boolean; delegatedPardons?: boolean; leaks?: number; departures?: number; failures?: number;
  posts?: WwPost[]; workforce?: number;
  vp?: { name: string; skills: number[]; mission: number; missionName: string; issue: string | null; weeksLeft: number | null; loyalty: number;
    missions: { id: number; name: string; desc: string; weeks: number }[] };
  library?: WwLibraryItem[]; commentWeeks?: number; emergencyDeclared?: boolean; actions?: WwAction[];
  courts?: WwCourts;
  scandals?: { title: string; severity: number; stage: string; impeachable: boolean; over: boolean }[]; pressure?: number; pardons?: number; defiance?: number;
  probes?: { subject: string; status: string; chamber: string }[]; impeachment?: string;
  record?: { date: string; what: string }[];
}
/** The Situation Room (milestone 8): the world, the brief, hot spots, operations, trade, alliances and wars. */
export interface SitCountry { id: string; name: string; region: string; align: string; rel: number; power: number; trade: number; balance: number; nuclear: boolean;
  leader: string; title: string; flag: string; label: [number, number]; tariff: number; sanction: number; aid: number; visits: number; visitIn: number;
  atWar: boolean; toppled: boolean; flight: number; calledRecently: boolean }
export interface SitGauge { name: string; value: number; tone: 'good' | 'mid' | 'bad' }
export interface SitWar { id: string; name: string; enemy: string; kind: string; stage: string; fighting: boolean; weeks: number; front: number; support: number;
  talks: number; troops: number; need: number; kia: number; cost: number; strategy: number; authorized: boolean; timetable: number; handoff: number; unrest: number }
export interface SitRoomView {
  date: string; open: boolean; reason?: string; defcon?: number; message?: string | null; callsLeft?: number; countries?: SitCountry[];
  summit?: { country: string; inWeeks: number; round: number };
  brief?: { priority: number; text: string }[];
  hotspots?: { name: string; stage: string; gauges: SitGauge[]; note: string }[];
  operations?: { index: number; name: string; desc: string; odds: number; ready: boolean; status: string }[];
  covert?: { index: number; name: string; desc: string; odds: number; leak: number; ready: boolean; status: string }[];
  counters?: { operations: number; covert: number; exposed: number; crises: number; deals: number; kia: number; ok: number; failed: number };
  tariffs?: string[]; levels?: string[]; aidLevels?: string[];
  alliances?: { nato: number; pacific: number; allySpend: number; deterrence: number; article5: boolean; press: boolean; drills: boolean;
    posture: { area: string; level: number }[]; postureLevels: string[] };
  wars?: SitWar[]; strategies?: string[]; strategyHelp?: string[]; record?: string[];
  flash?: { name: string; x: number; y: number }[];
}
export interface ForeignCountryView {
  open: boolean; id: string; name: string; nameIn: string; leader: string; title: string; align: string; region: string; rel: number; nuclear: boolean; power: number;
  ties: number; tiesLevels: string[]; unrest: number; toppled: boolean; atWar: boolean; trade: number; balance: number; tariff: number; sanction: number; aid: number;
  milAid: number; milAidLevels: string[]; arms: number; armsLevels: string[]; troops: number; troopStep: number; troopSteps: string[];
  peacekeepers: boolean; canKeepPeace: boolean; pact: boolean; tradeOdds: number; treaty: string; treatyReady: boolean;
  covert: { kind: number; name: string; desc: string; odds: number; leak: number; weeks: number; ready: boolean; reason: string }[];
  program?: string;
  force: { kind: number; name: string; desc: string; troops: number; expect: string; ready: boolean; reason: string }[];
  authorized: boolean; authorizeOdds: number;
  objectives: { id: string; name: string; why: string; steps: number; step: number; status: string; next?: string; reason?: string; odds?: string; ready?: boolean }[];
}

/** The transition office (milestone 10): the agenda and the White House staff, between the election and the oath. */
export interface TransitionProspect { id: string; name: string; profile: string; kind: string; skill: number; loyalty: number; interviewed: boolean;
  trait: string | null; traitDesc: string | null; checked: boolean; baggage: string | null; canInterview: boolean; canCheck: boolean; canAnnounce: boolean }
export interface TransitionView {
  phase: string; date: string; open: boolean; inauguration: string; president: string; party: string; ev?: number; evOpp?: number; popular?: number; congress: string;
  bills: { id: string; name: string; issue: string; cost: number; appeal: number; needs60: boolean; pledged: boolean; chosen: number }[];
  agenda: string[]; broken: number; hours?: number; hoursMax?: number;
  jobs?: { role: number; title: string; does: string; pick: string | null; prospects: TransitionProspect[] }[];
  score?: number; grade?: string; news?: string[];
}

/** Terms of office (milestone 9): the term, the run question, the race to succeed the president, the legacy goals. */
export interface TermsContender { index?: number; name: string; party: string; home: string; role: string; share: number; delegates: number;
  charisma: number; debate: number; endorsed?: boolean; nominee?: boolean }
export interface TermsView {
  open: boolean; term?: number; termWeek?: number; lameDuck?: number; running?: string; challenger?: string; challengerShare?: number;
  leaving?: boolean; successor?: string; handoff?: string; theme?: string; goals?: { label: string; met: boolean }[];
  race?: { year: number; electionDay: string; rivals: TermsContender[]; canEndorse: boolean; nominee: TermsContender | null; opponent: TermsContender | null;
    split: number; contests: number; national?: number; forecast?: number; done: boolean; won?: boolean; ev?: number[]; popular?: number[];
    primaries: string[]; season: boolean };
  record?: string[];
}

/** The verdict on a finished presidency: the historians' ten measures, the total, the rank and the record. */
export interface VerdictView {
  over: boolean; score?: number; grade?: string; rank?: string | null; measures?: { name: string; score: number }[]; lines?: string[];
  president: string; date: string; terms: number; record?: string[]; summary: string;
}

export interface PressView {
  date: string; open: boolean; reason?: string;
  outlets?: { index: number; name: string; audience: number; interviews: boolean; tone: number; label: string }[];
  relations?: number; presserIn?: number; interviewIn?: number; addressIn?: number; busy?: boolean; postsLeft?: number; followers?: number; tones?: string[];
  lastPoll?: string | null; pressers?: number; interviews?: number; speeches?: number; sotu?: string;
  wings?: { name: string; mood: number }[]; record?: string; opposition?: string; oppLeaders?: string[]; issues?: { id: string; label: string }[];
}

// ---- the Strait of Hormuz crisis (docs/HORMUZ.md) ------------------------------------------------------------------------
export interface HormuzSite { id: string; name: string; kind: 'missiles' | 'boats' | 'drones' | 'mines'; lon: number; lat: number; seen: number; struck: number }
export interface HormuzField { id: string; name: string; lon: number; lat: number; known: boolean; mines: number | null }
export interface HormuzView {
  date: string; open: boolean; reason?: string;
  active: boolean; day: number; started: string; cause: string; outcome: string | null; message: string | null;
  rungs: { name: string; desc: string }[]; rung: number; peakRung: number;
  iran: { tehran: number; guards: number; stability: number; read: string };
  sites: HormuzSite[]; fields: HormuzField[];
  oil: { normal: number; flow: number; bypass: number; bypassTarget: number; spr: number; sprLeft: number; sprStart: number; iea: number; shortfall: number;
    premium: number; insurance: number; reinsured: boolean; bypassAsked: boolean; price: number | null; gas: number | null };
  us: { escorts: number; sweepers: number; allies: number; hostsConsent: boolean; israelRestrained: boolean; gulfCalm: number; talks: number; pressure: number;
    settled: boolean; envoyIn: number; pressIn: number };
  toll: { killed: number; shipsHit: number; seized: number; tankersHit: number; strikes: number };
  invasion: { stage: number; option: number; days: number; progress: number; plans: { label: string; detail: string }[] };
  log: string[];
}

// ---- the Taiwan crisis (docs/ISLAND_CHAIN.md) ------------------------------------------------------------------------------
/** A sea zone: the player's own forces in it, and the enemy as the player's side sees it (contacts). */
export interface TaiwanZone {
  id: string; name: string; lon: number; lat: number; km: number; strait: boolean;
  own: { ships: number; groups: number; subs: number; carriers: number; amphibious: number; names: string[] };
  enemy: { ships: number; subs: number; carriers: number; amphibious: number; contacts: number; known: number; age: number };
  mines: number;
}
export interface TaiwanSector { name: string; terrain: string; prc: number; contested: boolean; lodgment: boolean }
export interface TaiwanAlly { id: string; name: string; level: number; levelName: string; basing: boolean; overflight: boolean; belligerent: boolean;
  attacked: boolean; sanctions: boolean; role: number; aligned: boolean }
export interface TaiwanProgramme { id: string; name: string; pct: number; on: boolean; days: number }
export interface TaiwanLosses { nation: string; label: string; killed: number; ships: number; carriers: number; amphibious: number; submarines: number; aircraft: number }
export interface TaiwanView {
  date: string; open: boolean; reason?: string;
  active: boolean; side: 'US' | 'PRC'; standalone: boolean; phase: 'crisis' | 'war' | 'over';
  day: number; hourOfDay: number; warDays: number; turnHours: number; year: number; preset: number; commitment: string;
  message: string | null; outcome: string | null; endReason: string | null; status: string | null; score: number | null; grade: string | null;
  rungs: { us: string[]; prc: string[] }; rung: { us: number; prc: number }; maxRung: number;
  nuclear: { risk: number; crisis: boolean; us: number; prc: number; postures: string[] };
  warning: { level: number; name: string; levels: string[]; chance: number; plans: { plan: string; share: number }[];
    indicators: { day: number; text: string; confidence: number; cover: boolean }[] };
  china: { plan: string; known: boolean; dDay: number; stoodDown: boolean; deterrence: number; region: number; lethality: number;
    options: { strikeUS: boolean; strikeJapan: boolean; cyber: boolean; decapitation: boolean; penghuFirst: boolean } };
  taiwan: { electricity: number; chips: number; will: number; command: number; throughput: number; blockade: number; blockadeName: string;
    lng: number; coal: number; oil: number; rationing: number; mobilization: number; territory: number; plaAshore: number; rocReady: number;
    civilians: number; asked: { mobilise: boolean; ration: boolean; disperse: boolean } };
  sectors: TaiwanSector[]; zones: TaiwanZone[];
  directives: { mainland: number; fleet: number; subs: number; munitions: number; air: number[]; convoys: boolean; held: string[]; preempt: boolean };
  allies: TaiwanAlly[]; capital: number;
  programmes: TaiwanProgramme[];
  economy: { brent: number; lng: number; warRisk: number; equity: number; gapUS: number; gapWorld: number; chipShock: number; sanctions: number;
    measures: string[]; support: number; approval: number | null; congress: number | null };
  losses: TaiwanLosses[];
  offer: { from: 'US' | 'PRC'; terms: number } | null;
  sitrep: string[]; log: string[];
}
