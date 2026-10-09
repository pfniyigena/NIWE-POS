import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';
import { CreateJournalEntryPayload, JournalEntry } from '../models';

/** Admin > Accounting > Journal Entries — the automatically-posted entries from every other module, plus manual adjustments entered here. Admin-only. */
@Injectable({ providedIn: 'root' })
export class JournalEntryApiService {
  constructor(private http: HttpClient) {}

  listAll(): Observable<JournalEntry[]> {
    return this.http.get<JournalEntry[]>(`${API_BASE}/accounting/journal-entries`);
  }

  getById(id: number): Observable<JournalEntry> {
    return this.http.get<JournalEntry>(`${API_BASE}/accounting/journal-entries/${id}`);
  }

  /** Backs the "New manual entry" screen — for one-off adjustments an admin needs to record by hand (e.g. rent, owner contributions). Must balance to the penny, see backend JournalEntryService.createManual's javadoc. */
  createManual(payload: CreateJournalEntryPayload): Observable<JournalEntry> {
    return this.http.post<JournalEntry>(`${API_BASE}/accounting/journal-entries`, payload);
  }
}
