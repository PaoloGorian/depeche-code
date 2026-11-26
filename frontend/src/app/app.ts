import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { BrandLogo } from './brand-logo';
import { OfficeMap } from './office-map/office-map';
import { AiText, Area, Booking, Recommendation, User } from './models';
import { PlannerApi } from './planner-api';

interface ChatEntry {
  from: 'me' | 'ai';
  text: string;
  ok?: boolean;
  parser?: string;
}

@Component({
  selector: 'app-root',
  imports: [FormsModule, BrandLogo, OfficeMap],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly api = inject(PlannerApi);

  readonly week = signal<string[]>([]);
  readonly users = signal<User[]>([]);
  readonly areas = signal<Area[]>([]);
  readonly bookings = signal<Booking[]>([]);
  readonly selectedUserId = signal<string | null>(null);
  readonly recommendation = signal<Recommendation | null>(null);
  readonly summary = signal<AiText | null>(null);
  readonly summaryLoading = signal(false);
  readonly toast = signal<string | null>(null);

  readonly chatText = signal('');
  readonly chatBusy = signal(false);
  readonly chatLog = signal<ChatEntry[]>([]);
  readonly examples = [
    'Oggi Chiara ha un workshop obbligatorio',
    'Venerdì Marco Bianchi ha una demo al cliente',
    'Domani Federica Gatti ha un evento facoltativo',
    'Annulla l’impegno di Chiara di lunedì',
  ];

  readonly selectedUser = computed(() => this.users().find((u) => u.id === this.selectedUserId()) ?? null);

  readonly teams = computed(() => {
    const groups = new Map<string, User[]>();
    for (const u of this.users()) {
      groups.set(u.team, [...(groups.get(u.team) ?? []), u]);
    }
    return [...groups.entries()].map(([team, members]) => ({ team, members }));
  });

  /** Verdetto per giorno, usato dalla mappa per marcare i giorni GO/STAY. */
  readonly verdicts = computed<Record<string, 'GO' | 'STAY'>>(() =>
    Object.fromEntries((this.recommendation()?.days ?? []).map((d) => [d.date, d.verdict])),
  );

  readonly goDays = computed(() => (this.recommendation()?.days ?? []).filter((d) => d.verdict === 'GO'));

  ngOnInit(): void {
    forkJoin({
      week: this.api.week(),
      users: this.api.users(),
      areas: this.api.areas(),
      bookings: this.api.bookings(),
    }).subscribe(({ week, users, areas, bookings }) => {
      this.week.set(week);
      this.users.set(users);
      this.areas.set(areas);
      this.bookings.set(bookings);
      this.selectUser(users[0]?.id);
    });
  }

  selectUser(id: string | undefined, withSummary = true): void {
    if (!id) return;
    this.selectedUserId.set(id);
    this.refresh(withSummary);
  }

  refresh(withSummary = true): void {
    const id = this.selectedUserId();
    if (!id) return;
    this.api.users().subscribe((u) => this.users.set(u));
    this.api.bookings().subscribe((b) => this.bookings.set(b));
    this.api.recommendation(id).subscribe((r) => this.recommendation.set(r));
    if (withSummary) {
      this.summary.set(null);
      this.summaryLoading.set(true);
      this.api.summary(id).subscribe({
        next: (s) => {
          this.summary.set(s);
          this.summaryLoading.set(false);
        },
        error: () => this.summaryLoading.set(false),
      });
    }
  }

  sendChat(text = this.chatText()): void {
    const message = text.trim();
    if (!message || this.chatBusy()) return;
    this.chatLog.update((log) => [...log, { from: 'me', text: message }]);
    this.chatText.set('');
    this.chatBusy.set(true);
    this.summaryLoading.set(true);
    this.api.chat(this.selectedUserId(), message).subscribe({
      next: (res) => {
        this.chatLog.update((log) => [
          ...log,
          { from: 'ai', text: res.reply, ok: res.ok, parser: res.command?.parser },
        ]);
        this.summary.set(res.summary);
        this.summaryLoading.set(false);
        this.chatBusy.set(false);
        this.selectUser(res.userId, false);
      },
      error: () => {
        this.chatLog.update((log) => [...log, { from: 'ai', text: 'Errore di comunicazione con il server.', ok: false }]);
        this.summaryLoading.set(false);
        this.chatBusy.set(false);
      },
    });
  }

  resetDemo(): void {
    this.api.reset().subscribe(() => {
      this.chatLog.set([]);
      this.showToast('Dati demo ripristinati');
      this.refresh();
    });
  }

  // ---------------------------------------------------------------- helpers per il template

  initials(u: User | undefined | null): string {
    return u ? `${u.firstName[0]}${u.lastName[0]}` : '?';
  }

  dayLabel(iso: string): string {
    return new Date(iso + 'T12:00:00').toLocaleDateString('it-IT', { weekday: 'long', day: 'numeric' });
  }

  private showToast(message: string): void {
    this.toast.set(message);
    setTimeout(() => this.toast.set(null), 3500);
  }
}
