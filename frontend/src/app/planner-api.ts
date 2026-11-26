import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { AiText, Area, Booking, ChatResult, ChatTurn, Recommendation, User } from './models';

@Injectable({ providedIn: 'root' })
export class PlannerApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  week(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/week`);
  }

  users(): Observable<User[]> {
    return this.http.get<User[]>(`${this.base}/users`);
  }

  areas(): Observable<Area[]> {
    return this.http.get<Area[]>(`${this.base}/areas`);
  }

  bookings(): Observable<Booking[]> {
    return this.http.get<Booking[]>(`${this.base}/bookings`);
  }

  recommendation(userId: string): Observable<Recommendation> {
    return this.http.get<Recommendation>(`${this.base}/users/${userId}/recommendation`);
  }

  /** Chat con l'AI: con messages vuoto restituisce il consiglio iniziale. */
  chat(userId: string, messages: ChatTurn[]): Observable<ChatResult> {
    return this.http.post<ChatResult>(`${this.base}/users/${userId}/chat`, { messages });
  }

  book(booking: Booking): Observable<Booking> {
    return this.http.post<Booking>(`${this.base}/bookings`, booking);
  }

  cancelBooking(userId: string, date: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/bookings/${userId}/${date}`);
  }

  inviteDraft(fromUserId: string, date: string, toUserIds: string[]): Observable<AiText> {
    return this.http.post<AiText>(`${this.base}/invites/draft`, { fromUserId, date, toUserIds });
  }

  acceptInvites(fromUserId: string, date: string, toUserIds: string[]): Observable<Booking[]> {
    return this.http.post<Booking[]>(`${this.base}/invites/accept`, { fromUserId, date, toUserIds });
  }

  reset(): Observable<void> {
    return this.http.post<void>(`${this.base}/reset`, {});
  }
}
