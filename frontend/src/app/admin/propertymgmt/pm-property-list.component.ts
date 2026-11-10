import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { OwnerRow, PM_PROPERTY_KINDS, PropertyMgmtApiService, PropertyRow, pmLabel } from '../../core/api/propertymgmt-api.service';

/** Property Management > Properties: the buildings and houses we manage, with occupancy and rent. */
@Component({
  selector: 'app-pm-property-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, PagerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Managed properties' | translate }}</h3>
        <a class="btn btn-primary" routerLink="/admin/propertymgmt/properties/new">{{ '+ Property' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="ow" [(ngModel)]="ownerId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any owner' | translate }}</option><option *ngFor="let o of owners" [ngValue]="o.id">{{ o.name }}</option>
        </select>
        <label style="display:flex; gap:0.4rem; align-items:center;"><input type="checkbox" name="ao" [(ngModel)]="activeOnly" (ngModelChange)="load()" /> {{ 'Active only' | translate }}</label>
        <input class="form-control" style="width:16rem;" [placeholder]="'Search name, code, area, owner' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>{{ 'Property' | translate }}</th><th>{{ 'Owner' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'Area' | translate }}</th><th class="text-end">{{ 'Units' | translate }}</th><th class="text-end">{{ 'Occupied' | translate }}</th><th class="text-end">{{ 'Vacant' | translate }}</th><th class="text-end">{{ 'Rent / month' | translate }}</th><th class="text-end">{{ 'Fee' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of list">
            <td data-label="Property"><a [routerLink]="['/admin/propertymgmt/properties', p.id]"><strong>{{ p.name }}</strong></a> <span class="text-muted" style="font-size:0.8rem;">{{ p.code }}</span><span class="badge badge-secondary" *ngIf="!p.active" style="margin-left:0.3rem;">{{ 'Inactive' | translate }}</span></td>
            <td data-label="Owner"><a *ngIf="p.ownerCount <= 1" [routerLink]="['/admin/propertymgmt/owners', p.ownerId]">{{ p.ownerName }}</a><span *ngIf="p.ownerCount > 1" [title]="p.ownersLabel">{{ p.ownerName }} <span class="text-muted">{{ '+ {p0} more' | translate: { p0: (p.ownerCount - 1) } }}</span></span></td>
            <td data-label="Type">{{ kind(p.kind) }}</td><td data-label="Area">{{ p.area || '—' }}</td>
            <td class="text-end" data-label="Units">{{ p.units }}</td><td class="text-end" data-label="Occupied">{{ p.occupied }}</td>
            <td class="text-end" data-label="Vacant"><span [style.color]="p.vacant ? '#b45309' : ''">{{ p.vacant }}</span></td>
            <td class="text-end" data-label="Rent / month">{{ p.monthlyRent | number: '1.0-0' }}</td>
            <td class="text-end" data-label="Fee">{{ p.effectiveFeePercent != null ? (p.effectiveFeePercent | number: '1.0-2') + '%' : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'No properties match.' | translate }}</p>
    </app-shell>
  `,
})
export class PmPropertyListComponent implements OnInit {
  list: PropertyRow[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  owners: OwnerRow[] = [];
  loaded = false;
  q = '';
  ownerId: number | null = null;
  activeOnly = false;
  error: string | null = null;

  constructor(private api: PropertyMgmtApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const o = Number(this.route.snapshot.queryParamMap.get('ownerId'));
    if (o) this.ownerId = o;
    this.api.owners({}).subscribe({ next: (l) => (this.owners = l), error: () => {} });
    this.load();
  }

  kind(k: string): string { return pmLabel(PM_PROPERTY_KINDS, k); }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.propertiesPage({ q: this.q.trim(), ownerId: this.ownerId, activeOnly: this.activeOnly, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the properties'; },
    });
  }
}
