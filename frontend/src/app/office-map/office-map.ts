import { Component, computed, input, signal } from '@angular/core';
import { Area, Booking, User } from '../models';
import { buildScene } from './office-map.layout';

/**
 * Mappa isometrica dell'ufficio, solo in visualizzazione.
 * Ogni area e' un'isola di scrivanie; i pallini sopra sono le persone prenotate nel giorno scelto.
 * La collocazione la decide il motore: qui si guarda soltanto chi c'e' e dove.
 */
@Component({
  selector: 'dc-office-map',
  template: `
    <section class="map">
      <header class="map-head">
        <div>
          <p class="eyebrow">{{ dayLong(currentDay()) }} · solo visualizzazione</p>
          <h2>{{ inOffice() }} in Box</h2>
        </div>
        <div class="days" role="tablist" aria-label="Giorno">
          @for (d of week(); track d) {
            <button type="button" role="tab" [class.active]="d === currentDay()" [attr.aria-selected]="d === currentDay()"
                    (click)="day.set(d)">{{ dayShort(d) }}</button>
          }
        </div>
      </header>

      <svg class="iso" [attr.viewBox]="scene().viewBox" role="img" aria-label="Mappa delle aree e delle persone in ufficio">
        @for (p of scene().floor; track $index) {
          <polygon [attr.points]="p.points" [attr.fill]="p.fill" />
        }
        @for (z of scene().zones; track z.areaId) {
          <g class="zone">
            @for (p of z.polys; track $index) {
              <polygon [attr.points]="p.points" [attr.fill]="p.fill" [attr.stroke]="p.stroke ?? null"
                       stroke-width="1" stroke-linejoin="round" />
            }
          </g>
        }
        @for (p of scene().pins; track p.userId) {
          <g class="pin pin-{{ p.role }}" [attr.transform]="'translate(' + p.x + ' ' + p.y + ')'">
            <title>{{ p.title }}</title>
            <ellipse cx="0" cy="0" rx="10" ry="4.5" class="pin-shadow" />
            @if (p.role === 'me') {
              <circle cx="0" cy="-15" r="20" class="pin-halo" />
            }
            <circle cx="0" cy="-15" r="12" class="pin-face" />
            <circle cx="0" cy="-17.5" r="3.5" class="pin-head" />
            <path d="M-6.25 -7.5 C-5.6 -11 -3 -13 0 -13 S5.6 -11 6.25 -7.5 Z" class="pin-head" />
          </g>
        }
        @for (l of scene().labels; track l.areaId) {
          <g class="label" [attr.transform]="'translate(' + l.x + ' ' + l.y + ')'">
            <rect [attr.x]="-l.width / 2" y="-13" [attr.width]="l.width" height="26" rx="13" class="label-pill" />
            <circle [attr.cx]="-l.width / 2 + 14" cy="0" r="4" [attr.fill]="l.color" />
            <text [attr.x]="-l.width / 2 + 24" y="4">
              <tspan class="label-name">{{ l.name }}</tspan>
              <tspan class="label-count" [class.full]="l.full" dx="6">{{ l.count }}</tspan>
            </text>
          </g>
        }
      </svg>

      <footer class="legend">
        <span><svg viewBox="-14 -29 28 28" width="18" height="18"><g class="pin pin-senior"><circle cx="0" cy="-15" r="12" class="pin-face" /><circle cx="0" cy="-17.5" r="3.5" class="pin-head" /><path d="M-6.25 -7.5 C-5.6 -11 -3 -13 0 -13 S5.6 -11 6.25 -7.5 Z" class="pin-head" /></g></svg>Senior Randstad</span>
        <span><svg viewBox="-14 -29 28 28" width="18" height="18"><g class="pin pin-junior"><circle cx="0" cy="-15" r="12" class="pin-face" /><circle cx="0" cy="-17.5" r="3.5" class="pin-head" /><path d="M-6.25 -7.5 C-5.6 -11 -3 -13 0 -13 S5.6 -11 6.25 -7.5 Z" class="pin-head" /></g></svg>Junior · studenti TTF</span>
        <span><svg viewBox="-14 -29 28 28" width="18" height="18"><g class="pin pin-me"><circle cx="0" cy="-15" r="12" class="pin-face" /><circle cx="0" cy="-17.5" r="3.5" class="pin-head" /><path d="M-6.25 -7.5 C-5.6 -11 -3 -13 0 -13 S5.6 -11 6.25 -7.5 Z" class="pin-head" /></g></svg>Tu</span>
      </footer>
    </section>
  `,
  styles: `
    :host { display: block; }
    .map { background: var(--card); border: 1px solid var(--line); border-radius: 24px; padding: 22px 24px 18px; }
    .map-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
    .eyebrow { margin: 0; font-size: 13px; color: var(--muted); }
    h2 { margin: 4px 0 0; font-size: 30px; font-weight: 700; letter-spacing: -.02em; color: var(--ink); }
    .days { display: flex; gap: 2px; padding: 4px; border-radius: 999px; background: var(--bg); }
    .days button { border: 0; background: transparent; color: var(--muted); padding: 8px 14px; border-radius: 999px; font-size: 13px; font-weight: 600; text-transform: capitalize; }
    .days button.active { background: var(--ink); color: #fff; }
    .iso { display: block; width: 100%; height: auto; margin-top: 8px; }
    .pin-shadow { fill: #14142b; opacity: .16; }
    .pin-face { stroke-width: 2; }
    .pin-senior .pin-face { fill: #14142b; stroke: #fff; }
    .pin-senior .pin-head { fill: #fff; }
    .pin-junior .pin-face { fill: #fff; stroke: #14142b; }
    .pin-junior .pin-head { fill: #14142b; }
    .pin-me .pin-face { fill: var(--accent); stroke: #fff; }
    .pin-me .pin-head { fill: #fff; }
    .pin-halo { fill: var(--accent); opacity: .22; }
    .pin { cursor: default; }
    .label-pill { fill: #fff; filter: drop-shadow(0 4px 10px rgba(20, 20, 43, .1)); }
    .label text { font-size: 11.5px; }
    .label-name { font-weight: 600; fill: var(--ink); }
    .label-count { font-weight: 500; fill: var(--muted); }
    .label-count.full { fill: var(--accent); }
    .legend { display: flex; justify-content: flex-end; gap: 20px; flex-wrap: wrap; font-size: 12px; color: var(--ink); }
    .legend span { display: inline-flex; align-items: center; gap: 7px; }
  `,
})
export class OfficeMap {
  readonly areas = input.required<Area[]>();
  readonly bookings = input.required<Booking[]>();
  readonly users = input.required<User[]>();
  readonly week = input.required<string[]>();
  readonly meId = input<string | null>(null);

  /** Giorno scelto dall'utente; se nullo si usa oggi (se e' nella settimana) o il primo giorno. */
  readonly day = signal<string | null>(null);

  readonly currentDay = computed(() => {
    const week = this.week();
    const chosen = this.day();
    if (chosen && week.includes(chosen)) return chosen;
    const today = new Date().toISOString().slice(0, 10);
    return week.includes(today) ? today : (week[0] ?? '');
  });

  private readonly bookingsOfDay = computed(() =>
    this.bookings().filter((b) => b.date === this.currentDay() && b.areaId !== null),
  );

  readonly inOffice = computed(() => this.bookingsOfDay().length);

  readonly scene = computed(() => buildScene(this.areas(), this.bookingsOfDay(), this.users(), this.meId()));

  dayShort(iso: string): string {
    return new Date(iso + 'T12:00:00').toLocaleDateString('it-IT', { weekday: 'short', day: 'numeric' });
  }

  dayLong(iso: string): string {
    if (!iso) return '';
    return new Date(iso + 'T12:00:00').toLocaleDateString('it-IT', { weekday: 'long', day: 'numeric', month: 'long' });
  }
}
