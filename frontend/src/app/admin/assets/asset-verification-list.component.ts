import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { AssetApiService, AssetCategory, Verification } from '../../core/api/asset-api.service';

import { T } from '../../core/i18n/t';
/** Assets > Verification: counting rounds ("did we find everything?") by branch, category or place, newest first, and a form to start one. */
@Component({
  selector: 'app-asset-verification-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Asset verification' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/assets/overview">{{ 'Overview' | translate }}</a>
          <a class="btn btn-outline-primary" routerLink="/admin/assets">{{ 'Asset register' | translate }}</a>
        </div>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <p class="text-muted">
        {{ 'A round lists the assets that should be in a branch, category or place. Walk around, mark each one found, missing or damaged, then close the round. Missing assets can be written off from the round.' | translate }}
      </p>
      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="start()" *ngIf="canManage">
        <strong class="mb-2">{{ 'Start a round' | translate }}</strong>
        <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
          <div class="form-group"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="name" [(ngModel)]="form.name" [placeholder]="'e.g. Year-end count' | translate" required /></div>
          <div class="form-group" *ngIf="branches.length > 1"><label class="form-label">{{ 'Branch' | translate }}</label>
            <select class="form-select" name="branch" [(ngModel)]="form.branchId">
              <option [ngValue]="null">{{ 'All branches' | translate }}</option>
              <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Category' | translate }}</label>
            <select class="form-select" name="category" [(ngModel)]="form.categoryId">
              <option [ngValue]="null">{{ 'All categories' | translate }}</option>
              <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }}</option>
            </select></div>
          <div class="form-group"><label class="form-label">{{ 'Place' | translate }}</label><input class="form-control" name="location" [(ngModel)]="form.location" [placeholder]="'all places' | translate" /></div>
          <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="note" [(ngModel)]="form.note" /></div>
          <button class="btn btn-primary" type="submit" [disabled]="busy">{{ 'Start' | translate }}</button>
        </div>
      </form>
      <table class="table-sm">
        <thead><tr><th>{{ 'Round' | translate }}</th><th>{{ 'Covers' | translate }}</th><th>{{ 'Started' | translate }}</th><th>{{ 'Status' | translate }}</th><th class="text-end">{{ 'Assets' | translate }}</th><th class="text-end">{{ 'Found' | translate }}</th><th class="text-end">{{ 'Missing' | translate }}</th><th class="text-end">{{ 'Damaged' | translate }}</th><th class="text-end">{{ 'To do' | translate }}</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let v of rounds">
            <td><a [routerLink]="['/admin/assets/verifications', v.id]">{{ v.name }}</a></td>
            <td>{{ cover(v) }}</td>
            <td>{{ v.startedOn }}</td>
            <td><span class="badge" [class.badge-warning]="v.status === 'OPEN'" [class.badge-success]="v.status === 'CLOSED'">{{ v.status === 'OPEN' ? ('Open' | translate) : (('Closed' | translate) + ' ' + (v.closedOn || '')) }}</span></td>
            <td class="text-end">{{ v.total }}</td><td class="text-end">{{ v.found }}</td><td class="text-end">{{ v.missing }}</td><td class="text-end">{{ v.damaged }}</td><td class="text-end">{{ v.pending }}</td>
            <td><a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/assets/verifications', v.id]">{{ 'Open' | translate }}</a></td>
          </tr>
          <tr *ngIf="!rounds.length"><td colspan="10" class="text-muted">{{ 'No rounds yet.' | translate }}</td></tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class AssetVerificationListComponent implements OnInit {
  rounds: Verification[] = [];
  categories: AssetCategory[] = [];
  branches: Branch[] = [];
  error: string | null = null;
  busy = false;
  form = { name: '', branchId: null as number | null, categoryId: null as number | null, location: '', note: '' };

  constructor(private api: AssetApiService, private branchApi: BranchApiService, private router: Router, private auth: AuthService) {}

  get canManage(): boolean {
    return this.auth.canManage('ACCOUNTING');
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.categories().subscribe({ next: (c) => (this.categories = c), error: () => {} });
    this.load();
  }

  load(): void {
    this.api.verifications().subscribe({ next: (r) => (this.rounds = r), error: (e) => (this.error = e?.error?.message ?? T('Failed to load the rounds')) });
  }

  cover(v: Verification): string {
    const parts = [v.branchName, v.categoryName, v.location].filter(Boolean);
    return parts.length ? parts.join(' · ') : 'Everything';
  }

  start(): void {
    this.error = null;
    this.busy = true;
    this.api
      .startVerification({
        name: this.form.name.trim(), branchId: this.form.branchId, categoryId: this.form.categoryId,
        location: this.form.location.trim() || null, note: this.form.note.trim() || null,
      })
      .subscribe({
        next: (v) => this.router.navigate(['/admin/assets/verifications', v.id]),
        error: (e) => {
          this.busy = false;
          this.error = e?.error?.message ?? T('Could not start the round');
        },
      });
  }
}
