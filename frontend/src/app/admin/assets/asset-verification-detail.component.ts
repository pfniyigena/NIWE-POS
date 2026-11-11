import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { AssetApiService, Verification, VerificationLine } from '../../core/api/asset-api.service';

import { T } from '../../core/i18n/t';
/** Assets > Verification > one round: mark every asset found, missing or damaged, close the round, and write off what is missing. */
@Component({
  selector: 'app-asset-verification-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <ng-container *ngIf="v">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <h3>{{ v.name }}
            <span class="badge ml-2" [class.badge-warning]="v.status === 'OPEN'" [class.badge-success]="v.status === 'CLOSED'">{{ v.status === 'OPEN' ? ('Open' | translate) : (('Closed' | translate) + ' ' + (v.closedOn || '')) }}</span>
          </h3>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <a class="btn btn-outline-primary" routerLink="/admin/assets/verifications">{{ 'Back' | translate }}</a>
            <a class="btn btn-outline-primary" [routerLink]="['/admin/reports/assets-verification']" [queryParams]="{ verificationId: v.id }">{{ 'Report' | translate }}</a>
            <button class="btn btn-outline-secondary" type="button" *ngIf="canManage && v.status === 'OPEN'" (click)="remove()">{{ 'Delete round' | translate }}</button>
          </div>
        </div>
        <p class="text-muted">
          {{ 'Covers {p0}. Started {p1}' | translate: { p0: (cover()), p1: (v.startedOn) } }}<span *ngIf="v.createdByName"> {{ 'by {p0}' | translate: { p0: (v.createdByName) } }}</span><span *ngIf="v.note"> — {{ v.note }}</span>.
        </p>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Assets' | translate }}</div><strong>{{ v.total }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Found' | translate }}</div><strong>{{ v.found }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Missing' | translate }}</div><strong>{{ v.missing }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Damaged' | translate }}</div><strong>{{ v.damaged }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Still to check' | translate }}</div><strong>{{ v.pending }}</strong></div>
        </div>

        <div class="card mb-3" style="padding:0.75rem 1rem;" *ngIf="canManage && v.status === 'OPEN'">
          <div class="flex gap-3 align-center" style="flex-wrap:wrap;">
            <span *ngIf="v.pending > 0">{{ 'Mark everything still unchecked as' | translate }}
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="rest('FOUND')" [disabled]="busy">{{ 'Found' | translate }}</button>
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="rest('MISSING')" [disabled]="busy">{{ 'Missing' | translate }}</button>
            </span>
            <label class="flex align-center gap-2"><input type="checkbox" name="applyLoc" [(ngModel)]="applyLocations" /> {{ 'Move assets to the place where they were found' | translate }}</label>
            <button class="btn btn-primary" type="button" (click)="close()" [disabled]="busy || v.pending > 0" [title]="v.pending > 0 ? 'Check every asset first' : ''">{{ 'Close the round' | translate }}</button>
          </div>
        </div>

        <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
          <button type="button" class="btn btn-sm" *ngFor="let f of filters" [ngClass]="filter === f.key ? 'btn-primary' : 'btn-outline-primary'" (click)="filter = f.key">{{ f.label }}</button>
          <input class="form-control" style="width:14rem;" [placeholder]="'Search' | translate" [(ngModel)]="search" name="search" />
        </div>
        <table class="table-sm">
          <thead><tr><th>{{ 'Tag' | translate }}</th><th>{{ 'Asset' | translate }}</th><th>{{ 'Should be at' | translate }}</th><th>{{ 'Held by' | translate }}</th><th class="text-end">{{ 'Book value' | translate }}</th><th>{{ 'Result' | translate }}</th><th>{{ 'Found at / note' | translate }}</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let l of visible()">
              <td><a [routerLink]="['/admin/assets', l.assetId]">{{ l.assetCode }}</a></td>
              <td>{{ l.assetName }}</td>
              <td>{{ l.expectedLocation || '—' }}</td>
              <td>{{ l.expectedCustodian || '—' }}</td>
              <td class="text-end">{{ l.bookValue | number: '1.2-2' }}</td>
              <td>
                <span class="badge badge-secondary" *ngIf="l.result === 'PENDING'">{{ 'To check' | translate }}</span>
                <span class="badge badge-success" *ngIf="l.result === 'FOUND'">{{ 'Found' | translate }}</span>
                <span class="badge badge-danger" *ngIf="l.result === 'MISSING'">{{ 'Missing' | translate }}</span>
                <span class="badge badge-warning" *ngIf="l.result === 'DAMAGED'">{{ 'Damaged' | translate }}</span>
                <span class="badge badge-secondary" *ngIf="l.writtenOff">{{ 'Written off' | translate }}</span>
              </td>
              <td>
                <ng-container *ngIf="canManage && v.status === 'OPEN'; else ro">
                  <input class="form-control" style="width:9rem; display:inline-block;" [placeholder]="'found at' | translate" [(ngModel)]="edit[l.id].place" [name]="'p' + l.id" />
                  <input class="form-control" style="width:9rem; display:inline-block;" [placeholder]="'note' | translate" [(ngModel)]="edit[l.id].note" [name]="'n' + l.id" />
                </ng-container>
                <ng-template #ro>{{ l.foundLocation || '' }}{{ l.foundLocation && l.note ? ' — ' : '' }}{{ l.note || '' }}</ng-template>
              </td>
              <td style="white-space:nowrap;">
                <ng-container *ngIf="canManage && v.status === 'OPEN'">
                  <button class="btn btn-sm btn-outline-primary" type="button" (click)="mark(l, 'FOUND')">{{ 'Found' | translate }}</button>
                  <button class="btn btn-sm btn-outline-danger" type="button" (click)="mark(l, 'MISSING')">{{ 'Missing' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" (click)="mark(l, 'DAMAGED')">{{ 'Damaged' | translate }}</button>
                </ng-container>
                <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="canManage && l.result === 'MISSING' && !l.writtenOff && l.assetStatus !== 'DISPOSED'" (click)="writeOff(l)" [disabled]="busy">{{ 'Write off…' | translate }}</button>
              </td>
            </tr>
            <tr *ngIf="!visible().length"><td colspan="8" class="text-muted">{{ 'Nothing here.' | translate }}</td></tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class AssetVerificationDetailComponent implements OnInit {
  v: Verification | null = null;
  lines: VerificationLine[] = [];
  edit: Record<number, { place: string; note: string }> = {};
  filter = 'PENDING';
  search = '';
  applyLocations = false;
  error: string | null = null;
  notice: string | null = null;
  busy = false;
  readonly filters = [
    { key: 'PENDING', label: 'To check' }, { key: 'FOUND', label: 'Found' }, { key: 'MISSING', label: 'Missing' },
    { key: 'DAMAGED', label: 'Damaged' }, { key: '', label: 'All' },
  ];

  constructor(private api: AssetApiService, private route: ActivatedRoute, private router: Router, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('ACCOUNTING');
  }

  private get id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.api.verification(this.id).subscribe({
      next: (v) => {
        this.v = v;
        if (v.status === 'CLOSED' && this.filter === 'PENDING') this.filter = '';
      },
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load the round')),
    });
    this.api.verificationLines(this.id).subscribe({
      next: (l) => {
        this.lines = l;
        l.forEach((x) => {
          if (!this.edit[x.id]) this.edit[x.id] = { place: x.foundLocation ?? '', note: x.note ?? '' };
        });
      },
      error: (e) => (this.error = e?.error?.message ?? T('Failed to load the assets')),
    });
  }

  cover(): string {
    const v = this.v;
    const parts = v ? [v.branchName, v.categoryName, v.location].filter(Boolean) : [];
    return parts.length ? parts.join(' · ') : 'everything';
  }

  visible(): VerificationLine[] {
    const q = this.search.trim().toLowerCase();
    return this.lines.filter(
      (l) => (!this.filter || l.result === this.filter) && (!q || (l.assetCode + ' ' + l.assetName + ' ' + (l.expectedLocation ?? '')).toLowerCase().includes(q))
    );
  }

  mark(l: VerificationLine, result: string): void {
    this.error = this.notice = null;
    const e = this.edit[l.id];
    this.api.markLine(this.id, l.id, { result, foundLocation: e.place.trim() || null, note: e.note.trim() || null }).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Could not save that')),
    });
  }

  rest(result: string): void {
    if (!confirm(T('Mark every asset not yet checked as {p0}?', { p0: result === 'FOUND' ? T('found') : T('missing') }))) return;
    this.busy = true;
    this.api.markRest(this.id, result).subscribe({
      next: () => {
        this.busy = false;
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not save that');
      },
    });
  }

  close(): void {
    if (!confirm(T('Close this round? It can no longer be changed.'))) return;
    this.busy = true;
    this.api.closeVerification(this.id, this.applyLocations).subscribe({
      next: () => {
        this.busy = false;
        this.notice = T('The round is closed.');
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not close the round');
      },
    });
  }

  writeOff(l: VerificationLine): void {
    if (!confirm(T('Write off {p0} — {p1}? It is disposed of today with no proceeds and Accounting books the loss.', { p0: l.assetCode, p1: l.assetName }))) return;
    this.busy = true;
    this.error = this.notice = null;
    this.api.writeOffLine(this.id, l.id).subscribe({
      next: () => {
        this.busy = false;
        this.notice = l.assetCode + ' was written off.';
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('Could not write it off');
      },
    });
  }

  remove(): void {
    if (!confirm(T('Delete this round?'))) return;
    this.api.deleteVerification(this.id).subscribe({
      next: () => this.router.navigate(['/admin/assets/verifications']),
      error: (e) => (this.error = e?.error?.message ?? T('Could not delete the round')),
    });
  }
}
