export type Seniority = 'JUNIOR' | 'SENIOR';
export type Skill = 'FE' | 'BE' | 'DATA' | 'EMBEDDED' | 'AI';
export type OfficeNeed = 'REQUIRED' | 'OPTIONAL' | 'FREE' | 'REMOTE';
export type Verdict = 'GO' | 'STAY';

export interface DayStatus {
  date: string;
  officeNeed: OfficeNeed;
  note: string | null;
}

export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  seniority: Seniority;
  skills: Skill[];
  team: string;
  minOfficeDays: number;
  availability: DayStatus[];
}

export interface Area {
  id: string;
  name: string;
  skill: Skill;
  seats: number;
}

export interface Booking {
  userId: string;
  date: string;
  areaId: string | null;
}

export interface DayRecommendation {
  date: string;
  verdict: Verdict;
  officeNeed: OfficeNeed;
  note: string | null;
  areaId: string | null;
  score: number;
  reasons: string[];
  inviteUserIds: string[];
  teamPresent: number;
  areaFreeSeats: number;
  alreadyBooked: boolean;
  areaFull: boolean;
}

export interface Recommendation {
  userId: string;
  officeDaysTarget: number;
  days: DayRecommendation[];
}

export interface AiText {
  text: string;
  source: 'llm' | 'template';
  provider: string;
}

/** Turno di conversazione inviato al backend. */
export interface ChatTurn {
  role: 'user' | 'assistant';
  content: string;
}

/** Risposta della chat: testo dell'AI e impegni registrati dall'ultimo messaggio. */
export interface ChatResult {
  reply: AiText;
  commitments: DayStatus[];
}

/** Messaggio mostrato nella chat "Pianifica con l'AI". */
export interface ChatMessage extends ChatTurn {
  ai?: AiText;
  /** Impegni capiti dall'AI in risposta a questo messaggio. */
  commitments?: DayStatus[];
  /** Mostra sotto il messaggio le card del piano (sempre aggiornate). */
  showPlan?: boolean;
  error?: boolean;
}
