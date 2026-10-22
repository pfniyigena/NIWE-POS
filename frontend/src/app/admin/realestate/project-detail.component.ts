import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReDocumentsComponent } from './re-documents.component';
import { RePrintComponent } from './re-print.component';
import { AuthService } from '../../core/auth.service';
import {
  PriceAdjustResult, PROJECT_STATUS_LABEL, ProjectDto, RealEstateApiService, UNIT_KINDS, UNIT_STATUS_LABEL, UnitKind, UnitRow, UnitStatus, unitKindLabel,
} from '../../core/api/realestate-api.service';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));

/** Real Estate > Projects > one project: its figures, its units and price list, and the tools to add units and move prices. */
@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, ReDocumentsComponent, RePrintComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!p && !error" class="text-muted">Loading…</div>
      <ng-container *ngIf="p">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/realestate/projects" style="font-size:0.85rem;">← Projects</a>
            <h3>{{ p.name }} <span class="text-muted" style="font-size:0.9rem;">{{ p.code }}</span>
              <span class="badge" [class.badge-success]="p.status === 'SELLING'" [class.badge-secondary]="p.status === 'PLANNING' || p.status === 'ARCHIVED'" [class.badge-warning]="p.status === 'COMPLETED'" style="font-size:0.8rem; vertical-align:middle;">{{ statusName(p.status) }}</span></h3>
          </div>
          <div class="flex gap-2" *ngIf="canManage" style="flex-wrap:wrap;">
            <button class="btn btn-success" type="button" *ngIf="p.status === 'PLANNING'" (click)="setStatus('SELLING')" [disabled]="busy">Start selling</button>
            <a class="btn btn-outline-secondary" [routerLink]="['/admin/realestate/projects', p.id, 'edit']">Edit</a>
            <button class="btn btn-outline-danger" type="button" *ngIf="!p.totalUnits" (click)="remove()">Delete</button>
          </div>
        </div>
        <app-re-print kind="project" [id]="p.id"></app-re-print>
        <div class="alert alert-danger" *ngIf="actionError">{{ actionError }}</div>
        <div class="alert alert-info" *ngIf="notice">{{ notice }}</div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(22rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">The project</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">Area</td><td>{{ p.area || '—' }}</td></tr>
              <tr *ngIf="p.address"><td class="text-muted">Location</td><td>{{ p.address }}</td></tr>
              <tr><td class="text-muted">Developer</td><td>{{ p.developerName || '—' }}</td></tr>
              <tr><td class="text-muted">Launch</td><td>{{ p.launchDate ? (p.launchDate | date: 'd MMM y') : '—' }}</td></tr>
              <tr><td class="text-muted">Handover</td><td>{{ p.handoverDate ? (p.handoverDate | date: 'd MMM y') : '—' }}</td></tr>
              <tr *ngIf="p.description"><td class="text-muted">About</td><td style="white-space:pre-wrap;">{{ p.description }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">The numbers</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:11rem;">Units</td><td class="text-end">{{ p.totalUnits }}</td></tr>
              <tr><td class="text-muted">Available</td><td class="text-end">{{ p.available }}</td></tr>
              <tr><td class="text-muted">Reserved</td><td class="text-end">{{ p.reserved }}</td></tr>
              <tr><td class="text-muted">Sold</td><td class="text-end">{{ p.sold }}</td></tr>
              <tr *ngIf="p.withheld"><td class="text-muted">Withheld</td><td class="text-end">{{ p.withheld }}</td></tr>
              <tr><td class="text-muted">Value still for sale</td><td class="text-end">{{ p.stats.stockValue | number: '1.0-0' }}</td></tr>
              <tr><td class="text-muted">Sold for</td><td class="text-end">{{ p.stats.soldValue | number: '1.0-0' }}</td></tr>
              <tr><td class="text-muted">Collected from buyers</td><td class="text-end">{{ p.stats.collected | number: '1.0-0' }}</td></tr>
              <tr><td class="text-muted">Still owed by buyers</td><td class="text-end">{{ p.stats.outstanding | number: '1.0-0' }}</td></tr>
              <tr *ngIf="p.stats.minPrice != null"><td class="text-muted">Price range</td><td class="text-end">{{ p.stats.minPrice | number: '1.0-0' }} – {{ p.stats.maxPrice | number: '1.0-0' }}</td></tr>
            </tbody></table>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;">
          <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
            <h4>Units and price list</h4>
            <div class="flex gap-2" style="flex-wrap:wrap;">
              <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/admin/reports', 're-price-list']">Price list report</a>
              <ng-container *ngIf="canManage && p.status !== 'ARCHIVED'">
                <button class="btn btn-sm btn-outline-primary" type="button" (click)="panel = panel === 'one' ? '' : 'one'">+ Add a unit</button>
                <button class="btn btn-sm btn-outline-primary" type="button" (click)="panel = panel === 'many' ? '' : 'many'">Add many…</button>
                <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="p.totalUnits" (click)="panel = panel === 'price' ? '' : 'price'">Change prices…</button>
              </ng-container>
            </div>
          </div>

          <div class="card mb-2" style="padding:0.75rem;" *ngIf="panel === 'one'">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">Unit no. *</label><input class="form-control" style="width:8rem;" name="u1" [(ngModel)]="one.unitNo" maxlength="40" /></div>
              <div><label class="form-label">Block / phase</label><input class="form-control" style="width:10rem;" name="u2" [(ngModel)]="one.block" maxlength="80" list="blocks" /></div>
              <div><label class="form-label">Type</label><select class="form-select" name="u3" [(ngModel)]="one.kind"><option *ngFor="let k of kinds" [value]="k.value">{{ k.label }}</option></select></div>
              <div><label class="form-label">Floor</label><input class="form-control" style="width:5rem;" type="number" name="u4" [(ngModel)]="one.floor" /></div>
              <div><label class="form-label">Beds</label><input class="form-control" style="width:5rem;" type="number" min="0" name="u5" [(ngModel)]="one.bedrooms" /></div>
              <div><label class="form-label">Baths</label><input class="form-control" style="width:5rem;" type="number" min="0" name="u6" [(ngModel)]="one.bathrooms" /></div>
              <div><label class="form-label">Size (m²)</label><input class="form-control" style="width:7rem;" type="number" min="0" step="0.01" name="u7" [(ngModel)]="one.sizeSqm" /></div>
              <div><label class="form-label">List price *</label><input class="form-control" style="width:11rem;" type="number" min="0" name="u8" [(ngModel)]="one.listPrice" /></div>
              <button class="btn btn-primary" type="button" (click)="addOne()" [disabled]="busy">Add unit</button>
            </div>
            <datalist id="blocks"><option *ngFor="let b of p.blocks" [value]="b"></option></datalist>
          </div>

          <div class="card mb-2" style="padding:0.75rem;" *ngIf="panel === 'many'">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">Prefix</label><input class="form-control" style="width:7rem;" name="b1" [(ngModel)]="many.prefix" maxlength="20" placeholder="e.g. A-" /></div>
              <div><label class="form-label">From *</label><input class="form-control" style="width:6rem;" type="number" min="0" name="b2" [(ngModel)]="many.from" /></div>
              <div><label class="form-label">To *</label><input class="form-control" style="width:6rem;" type="number" min="0" name="b3" [(ngModel)]="many.to" /></div>
              <div><label class="form-label">Digits</label><input class="form-control" style="width:5rem;" type="number" min="0" max="8" name="b4" [(ngModel)]="many.padding" title="Pad the number with zeros: 2 gives 01, 02..." /></div>
              <div><label class="form-label">Block / phase</label><input class="form-control" style="width:10rem;" name="b5" [(ngModel)]="many.block" maxlength="80" list="blocks" /></div>
              <div><label class="form-label">Type</label><select class="form-select" name="b6" [(ngModel)]="many.kind"><option *ngFor="let k of kinds" [value]="k.value">{{ k.label }}</option></select></div>
              <div><label class="form-label">Size (m²)</label><input class="form-control" style="width:7rem;" type="number" min="0" step="0.01" name="b7" [(ngModel)]="many.sizeSqm" /></div>
              <div><label class="form-label">List price *</label><input class="form-control" style="width:11rem;" type="number" min="0" name="b8" [(ngModel)]="many.listPrice" /></div>
              <button class="btn btn-primary" type="button" (click)="addMany()" [disabled]="busy">Create units</button>
            </div>
            <div class="text-muted mt-1" style="font-size:0.85rem;">Makes one unit for every number from the first to the last (at most 500 at a time), all alike. {{ manyExample() }} Change single prices afterwards.</div>
          </div>

          <div class="card mb-2" style="padding:0.75rem;" *ngIf="panel === 'price'">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">Change by %</label><input class="form-control" style="width:8rem;" type="number" step="0.01" name="c1" [(ngModel)]="adj.percent" (ngModelChange)="previewed = null" placeholder="e.g. 5 or -3" /></div>
              <div><label class="form-label">Round to</label><input class="form-control" style="width:9rem;" type="number" min="1" name="c2" [(ngModel)]="adj.roundTo" (ngModelChange)="previewed = null" placeholder="e.g. 1000" /></div>
              <div><label class="form-label">Only block</label><select class="form-select" name="c3" [(ngModel)]="adj.block" (ngModelChange)="previewed = null"><option [ngValue]="null">All blocks</option><option *ngFor="let b of p.blocks" [ngValue]="b">{{ b }}</option></select></div>
              <div><label class="form-label">Only type</label><select class="form-select" name="c4" [(ngModel)]="adj.kind" (ngModelChange)="previewed = null"><option [ngValue]="null">All types</option><option *ngFor="let k of kinds" [ngValue]="k.value">{{ k.label }}</option></select></div>
              <div><label class="form-label">Reason</label><input class="form-control" style="width:14rem;" name="c5" [(ngModel)]="adj.reason" (ngModelChange)="previewed = null" maxlength="200" placeholder="e.g. Phase 2 launch" /></div>
              <button class="btn btn-outline-primary" type="button" (click)="adjust(true)" [disabled]="busy">Preview</button>
              <button class="btn btn-primary" type="button" (click)="adjust(false)" [disabled]="busy || !previewed || !previewed.changed">Apply to {{ previewed ? previewed.changed : 0 }} unit(s)</button>
            </div>
            <div class="text-muted mt-1" style="font-size:0.85rem;">Only units that are available or withheld are repriced; reserved and sold units keep the price of their sale. Every change is kept in the unit's price history.</div>
            <div *ngIf="previewed" class="mt-2">
              <div *ngIf="!previewed.changed" class="text-muted">Nothing would change.</div>
              <div style="max-height:14rem; overflow:auto;" *ngIf="previewed.changed">
                <table class="table-sm"><thead><tr><th>Unit</th><th>Block</th><th class="text-end">Now</th><th class="text-end">New</th></tr></thead>
                  <tbody><tr *ngFor="let l of previewed.lines"><td>{{ l.unitNo }}</td><td>{{ l.block || '—' }}</td><td class="text-end">{{ l.oldPrice | number: '1.0-0' }}</td><td class="text-end"><strong>{{ l.newPrice | number: '1.0-0' }}</strong></td></tr></tbody></table>
              </div>
            </div>
          </div>

          <div class="flex gap-2 mb-2 align-center" style="flex-wrap:wrap;" *ngIf="p.totalUnits">
            <select class="form-select" style="width:auto;" name="fs" [(ngModel)]="fStatus"><option value="">Any status</option><option *ngFor="let s of statuses" [value]="s.value">{{ s.label }}</option></select>
            <select class="form-select" style="width:auto;" name="fb" [(ngModel)]="fBlock"><option value="">All blocks</option><option *ngFor="let b of p.blocks" [value]="b">{{ b }}</option></select>
            <select class="form-select" style="width:auto;" name="fk" [(ngModel)]="fKind"><option value="">All types</option><option *ngFor="let k of kinds" [value]="k.value">{{ k.label }}</option></select>
            <input class="form-control" style="width:11rem;" placeholder="Unit no." name="fq" [(ngModel)]="fQ" />
            <span class="text-muted" style="font-size:0.85rem;">{{ shown().length }} of {{ p.totalUnits }}</span>
          </div>
          <table class="stack table-sm" *ngIf="p.totalUnits">
            <thead><tr><th>Unit</th><th>Block</th><th>Type</th><th class="text-end">Floor</th><th class="text-end">Beds</th><th class="text-end">Size m²</th><th class="text-end">List price</th><th class="text-end">Per m²</th><th>Status</th><th>Buyer</th></tr></thead>
            <tbody>
              <tr *ngFor="let u of shown()">
                <td data-label="Unit"><a [routerLink]="['/admin/realestate/units', u.id]"><strong>{{ u.unitNo }}</strong></a></td>
                <td data-label="Block">{{ u.block || '—' }}</td>
                <td data-label="Type">{{ kindName(u.kind) }}</td>
                <td class="text-end" data-label="Floor">{{ u.floor ?? '—' }}</td>
                <td class="text-end" data-label="Beds">{{ u.bedrooms ?? '—' }}</td>
                <td class="text-end" data-label="Size m²">{{ u.sizeSqm != null ? (u.sizeSqm | number: '1.0-2') : '—' }}</td>
                <td class="text-end" data-label="List price">{{ u.listPrice | number: '1.0-0' }}</td>
                <td class="text-end" data-label="Per m²">{{ u.pricePerSqm != null ? (u.pricePerSqm | number: '1.0-0') : '—' }}</td>
                <td data-label="Status"><span class="badge" [class.badge-success]="u.status === 'AVAILABLE'" [class.badge-warning]="u.status === 'RESERVED'" [class.badge-danger]="u.status === 'SOLD'" [class.badge-secondary]="u.status === 'WITHHELD'">{{ unitStatusName(u.status) }}</span></td>
                <td data-label="Buyer"><a *ngIf="u.saleId" [routerLink]="['/admin/realestate/unit-sales', u.saleId]">{{ u.buyerName }}</a><span *ngIf="!u.saleId">—</span></td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="!p.totalUnits">No units yet. Use <strong>Add many</strong> to make a run of plots or flats in one go.</p>
        </div>
        <div class="card mb-3" style="padding:1rem;">
          <app-re-documents subjectType="PROJECT" [subjectId]="p.id" [canManage]="canManage" defaultCategory="PERMIT"></app-re-documents>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class ProjectDetailComponent implements OnInit {
  p: ProjectDto | null = null;
  error: string | null = null;
  actionError: string | null = null;
  notice: string | null = null;
  busy = false;
  canManage: boolean;
  panel: '' | 'one' | 'many' | 'price' = '';
  kinds = UNIT_KINDS;
  statuses = (Object.keys(UNIT_STATUS_LABEL) as UnitStatus[]).map((k) => ({ value: k, label: UNIT_STATUS_LABEL[k] }));
  fStatus = '';
  fBlock = '';
  fKind = '';
  fQ = '';
  one = { unitNo: '', block: '', kind: 'PLOT' as UnitKind, floor: null as number | null, bedrooms: null as number | null, bathrooms: null as number | null, sizeSqm: null as number | null, listPrice: null as number | null };
  many = { prefix: '', from: 1 as number | null, to: null as number | null, padding: 2 as number | null, block: '', kind: 'PLOT' as UnitKind, sizeSqm: null as number | null, listPrice: null as number | null };
  adj = { percent: null as number | null, roundTo: null as number | null, block: null as string | null, kind: null as UnitKind | null, reason: '' };
  previewed: PriceAdjustResult | null = null;

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router, auth: AuthService) {
    this.canManage = auth.canManage('OPERATIONS');
  }

  ngOnInit(): void { this.load(); }

  private id(): number { return Number(this.route.snapshot.paramMap.get('id')); }

  load(): void {
    this.api.project(this.id()).subscribe({ next: (p) => (this.p = p), error: (e) => (this.error = e?.error?.message ?? 'Could not load the project') });
  }

  statusName(s: string): string { return (PROJECT_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  unitStatusName(s: string): string { return (UNIT_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  kindName(k: string): string { return unitKindLabel(k); }

  shown(): UnitRow[] {
    const q = this.fQ.trim().toLowerCase();
    return (this.p?.units ?? []).filter((u) => (!this.fStatus || u.status === this.fStatus) && (!this.fBlock || u.block === this.fBlock) && (!this.fKind || u.kind === this.fKind)
      && (!q || u.unitNo.toLowerCase().includes(q)));
  }

  manyExample(): string {
    const m = this.many;
    if (m.from == null || m.to == null || m.to < m.from) return '';
    const pad = (n: number) => (m.padding ? String(n).padStart(m.padding, '0') : String(n));
    return `For example ${m.prefix}${pad(m.from)} to ${m.prefix}${pad(m.to)} (${m.to - m.from + 1} units).`;
  }

  private run(call: import('rxjs').Observable<unknown>, done: () => void): void {
    this.actionError = null;
    this.notice = null;
    this.busy = true;
    call.subscribe({
      next: () => { this.busy = false; done(); this.load(); },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'That did not work'; },
    });
  }

  setStatus(s: 'SELLING'): void { this.run(this.api.updateProject(this.p!.id, { status: s }), () => (this.notice = 'The project is now selling: units can be reserved.')); }

  remove(): void {
    if (!confirm('Delete this project?')) return;
    this.api.deleteProject(this.p!.id).subscribe({ next: () => this.router.navigate(['/admin/realestate/projects']), error: (e) => (this.actionError = e?.error?.message ?? 'Could not delete it') });
  }

  addOne(): void {
    const o = this.one;
    if (!o.unitNo.trim()) { this.actionError = 'Give the unit a number.'; return; }
    if (!num(o.listPrice)) { this.actionError = 'Enter the list price.'; return; }
    this.run(this.api.addUnit(this.p!.id, { unitNo: o.unitNo.trim(), block: o.block, kind: o.kind, floor: num(o.floor), bedrooms: num(o.bedrooms), bathrooms: num(o.bathrooms), sizeSqm: num(o.sizeSqm), listPrice: num(o.listPrice) }),
      () => { this.one.unitNo = ''; this.notice = 'Unit added.'; });
  }

  addMany(): void {
    const m = this.many;
    if (m.from == null || m.to == null) { this.actionError = 'Give the first and the last number.'; return; }
    if (!num(m.listPrice)) { this.actionError = 'Enter the list price.'; return; }
    this.run(this.api.addUnitsBulk(this.p!.id, { prefix: m.prefix, from: num(m.from), to: num(m.to), padding: num(m.padding), block: m.block, kind: m.kind, floor: null, bedrooms: null, bathrooms: null, sizeSqm: num(m.sizeSqm), listPrice: num(m.listPrice) }),
      () => { this.panel = ''; this.notice = 'Units created.'; });
  }

  adjust(preview: boolean): void {
    const a = this.adj;
    if (!num(a.percent)) { this.actionError = 'Enter the percentage to change prices by.'; return; }
    this.actionError = null;
    this.notice = null;
    this.busy = true;
    this.api.adjustPrices(this.p!.id, { percent: num(a.percent), roundTo: num(a.roundTo), block: a.block, kind: a.kind, reason: a.reason.trim() || null, preview }).subscribe({
      next: (r) => {
        this.busy = false;
        if (preview) { this.previewed = r; return; }
        this.previewed = null;
        this.panel = '';
        this.notice = r.changed + ' price(s) changed.';
        this.load();
      },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'Could not change the prices'; },
    });
  }
}
