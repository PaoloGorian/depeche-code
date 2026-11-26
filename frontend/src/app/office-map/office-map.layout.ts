/**
 * Geometria della mappa isometrica dell'ufficio.
 * Funzioni pure (niente Angular) cosi' si possono testare e riusare.
 *
 * Mondo: piano 1000 x D unita'. Ogni area e' una zona colorata con muretti sul retro
 * e un'isola di scrivanie al centro; le persone prenotate stanno sopra la scrivania.
 */
import { Area, Booking, Skill, User } from '../models';

export interface IsoPoly {
  points: string;
  fill: string;
  stroke?: string;
}

export type PinRole = 'senior' | 'junior' | 'me';

export interface IsoPin {
  userId: string;
  x: number;
  y: number;
  role: PinRole;
  title: string;
}

export interface IsoLabel {
  areaId: string;
  x: number;
  y: number;
  width: number;
  name: string;
  count: string;
  color: string;
  full: boolean;
}

export interface IsoScene {
  viewBox: string;
  floor: IsoPoly[];
  zones: { areaId: string; polys: IsoPoly[] }[];
  pins: IsoPin[];
  labels: IsoLabel[];
}

/** Colore di ogni zona per skill (FE = blu Randstad): le uniche note di colore della mappa. */
export const SKILL_COLORS: Record<Skill, string> = {
  FE: '#2175D9',
  BE: '#7B5CFF',
  DATA: '#12B8A0',
  EMBEDDED: '#FF5A36',
  AI: '#FF5C93',
};
const FALLBACK_COLORS = ['#FFB21F', '#9BD400', '#2F6BFF', '#7B5CFF', '#12B8A0'];

const C30 = Math.cos(Math.PI / 6);
const S = 0.72; // scala di proiezione
const WORLD_W = 1000;
const ROW_D = 300;
const GAP = 20;
const WALL_H = 26;
const DESK_H = 12;
const DESK_D = 80;

interface Rect {
  x: number;
  y: number;
  w: number;
  d: number;
}

function project(x: number, y: number, z = 0): [number, number] {
  return [(x - y) * C30 * S, (x + y) * 0.5 * S - z * S];
}

function pts(list: [number, number][]): string {
  return list.map(([x, y]) => `${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
}

function hexToRgb(hex: string): [number, number, number] {
  const h = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16)) as [number, number, number];
}

/** Mescola il colore con il bianco (t=1 bianco) o con il nero (t<0). */
function tint(hex: string, t: number): string {
  const target = t >= 0 ? 255 : 0;
  const k = Math.abs(t);
  const [r, g, b] = hexToRgb(hex).map((c) => Math.round(c * (1 - k) + target * k));
  return `#${[r, g, b].map((c) => c.toString(16).padStart(2, '0')).join('')}`;
}

/** Parallelepipedo: facce visibili top, lato +y, lato +x. */
function box(r: Rect, h: number, color: string): IsoPoly[] {
  const P = project;
  const { x, y, w, d } = r;
  return [
    { points: pts([P(x, y + d), P(x + w, y + d), P(x + w, y + d, h), P(x, y + d, h)]), fill: tint(color, -0.25) },
    { points: pts([P(x + w, y), P(x + w, y + d), P(x + w, y + d, h), P(x + w, y, h)]), fill: tint(color, -0.42) },
    { points: pts([P(x, y, h), P(x + w, y, h), P(x + w, y + d, h), P(x, y + d, h)]), fill: color },
  ];
}

/** Hash stabile per piccoli scostamenti: i pallini non sembrano su una griglia. */
function jitter(seed: string, amount: number): number {
  let h = 0;
  for (const ch of seed) h = (h * 31 + ch.charCodeAt(0)) | 0;
  return ((Math.abs(h) % 1000) / 1000 - 0.5) * 2 * amount;
}

/** Divide le aree in righe da max 3, ognuna larga quanto il piano. */
function zoneRects(count: number): Rect[] {
  const rects: Rect[] = [];
  const rows = Math.max(1, Math.ceil(count / 3));
  let placed = 0;
  for (let r = 0; r < rows; r++) {
    const inRow = Math.ceil((count - placed) / (rows - r));
    const w = (WORLD_W - GAP * (inRow + 1)) / inRow;
    for (let c = 0; c < inRow; c++) {
      rects.push({ x: GAP + c * (w + GAP), y: GAP + r * (ROW_D + GAP), w, d: ROW_D });
    }
    placed += inRow;
  }
  return rects;
}

export function buildScene(
  areas: Area[],
  bookingsOfDay: Booking[],
  users: User[],
  meId: string | null,
): IsoScene {
  const rects = zoneRects(areas.length);
  const rows = Math.max(1, Math.ceil(areas.length / 3));
  const worldD = GAP + rows * (ROW_D + GAP);
  const userById = new Map(users.map((u) => [u.id, u]));

  // pavimento
  const T = 18;
  const P = project;
  const floor: IsoPoly[] = [
    { points: pts([P(0, worldD, -T), P(WORLD_W, worldD, -T), P(WORLD_W, worldD), P(0, worldD)]), fill: '#DDE5EF' },
    { points: pts([P(WORLD_W, 0, -T), P(WORLD_W, worldD, -T), P(WORLD_W, worldD), P(WORLD_W, 0)]), fill: '#C9D5E3' },
    { points: pts([P(0, 0), P(WORLD_W, 0), P(WORLD_W, worldD), P(0, worldD)]), fill: '#F3F6FA' },
  ];

  const zones: IsoScene['zones'] = [];
  const pins: IsoPin[] = [];
  const labels: IsoLabel[] = [];

  areas.forEach((area, i) => {
    const z = rects[i];
    const color = SKILL_COLORS[area.skill] ?? FALLBACK_COLORS[i % FALLBACK_COLORS.length];
    const { x: x0, y: y0 } = z;
    const x1 = z.x + z.w;
    const y1 = z.y + z.d;

    const polys: IsoPoly[] = [
      { points: pts([P(x0, y0), P(x1, y0), P(x1, y1), P(x0, y1)]), fill: tint(color, 0.84), stroke: tint(color, 0.55) },
      { points: pts([P(x0, y0), P(x0, y1), P(x0, y1, WALL_H), P(x0, y0, WALL_H)]), fill: tint(color, 0.35) },
      { points: pts([P(x0, y0), P(x1, y0), P(x1, y0, WALL_H), P(x0, y0, WALL_H)]), fill: tint(color, 0.48) },
    ];

    // isola di scrivanie: piu' posti, piu' lunga
    const deskW = Math.min(z.w - 70, 80 + area.seats * 50);
    const desk: Rect = { x: x0 + (z.w - deskW) / 2, y: y0 + (z.d - DESK_D) / 2 - 10, w: deskW, d: DESK_D };
    polys.push(...box(desk, DESK_H, color));
    zones.push({ areaId: area.id, polys });

    // persone sopra la scrivania, distribuite lungo l'isola (nessun posto fisso disegnato)
    const here = bookingsOfDay.filter((b) => b.areaId === area.id);
    const slots = Math.max(area.seats, here.length);
    here.forEach((b, k) => {
      const wx = desk.x + ((k + 0.5) / slots) * desk.w + jitter(b.userId + 'x', 5);
      const wy = desk.y + desk.d / 2 + (k % 2 === 0 ? -8 : 8) + jitter(b.userId + 'y', 4);
      const [sx, sy] = P(wx, wy, DESK_H);
      const u = userById.get(b.userId);
      const role: PinRole = b.userId === meId ? 'me' : u?.seniority === 'SENIOR' ? 'senior' : 'junior';
      const title = u ? `${u.firstName} ${u.lastName} · ${u.seniority === 'SENIOR' ? 'Senior' : 'Junior'} ${u.skills.join('/')}` : b.userId;
      pins.push({ userId: b.userId, x: sx, y: sy, role, title });
    });

    // etichetta sul bordo anteriore della zona
    const [lx, ly] = P((x0 + x1) / 2, y1 - 18);
    const count = `${here.length}/${area.seats}`;
    const width = 40 + area.name.length * 6.6 + count.length * 6.6;
    labels.push({ areaId: area.id, x: lx, y: ly, width, name: area.name, count, color, full: here.length >= area.seats });
  });

  const depth = new Map(areas.map((a, i) => [a.id, rects[i].x + rects[i].y]));
  zones.sort((a, b) => (depth.get(a.areaId) ?? 0) - (depth.get(b.areaId) ?? 0));
  pins.sort((a, b) => a.y - b.y);

  // viewBox stretto attorno al pavimento (+ spazio per muretti e pallini)
  const corners = [P(0, 0), P(WORLD_W, 0), P(0, worldD), P(WORLD_W, worldD, -T)];
  const xs = corners.map((c) => c[0]);
  const ys = corners.map((c) => c[1]);
  const minX = Math.min(...xs) - 10;
  const maxX = Math.max(...xs) + 10;
  const minY = Math.min(...ys) - 50;
  const maxY = Math.max(...ys) + 10;
  const viewBox = `${minX.toFixed(0)} ${minY.toFixed(0)} ${(maxX - minX).toFixed(0)} ${(maxY - minY).toFixed(0)}`;

  return { viewBox, floor, zones, pins, labels };
}
