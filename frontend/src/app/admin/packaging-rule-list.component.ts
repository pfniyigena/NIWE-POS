import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { PackagingApiService } from '../core/api/packaging-api.service';
import { PackagingRule } from '../core/models';

/**
 * Admin > Inventory > Packaging rules — Fourier: "Product Packaging & Breakdown / Unit
 * Conversion". The recipe list (e.g. "1 x Whiskey 750ml bottle -> 25 x Whiskey shot"); actually
 * running a breakdown happens on the separate "Breakdown stock" screen. Rules are always
 * configurable here regardless of the Inventory settings toggle — only running a breakdown is
 * gated (see PackagingBreakdownComponent).
 */
@Component({
  selector: 'app-packaging-rule-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">{{ 'Packaging rules' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-primary" routerLink="/admin/packaging/breakdown">{{ 'Break down stock' | translate }}</a>
          <a class="btn btn-primary" routerLink="/admin/packaging/rules/new">{{ '+ New packaging rule' | translate }}</a>
        </div>
      </div>
      <p class="text-muted" style="font-size:0.85rem;">
        {{ 'Each rule says how one product breaks down into another, e.g. 1 bottle of whiskey into 25 shots, or 1 case of beer into 24 bottles.' | translate }}
      </p>
      <table class="table-sm">
        <thead>
          <tr>
            <th>{{ 'Parent product' | translate }}</th>
            <th></th>
            <th>{{ 'Breaks down into' | translate }}</th>
            <th class="text-end">{{ 'Units per parent' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of rules">
            <td>{{ r.parentProductName }}</td>
            <td class="text-muted">&rarr;</td>
            <td>{{ r.childProductName }}</td>
            <td class="text-end">{{ r.unitsPerParent }}</td>
            <td><span class="badge" [ngClass]="r.active ? 'badge-success' : 'badge-secondary'">{{ r.active ? ('Active' | translate) : ('Paused' | translate) }}</span></td>
            <td class="flex gap-2">
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/packaging/breakdown']" [queryParams]="{ ruleId: r.id }" *ngIf="r.active">{{ 'Break down' | translate }}</a>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/packaging/rules', r.id, 'edit']">{{ 'Edit' | translate }}</a>
              <button class="btn btn-sm btn-outline-secondary" (click)="pause(r)" *ngIf="r.active">{{ 'Pause' | translate }}</button>
              <button class="btn btn-sm btn-outline-secondary" (click)="resume(r)" *ngIf="!r.active">{{ 'Resume' | translate }}</button>
            </td>
          </tr>
          <tr *ngIf="rules.length === 0">
            <td colspan="6" class="text-muted">{{ 'No packaging rules yet — create one with "+ New packaging rule".' | translate }}</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class PackagingRuleListComponent implements OnInit {
  rules: PackagingRule[] = [];
  error: string | null = null;

  constructor(private packagingApi: PackagingApiService) {}

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.packagingApi.listRules().subscribe({
      next: (r) => (this.rules = r),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load packaging rules'),
    });
  }

  pause(r: PackagingRule): void {
    this.packagingApi.setRuleActive(r.id, false).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to pause packaging rule'),
    });
  }

  resume(r: PackagingRule): void {
    this.packagingApi.setRuleActive(r.id, true).subscribe({
      next: () => this.load(),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to resume packaging rule'),
    });
  }
}
