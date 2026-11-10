import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReExportComponent } from './re-export.component';
import { ReImportComponent } from './re-import.component';
import { AuthService } from '../../core/auth.service';
import { PagerComponent } from '../../shared/pager.component';
import { PAGE_SIZE } from '../../core/api/paged';
import { RePhotoComponent } from './re-photo.component';
import { Option, PROPERTY_STATUS_LABEL, PROPERTY_TYPES, PropertyRow, PURPOSE_LABEL, RealEstateApiService, typeLabel } from '../../core/api/realestate-api.service';

/** Real Estate > Properties: every listing with its cover photo, price, agent and how many leads and viewings it has. */
@Component({
  selector: 'app-property-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, RePhotoComponent, PagerComponent, ReExportComponent, ReImportComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Properties' | translate }}</h3>
        <div class="flex gap-2">
          <button class="btn btn-outline-secondary imp-open" type="button" *ngIf="canManage" (click)="importOpen = !importOpen">{{ 'Import' | translate }}</button>
          <a class="btn btn-primary" routerLink="/admin/realestate/properties/new">{{ '+ Add property' | translate }}</a>
        </div>
      </div>
      <app-re-import kind="properties" *ngIf="importOpen" (imported)="load()" (closed)="importOpen = false"></app-re-import>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex gap-2 mb-3 align-center" style="flex-wrap:wrap;">
        <select class="form-select" style="width:auto;" name="st" [(ngModel)]="status" (ngModelChange)="load()">
          <option value="">{{ 'All statuses' | translate }}</option>
          <option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ty" [(ngModel)]="type" (ngModelChange)="load()">
          <option value="">{{ 'All types' | translate }}</option><option *ngFor="let t of types" [value]="t.value">{{ t.label }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="pu" [(ngModel)]="purpose" (ngModelChange)="load()">
          <option value="">{{ 'Sale or rent' | translate }}</option><option value="SALE">{{ 'For sale' | translate }}</option><option value="RENT">{{ 'For rent' | translate }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ar" [(ngModel)]="area" (ngModelChange)="load()">
          <option value="">{{ 'All areas' | translate }}</option><option *ngFor="let a of areas" [value]="a">{{ a }}</option>
        </select>
        <select class="form-select" style="width:auto;" name="ag" [(ngModel)]="agentId" (ngModelChange)="load()">
          <option [ngValue]="null">{{ 'Any agent' | translate }}</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option>
        </select>
        <input class="form-control" style="width:6rem;" type="number" min="0" [placeholder]="'Beds' | translate" name="bd" [(ngModel)]="bedrooms" (change)="load()" />
        <input class="form-control" style="width:9rem;" type="number" min="0" [placeholder]="'Price from' | translate" name="mn" [(ngModel)]="minPrice" (change)="load()" />
        <input class="form-control" style="width:9rem;" type="number" min="0" [placeholder]="'Price to' | translate" name="mx" [(ngModel)]="maxPrice" (change)="load()" />
        <input class="form-control" style="width:14rem;" [placeholder]="'Search code, title, address, owner' | translate" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">{{ 'Search' | translate }}</button>
        <app-re-export kind="properties" [filters]="{ q: q.trim(), status: status, type: type, purpose: purpose, area: area, agentId: agentId, bedrooms: bedrooms, minPrice: minPrice, maxPrice: maxPrice }"></app-re-export>
      </div>

      <table class="stack table-sm">
        <thead><tr><th></th><th>{{ 'Property' | translate }}</th><th>{{ 'Type' | translate }}</th><th>{{ 'Area' | translate }}</th><th class="text-end">{{ 'Beds' | translate }}</th><th class="text-end">{{ 'Size m²' | translate }}</th><th class="text-end">{{ 'Sale price' | translate }}</th><th class="text-end">{{ 'Rent / month' | translate }}</th><th>{{ 'Status' | translate }}</th><th>{{ 'Agent' | translate }}</th><th class="text-end">{{ 'Days listed' | translate }}</th><th class="text-end">{{ 'Leads' | translate }}</th><th class="text-end">{{ 'Viewings' | translate }}</th></tr></thead>
        <tbody>
          <tr *ngFor="let p of list">
            <td style="width:5.5rem;" data-label=""><app-re-photo [propertyId]="p.id" [photoId]="p.coverPhotoId"></app-re-photo></td>
            <td data-label="Property"><a [routerLink]="['/admin/realestate/properties', p.id]"><strong>{{ p.code }}</strong></a> {{ p.title }}
              <span class="badge badge-warning" *ngIf="p.featured" style="margin-left:0.3rem;">{{ 'Featured' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="p.publicListing" style="margin-left:0.3rem;" [title]="'Shown on the public website while Available' | translate">{{ 'On website' | translate }}</span></td>
            <td data-label="Type">{{ typeName(p.type) }}<div class="text-muted" style="font-size:0.75rem;">{{ purposeName(p.purpose) }}</div></td>
            <td data-label="Area">{{ p.area || '—' }}</td>
            <td class="text-end" data-label="Beds">{{ p.bedrooms ?? '—' }}</td>
            <td class="text-end" data-label="Size m²">{{ p.sizeSqm != null ? (p.sizeSqm | number: '1.0-0') : '—' }}</td>
            <td class="text-end" data-label="Sale price">{{ p.salePrice != null ? (p.salePrice | number: '1.0-0') : '—' }}</td>
            <td class="text-end" data-label="Rent / month">{{ p.rentPrice != null ? (p.rentPrice | number: '1.0-0') : '—' }}</td>
            <td data-label="Status"><span class="badge" [class.badge-success]="p.status === 'AVAILABLE'" [class.badge-warning]="p.status === 'RESERVED'" [class.badge-secondary]="p.status !== 'AVAILABLE' && p.status !== 'RESERVED'">{{ statusName(p.status) }}</span></td>
            <td data-label="Agent">{{ p.agentName || '—' }}</td>
            <td class="text-end" data-label="Days listed">{{ p.listedDate ? p.daysListed : '—' }}</td>
            <td class="text-end" data-label="Leads">{{ p.openLeads }}</td>
            <td class="text-end" data-label="Viewings">{{ p.upcomingViewings }}</td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length && loaded">{{ 'No properties match. Add one to start your list.' | translate }}</p>
    </app-shell>
  `,
})
export class PropertyListComponent implements OnInit {
  list: PropertyRow[] = [];
  loaded = false;
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  agents: Option[] = [];
  areas: string[] = [];
  types = PROPERTY_TYPES;
  statuses = (Object.keys(PROPERTY_STATUS_LABEL) as (keyof typeof PROPERTY_STATUS_LABEL)[]).map((k) => ({ value: k, label: PROPERTY_STATUS_LABEL[k] }));
  status = '';
  type = '';
  purpose = '';
  area = '';
  agentId: number | null = null;
  bedrooms: number | null = null;
  minPrice: number | null = null;
  maxPrice: number | null = null;
  q = '';
  error: string | null = null;

  canManage: boolean;
  importOpen = false;

  constructor(private api: RealEstateApiService, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void {
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.api.settings().subscribe({ next: (s) => (this.areas = s.areas), error: () => {} });
    this.load();
  }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.error = null;
    this.api.propertiesPage({ q: this.q.trim(), status: this.status, type: this.type, purpose: this.purpose, area: this.area, agentId: this.agentId, bedrooms: this.bedrooms,
      minPrice: this.minPrice, maxPrice: this.maxPrice, page: this.page, size: this.size }).subscribe({
      next: (r) => { this.list = r.items; this.total = r.total; this.loaded = true; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the properties'),
    });
  }

  typeName(t: string): string { return typeLabel(t); }
  purposeName(p: string): string { return (PURPOSE_LABEL as Record<string, string>)[p] ?? p; }
  statusName(s: string): string { return (PROPERTY_STATUS_LABEL as Record<string, string>)[s] ?? s; }
}
