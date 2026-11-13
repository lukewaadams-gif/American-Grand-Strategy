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
}

export interface DecisionOption { label: string; detail: string; tag: string | null; tone: Tone | null; command: object; }
export interface Decision { kind: string; eyebrow: string; title: string; body: string; options: DecisionOption[]; }
