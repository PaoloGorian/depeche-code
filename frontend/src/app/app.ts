import { Component, computed, ElementRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { BrandLogo } from './brand-logo';
import { OfficeMap } from './office-map/office-map';
import {
  AiText, Area, Booking, ChatMessage, ChatTurn, DayRecommendation, DayStatus, OfficeNeed, Recommendation, User,
} from './models';
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
  readonly invite = signal<InviteDialog | null>(null);
  readonly toast = signal<string | null>(null);
  readonly expanded = signal<Set<string>>(new Set());

  // UI Figma: login, giorno mostrato sulla mappa, pannello "Pianifica con l'AI"
  readonly loggedIn = signal(false);
  readonly loginEmail = signal('');
  readonly loginError = signal<string | null>(null);
  readonly plannerOpen = signal(false);
  readonly mapDay = signal<string | null>(null);

  // Chat "Pianifica con l'AI": il primo messaggio e' il consiglio per l'utente loggato
  readonly messages = signal<ChatMessage[]>([]);
  readonly chatLoading = signal(false);
  readonly chatDraft = signal('');
  readonly suggestions = [
    'Giovedì ho un cliente in sede',
    'Mercoledì sono dal medico, lavoro da casa',
    'Con chi mi conviene andare in ufficio?',
  ];
  private readonly chatScroll = viewChild<ElementRef<HTMLElement>>('chatScroll');
  /** Indice dell'ultimo messaggio che mostra le card del piano: le card compaiono una volta sola. */
  readonly planIndex = computed(() => this.messages().map((m) => !!m.showPlan).lastIndexOf(true));

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
    this.scrollChat();
  }

  selectUser(id: string | undefined): void {
    if (!id) return;
    this.selectedUserId.set(id);
    this.expanded.set(new Set());
    this.refresh();
    this.startChat();
  }

  /** Ricarica prenotazioni, profili (gli impegni cambiano dalla chat) e piano del motore. */
  refresh(): void {
    const id = this.selectedUserId();
    if (!id) return;
    this.api.bookings().subscribe((b) => this.bookings.set(b));
    this.api.users().subscribe((u) => this.users.set(u));
    this.api.recommendation(id).subscribe((r) => this.recommendation.set(r));
  }

  // ---------------------------------------------------------------- chat con l'AI

  /** Nuova conversazione: il primo messaggio e' il consiglio dell'AI per l'utente loggato. */
  startChat(): void {
    const id = this.selectedUserId();
    if (!id) return;
    this.messages.set([]);
    this.chatDraft.set('');
    this.askAi(id, []);
  }

  sendMessage(text = this.chatDraft()): void {
    const id = this.selectedUserId();
    const content = text.trim();
    if (!id || !content || this.chatLoading()) return;
    this.chatDraft.set('');
    this.messages.update((list) => [...list, { role: 'user', content }]);
    const history: ChatTurn[] = this.messages()
      .filter((m) => !m.error)
      .map(({ role, content }) => ({ role, content }));
    this.askAi(id, history);
  }

  onChatKey(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.sendMessage();
    }
  }

  private askAi(userId: string, history: ChatTurn[]): void {
    this.chatLoading.set(true);
    this.scrollChat();
    this.api.chat(userId, history).subscribe({
      next: ({ reply, commitments }) => {
        if (this.selectedUserId() !== userId) return; // l'utente e' cambiato nel frattempo
        const opening = history.length === 0;
        this.messages.update((list) => [
          ...list,
          { role: 'assistant', content: reply.text, ai: reply, commitments, showPlan: opening || commitments.length > 0 },
        ]);
        this.chatLoading.set(false);
        if (commitments.length) this.refresh();
        this.scrollChat();
      },
      error: () => {
        this.messages.update((list) => [
          ...list,
          { role: 'assistant', content: 'Non riesco a contattare l\'assistente. Riprova tra poco.', error: true },
        ]);
        this.chatLoading.set(false);
        this.scrollChat();
      },
    });
  }

  private scrollChat(): void {
    setTimeout(() => {
      const el = this.chatScroll()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    });
  }

  commitmentLabel(c: DayStatus): string {
    const what = { REQUIRED: 'in ufficio', OPTIONAL: 'evento facoltativo', REMOTE: 'da remoto', FREE: 'impegno annullato' }[c.officeNeed];
    return `${this.dayShort(c.date)} · ${what}${c.note ? ' · ' + c.note : ''}`;
  }

  // ---------------------------------------------------------------- prenotazioni

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

  cancelBooking(date: string): void {
    const user = this.selectedUser();
    if (!user) return;
    this.api.cancelBooking(user.id, date).subscribe({
      next: () => {
        this.showToast(`Prenotazione di ${this.dayLabel(date)} annullata`);
        this.refresh();
      },
      error: (e) => this.showToast(e.error?.message ?? 'Annullamento non riuscito'),
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
      this.startChat();
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
    return { REQUIRED: 'Obbligatorio', OPTIONAL: 'Jolly 🃏', FREE: 'Libero', REMOTE: 'Da remoto' }[need];
  }

  scoreWidth(score: number): number {
    return Math.min(100, Math.round((score / 160) * 100));
  }

  private showToast(message: string): void {
    this.toast.set(message);
    setTimeout(() => this.toast.set(null), 3500);
  }
}
