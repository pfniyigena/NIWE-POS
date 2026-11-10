import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ConflictLog, ConflictResult, LawApiService, PARTY_ROLE_LABEL } from '../core/api/law-api.service';

/** Law Firm > Conflict check: search a name, phone, ID or e-mail against every client and every outside contact before taking someone on. Every search is logged. */
@Component({
  selector: 'app-law-conflict-check',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-2">{{ 'Conflict check' | translate }}</h3>
      <p class="text-muted">{{ 'Before you act for someone, search for them here. It looks at all clients and all opposing parties, witnesses and other contacts, on open and closed matters.' | translate }}</p>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="search()">
        <div class="flex gap-2" style="flex-wrap:wrap; align-items:end;">
          <div class="form-group" style="flex:2; min-width:14rem;"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="n" [(ngModel)]="name" /></div>
          <div class="form-group"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="p" [(ngModel)]="phone" /></div>
          <div class="form-group"><label class="form-label">{{ 'ID / registration no.' | translate }}</label><input class="form-control" name="i" [(ngModel)]="idNumber" /></div>
          <div class="form-group"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" name="e" [(ngModel)]="email" /></div>
          <button class="btn btn-primary" type="submit" [disabled]="busy">{{ busy ? ('Searching…' | translate) : ('Check' | translate) }}</button>
        </div>
      </form>

      <ng-container *ngIf="result">
        <div class="alert alert-success" *ngIf="!result.hits">{{ 'No conflicts found. This search has been logged.' | translate }}</div>
        <ng-container *ngFor="let s of result.subjects">
          <div *ngFor="let h of s.hits" class="card mb-2" style="padding:0.9rem;" [style.borderLeft]="h.severity === 'HIGH' ? '4px solid #c0392b' : '4px solid #e6a700'">
            <div class="flex justify-between" style="flex-wrap:wrap; gap:0.5rem;">
              <div><strong>{{ h.name }}</strong> <span class="text-muted">{{ h.kind === 'CLIENT' ? ('client' | translate) : ('contact' | translate) }}</span>
                <span class="text-muted" style="font-size:0.85rem;"> {{ '· matches on {p0}' | translate: { p0: (h.reasons.join(', ')) } }}</span></div>
              <span class="badge" [class.badge-danger]="h.severity === 'HIGH'" [class.badge-warning]="h.severity !== 'HIGH'">{{ h.severity === 'HIGH' ? ('Opposite sides' | translate) : ('Review' | translate) }}</span>
            </div>
            <div class="text-muted" style="font-size:0.85rem;"><span *ngIf="h.phone">{{ h.phone }} </span><span *ngIf="h.email">{{ h.email }} </span><span *ngIf="h.idNumber">{{ 'ID {p0}' | translate: { p0: (h.idNumber) } }}</span></div>
            <ul style="margin:0.4rem 0 0 1rem;">
              <li *ngFor="let r of h.matters">
                <span *ngIf="!r.hidden"><a [routerLink]="['/law/matters', r.id]">{{ r.number }}</a> {{ r.title }}</span>
                <span *ngIf="r.hidden" class="text-muted">{{ 'A restricted matter' | translate }}</span>
                — {{ role(r.role) }} <span class="text-muted">({{ r.status.toLowerCase().replace('_', ' ') }})</span>
              </li>
            </ul>
          </div>
        </ng-container>
      </ng-container>

      <h4 class="mt-4 mb-2" *ngIf="log.length">{{ 'Recent checks' | translate }}</h4>
      <table class="stack table-sm" *ngIf="log.length">
        <thead><tr><th>{{ 'When' | translate }}</th><th>{{ 'Searched for' | translate }}</th><th>{{ 'Matter' | translate }}</th><th class="text-end">{{ 'Hits' | translate }}</th><th>{{ 'By' | translate }}</th></tr></thead>
        <tbody><tr *ngFor="let l of log"><td data-label="When">{{ l.at | date: 'd MMM y, HH:mm' }}</td><td data-label="Searched for">{{ l.query }}</td>
          <td data-label="Matter"><a *ngIf="l.matterId" [routerLink]="['/law/matters', l.matterId]">{{ l.matterNumber }}</a><span *ngIf="!l.matterId">—</span></td><td class="text-end" data-label="Hits">{{ l.hits }}</td><td data-label="By">{{ l.checkedBy }}</td></tr></tbody>
      </table>
    </app-shell>
  `,
})
export class LawConflictCheckComponent implements OnInit {
  name = '';
  phone = '';
  idNumber = '';
  email = '';
  busy = false;
  result: ConflictResult | null = null;
  log: ConflictLog[] = [];
  error: string | null = null;

  constructor(private api: LawApiService) {}

  ngOnInit(): void { this.loadLog(); }

  loadLog(): void {
    this.api.conflictLog().subscribe({ next: (l) => (this.log = l), error: () => {} });
  }

  role(r: string): string { return r === 'CLIENT' ? 'Client' : (PARTY_ROLE_LABEL as Record<string, string>)[r] ?? r; }

  search(): void {
    this.busy = true;
    this.error = null;
    this.api.conflictSearch({ name: this.name.trim(), phone: this.phone.trim(), idNumber: this.idNumber.trim(), email: this.email.trim() }).subscribe({
      next: (r) => { this.result = r; this.busy = false; this.loadLog(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not run the check'; },
    });
  }
}
