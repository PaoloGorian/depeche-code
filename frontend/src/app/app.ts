import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { BrandLogo } from './brand-logo';
import { OfficeMap } from './office-map/office-map';
import { AiText, Area, Booking, DayRecommendation, OfficeNeed, Recommendation, User } from './models';
import { PlannerApi } from './planner-api';

interface InviteDialog {
  day: DayRecommendation;
  selected: Set<string>;
  draft: AiText | null;
  loading: boolean;
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
  readonly invite = signal<InviteDialog | null>(null);
  readonly toast = signal<string | null>(null);
  readonly expanded = signal<Set<string>>(new Set());

  // UI Figma: login, giorno mostrato sulla mappa, pannello "Pianifica con l'AI"
  readonly loggedIn = signal(false);
  readonly loginEmail = signal('');
  readonly loginError = signal<string | null>(null);
  readonly plannerOpen = signal(false);
  readonly mapDay = signal<string | null>(null);

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

  readonly goCount = computed(() => this.recommendation()?.days.filter((d) => d.verdict === 'GO').length ?? 0);

  /** Prenotazione dell'utente nel giorno mostrato sulla mappa. */
  readonly myBooking = computed(
    () => this.bookings().find((b) => b.userId === this.selectedUserId() && b.date === this.mapDay() && b.areaId) ?? null,
  );

  /** Raccomandazione del motore per il giorno mostrato sulla mappa. */
  readonly dayRec = computed(() => this.recommendation()?.days.find((d) => d.date === this.mapDay()) ?? null);

  /** Frase della card "Oggi sei qui": quanti colleghi del team sono nella stessa area. */
  readonly teamTodayText = computed(() => {
    const me = this.selectedUser();
    const booking = this.myBooking();
    if (!me || !booking) return '';
    const mates = this.bookings()
      .filter((b) => b.date === booking.date && b.areaId === booking.areaId && b.userId !== me.id)
      .map((b) => this.userById(b.userId))
      .filter((u): u is User => !!u);
    if (!mates.length) return 'Per ora sei l\'unico in quest\'area';
    const names = mates.slice(0, 2).map((u) => u.firstName).join(' e ');
    return mates.length > 2 ? `Con ${names} e altri ${mates.length - 2}` : `Con ${names}`;
  });

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
      const today = new Date().toISOString().slice(0, 10);
      this.mapDay.set(week.includes(today) ? today : (week[0] ?? null));
      this.loginEmail.set(users[0]?.email ?? '');
    });
  }

  login(): void {
    const email = this.loginEmail().trim().toLowerCase();
    const user = this.users().find((u) => u.email.toLowerCase() === email);
    if (!user) {
      this.loginError.set('Email non trovata tra gli utenti demo');
      return;
    }
    this.loginError.set(null);
    this.loggedIn.set(true);
    this.selectUser(user.id);
  }

  logout(): void {
    this.plannerOpen.set(false);
    this.invite.set(null);
    this.loggedIn.set(false);
  }

  openPlanner(): void {
    this.plannerOpen.set(true);
  }

  selectUser(id: string | undefined): void {
    if (!id) return;
    this.selectedUserId.set(id);
    this.expanded.set(new Set());
    this.refresh();
  }

  refresh(withSummary = true): void {
    const id = this.selectedUserId();
    if (!id) return;
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

  book(day: DayRecommendation): void {
    const user = this.selectedUser();
    if (!user) return;
    this.api.book({ userId: user.id, date: day.date, areaId: day.areaId }).subscribe({
      next: () => {
        this.showToast(`Prenotato ${this.dayLabel(day.date)} in ${this.areaName(day.areaId)}`);
        this.refresh();
      },
      error: (e) => this.showToast(e.error?.message ?? 'Prenotazione non riuscita'),
    });
  }

  openInvite(day: DayRecommendation): void {
    this.invite.set({ day, selected: new Set(day.inviteUserIds), draft: null, loading: false });
    this.generateDraft();
  }

  toggleInvitee(id: string): void {
    const dialog = this.invite();
    if (!dialog) return;
    const selected = new Set(dialog.selected);
    selected.has(id) ? selected.delete(id) : selected.add(id);
    this.invite.set({ ...dialog, selected });
  }

  generateDraft(): void {
    const dialog = this.invite();
    const user = this.selectedUser();
    if (!dialog || !user || dialog.selected.size === 0) return;
    this.invite.set({ ...dialog, loading: true });
    this.api.inviteDraft(user.id, dialog.day.date, [...dialog.selected]).subscribe({
      next: (draft) => this.invite.update((d) => (d ? { ...d, draft, loading: false } : d)),
      error: () => this.invite.update((d) => (d ? { ...d, loading: false } : d)),
    });
  }

  updateDraftText(text: string): void {
    this.invite.update((d) => (d && d.draft ? { ...d, draft: { ...d.draft, text } } : d));
  }

  sendInvite(): void {
    const dialog = this.invite();
    const user = this.selectedUser();
    if (!dialog || !user) return;
    this.api.acceptInvites(user.id, dialog.day.date, [...dialog.selected]).subscribe((created) => {
      this.invite.set(null);
      this.showToast(
        `Invito inviato. ${created.length} ${created.length === 1 ? 'collega ha' : 'colleghi hanno'} accettato e prenotato`,
      );
      this.refresh();
    });
  }

  resetDemo(): void {
    this.api.reset().subscribe(() => {
      this.showToast('Dati demo ripristinati');
      this.refresh();
    });
  }

  toggleReasons(date: string): void {
    const next = new Set(this.expanded());
    next.has(date) ? next.delete(date) : next.add(date);
    this.expanded.set(next);
  }

  // ---------------------------------------------------------------- helpers per il template

  userById(id: string): User | undefined {
    return this.users().find((u) => u.id === id);
  }

  areaName(id: string | null): string {
    return this.areas().find((a) => a.id === id)?.name ?? 'Nessuna area';
  }

  initials(u: User | undefined): string {
    return u ? `${u.firstName[0]}${u.lastName[0]}` : '?';
  }

  dayLabel(iso: string): string {
    return new Date(iso + 'T12:00:00').toLocaleDateString('it-IT', { weekday: 'long', day: 'numeric' });
  }

  dayShort(iso: string): string {
    return new Date(iso + 'T12:00:00').toLocaleDateString('it-IT', { weekday: 'short', day: 'numeric' });
  }

  needLabel(need: OfficeNeed): string {
    return { REQUIRED: 'Obbligatorio', OPTIONAL: 'Jolly 🃏', FREE: 'Libero' }[need];
  }

  scoreWidth(score: number): number {
    return Math.min(100, Math.round((score / 160) * 100));
  }

  private showToast(message: string): void {
    this.toast.set(message);
    setTimeout(() => this.toast.set(null), 3500);
  }
}
