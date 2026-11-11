import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { BranchApiService } from '../core/api/branch-api.service';
import { Branch } from '../core/models';

import { T } from '../core/i18n/t';
/**
 * Admin > Branches — Fourier: "How business branches can be introduced?" A branch is a trading
 * location (outlet) that owns one or more warehouses (set on the warehouse itself) and, through a
 * register's warehouse, its tills. Sales, purchases and expenses record the branch they belong
 * to, so reports and the dashboard can be filtered per branch. The "Head Office" is seeded
 * automatically; everything created before branches existed belongs to it.
 */
@Component({
  selector: 'app-branch-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ 'Branches' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/branches/new">{{ '+ New branch' | translate }}</a>
      </div>
      <p class="text-muted" style="font-size:0.85rem;">
        {{ 'A branch owns one or more warehouses (assign them on the warehouse). Sales, purchases and expenses are tagged with their branch so you can report per branch. Everything recorded before branches existed belongs to the Head Office.' | translate }}
      </p>
      <table>
        <thead>
          <tr>
            <th>{{ 'Code' | translate }}</th>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Phone' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let b of branches">
            <td>{{ b.code }}</td>
            <td>{{ b.name }} <span class="text-muted" *ngIf="b.headquarters" style="font-size:0.8rem;">{{ '★ Head office' | translate }}</span></td>
            <td>{{ b.phone || '—' }}</td>
            <td>
              <span class="badge badge-success" *ngIf="b.active">{{ 'Active' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="!b.active">{{ 'Inactive' | translate }}</span>
            </td>
            <td class="flex gap-2 align-center">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/branches', b.id, 'edit']">{{ 'Edit' | translate }}</a>
              <button class="btn btn-sm btn-outline-secondary" (click)="setActive(b, false)" *ngIf="b.active && !b.headquarters">{{ 'Deactivate' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="setActive(b, true)" *ngIf="!b.active">{{ 'Activate' | translate }}</button>
              <span class="text-muted" style="font-size:0.8rem;" *ngIf="b.headquarters">{{ 'Can\\'t be deactivated' | translate }}</span>
            </td>
          </tr>
          <tr *ngIf="branches.length === 0">
            <td colspan="5" class="text-muted">{{ 'No branches yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class BranchListComponent implements OnInit {
  branches: Branch[] = [];
  error: string | null = null;

  constructor(private branchApi: BranchApiService) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.branchApi.listAll().subscribe({
      next: (b) => (this.branches = b),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load branches')),
    });
  }

  setActive(b: Branch, active: boolean): void {
    this.error = null;
    this.branchApi.setActive(b.id, active).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? T('Failed to update branch')),
    });
  }
}
