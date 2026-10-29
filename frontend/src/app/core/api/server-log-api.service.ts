import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface ServerLogFile {
  name: string;
  size: number;
  modified: string;
}

export interface ServerLogEntry {
  time: string | null;
  level: string | null;
  text: string;
}

export interface ServerLogTail {
  file: string;
  size: number;
  modified: string;
  truncated: boolean;
  matched: number;
  entries: ServerLogEntry[];
}

/** v1.14.0: Admin > Server logs. */
@Injectable({ providedIn: 'root' })
export class ServerLogApiService {
  constructor(private http: HttpClient) {}

  files(): Observable<ServerLogFile[]> {
    return this.http.get<ServerLogFile[]>(`${API_BASE}/admin/server-logs`);
  }

  tail(name: string, lines: number, level: string, q: string): Observable<ServerLogTail> {
    const params: string[] = [`lines=${lines}`];
    if (level) params.push(`level=${encodeURIComponent(level)}`);
    if (q) params.push(`q=${encodeURIComponent(q)}`);
    return this.http.get<ServerLogTail>(`${API_BASE}/admin/server-logs/${encodeURIComponent(name)}?${params.join('&')}`);
  }

  download(name: string): Observable<Blob> {
    return this.http.get(`${API_BASE}/admin/server-logs/${encodeURIComponent(name)}/download`, { responseType: 'blob' });
  }
}
