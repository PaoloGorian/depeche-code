export type Seniority = 'JUNIOR' | 'SENIOR';
export type Skill = 'FE' | 'BE' | 'DATA' | 'EMBEDDED' | 'AI';
export type OfficeNeed = 'REQUIRED' | 'OPTIONAL' | 'FREE';
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
