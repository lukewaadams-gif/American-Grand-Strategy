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
export interface Decision { kind: string; eyebrow: string; title: string; body: string; options: DecisionOption[]; }

// ---- the presidency ----------------------------------------------------------------------------------------------

export interface PresidencyBill { id: string; name: string; stage: string; }

/** What the Oval Office shows (EngineApi.presidency). Figures that do not exist yet (before the first reading) are null. */
export interface PresidencyView {
  phase: string; date: string; dateLabel: string;
  president: { name: string; id: string; party: 'D' | 'R'; partyName: string };
  approval: number | null; approvalChange: number | null;
  unemployment?: number | null; inflation?: number | null; growth?: number | null; gas: number | null;
  termWeek: number; termWeeks: number;
  pendingDecisions: number; congress: string;
  /** The length of the turn the End button plays: 'week', 'month', 'transition', or null when there is none. */
  turnUnit: 'week' | 'month' | 'transition' | null;
  upcoming: { date: string; what: string }[];
  bills: PresidencyBill[];
  news: NewsItem[];
}
