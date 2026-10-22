import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { API_BASE } from '../../core/constants';

interface Rate { userId: number; name: string; hourlyCost: number | null; }

/**
 * Law firm settings > Cost of lawyers' time (admin only): what one hour of each person's time costs the firm, e.g. salary and overheads per hour. Only the
 * matter profitability report uses it. A person with no rate counts as free in that report, and the report says how many hours that affects.
 */
@Component({
  selector: 'app-law-cost-rates',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="card mt-3" style="padding:1rem;">
      <h4 class="mb-1">Cost of lawyers' time</h4>
      <div class="text-muted mb-2" style="font-size:0.85rem;">What one hour of each person's time costs the firm (salary and overheads, per hour). Used only by the <em>Law matter profitability</em> report to work out profit: fees billed less the cost of the time put in. Only admins can see or change this. Leave it empty if you do not want to count that person's time.</div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <div class="text-muted" *ngIf="!rates">Loading…</div>
      <form *ngIf="rates" (ngSubmit)="save()">
        <table class="stack table-sm" style="max-width:34rem;">
          <thead><tr><th>Person</th><th style="width:12rem;">Cost per hour</th></tr></thead>
          <tbody><tr *ngFor="let r of rates; let i = index">
            <td data-label="Person">{{ r.name }}</td>
            <td data-label="Cost per hour"><input class="form-control" type="number" min="0" step="0.01" [name]="'r' + i" [(ngModel)]="r.hourlyCost" placeholder="not set" /></td>
          </tr></tbody>
        </table>
        <button class="btn btn-primary mt-2" type="submit" [disabled]="busy">{{ busy ? 'Saving…' : 'Save the costs' }}</button>
      </form>
    </div>
  `,
})
export class LawCostRatesComponent implements OnInit {
  rates: Rate[] | null = null;
  error: string | null = null;
  saved = false;
  busy = false;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<Rate[]>(`${API_BASE}/law/cost-rates`).subscribe({
      next: (r) => (this.rates = r),
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the costs'),
    });
  }

  save(): void {
    if (!this.rates) return;
    this.busy = true;
    this.error = null;
    this.saved = false;
    const rates = this.rates.map((r) => ({ userId: r.userId, hourlyCost: r.hourlyCost === null || (r.hourlyCost as unknown) === '' ? null : Number(r.hourlyCost) }));
    this.http.put<Rate[]>(`${API_BASE}/law/cost-rates`, { rates }).subscribe({
      next: (r) => { this.busy = false; this.saved = true; this.rates = r; },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not save the costs'; },
    });
  }
}
