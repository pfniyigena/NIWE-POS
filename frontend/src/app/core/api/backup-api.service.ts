import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

export interface BackupFile {
  filename: string;
  sizeBytes: number;
  createdAt: string;
}

/** Admin > Data > Backup & Restore (Fourier: "no backup/restore ... anywhere in the backend"). See BackupController on the backend. */
@Injectable({ providedIn: 'root' })
export class BackupApiService {
  constructor(private http: HttpClient) {}

  list(): Observable<BackupFile[]> {
    return this.http.get<BackupFile[]>(`${API_BASE}/backups`);
  }

  createNow(): Observable<BackupFile> {
    return this.http.post<BackupFile>(`${API_BASE}/backups`, {});
  }

  download(filename: string): Observable<Blob> {
    return this.http.get(`${API_BASE}/backups/${encodeURIComponent(filename)}/download`, { responseType: 'blob' });
  }

  restoreFromStored(filename: string): Observable<void> {
    return this.http.post<void>(`${API_BASE}/backups/${encodeURIComponent(filename)}/restore`, {});
  }

  restoreFromUpload(file: File): Observable<void> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<void>(`${API_BASE}/backups/restore-upload`, formData);
  }

  delete(filename: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/backups/${encodeURIComponent(filename)}`);
  }
}
