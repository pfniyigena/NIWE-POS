import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE } from '../constants';

/** What one run of the daily stock jobs did — see the backend's StockAutomationService.RunResult. */
export interface StockAutomationResult {
  requisitionsEnabled: boolean;
  requisitionNumber: string | null;
  requisitionLines: number;
  digestEnabled: boolean;
  digestSent: boolean;
  lowStockCount: number;
  reorderCount: number;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class StockAutomationApiService {
  constructor(private http: HttpClient) {}

  /** Runs whichever daily stock jobs are switched on, right now. */
  run(): Observable<StockAutomationResult> {
    return this.http.post<StockAutomationResult>(`${API_BASE}/inventory/stock-automation/run`, {});
  }
}
