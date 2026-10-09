import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject } from 'rxjs';
import { API_BASE } from '../constants';

/**
 * Task: "local queuing of sales that sync once connectivity returns" — tracks whether this
 * terminal can actually reach the backend, which is a stricter question than the browser's own
 * `navigator.onLine` (true the moment a device has *any* network link, e.g. to a Wi-Fi router
 * with no working internet beyond it, or a backend that's simply down while the network is fine).
 * Backed by a periodic ping to the public `GET /api/health` endpoint, plus the browser's
 * online/offline events as a fast first signal.
 */
@Injectable({ providedIn: 'root' })
export class ConnectivityService {
  private static readonly PING_INTERVAL_MS = 20000;

  private readonly onlineSubject = new BehaviorSubject<boolean>(typeof navigator === 'undefined' ? true : navigator.onLine);
  readonly online$ = this.onlineSubject.asObservable();

  constructor(private http: HttpClient) {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => this.checkNow());
      window.addEventListener('offline', () => this.setOnline(false));
      setInterval(() => this.checkNow(), ConnectivityService.PING_INTERVAL_MS);
    }
    this.checkNow();
  }

  get isOnline(): boolean {
    return this.onlineSubject.value;
  }

  /** Pings the backend right now rather than waiting for the next scheduled check — used on terminal init and after any real HTTP call fails. */
  checkNow(): void {
    if (typeof navigator !== 'undefined' && !navigator.onLine) {
      this.setOnline(false);
      return;
    }
    this.http.get(`${API_BASE}/health`, { responseType: 'text' }).subscribe({
      next: () => this.setOnline(true),
      error: () => this.setOnline(false),
    });
  }

  /**
   * Called by other services the moment a normal API call fails with a genuine network error
   * (status 0 — the request never reached the server), so the terminal flips to offline mode
   * immediately instead of waiting up to `PING_INTERVAL_MS` for the next scheduled ping.
   */
  reportNetworkFailure(): void {
    this.setOnline(false);
  }

  private setOnline(value: boolean): void {
    if (this.onlineSubject.value !== value) {
      this.onlineSubject.next(value);
    }
  }
}
