import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { VALUATION_METHODS, VALUATION_PURPOSES, ValuationDto, ValuationMethod, ValuationPurpose, RealEstateApiService } from '../../core/api/realestate-api.service';
import { ReDocumentsComponent } from './re-documents.component';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));

/** The valuations of one property (newest first); the newest one still valid is the current valuation. Each valuation can carry its report as a document. */
@Component({
  selector: 'app-re-valuations',
  standalone: true,
  imports: [CommonModule, FormsModule, ReDocumentsComponent],
  template: `
    <div class="flex justify-between align-center mb-2">
      <h4>Valuations</h4>
      <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="canManage && !formOpen" (click)="openForm(null)">+ Record a valuation</button>
    </div>
    <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

    <form *ngIf="formOpen" (ngSubmit)="save()" class="mb-3" style="padding:0.75rem; border:1px solid rgba(127,127,127,0.3); border-radius:6px;">
      <strong>{{ editId ? 'Change the valuation' : 'New valuation' }}</strong>
      <div class="flex gap-2 mt-1" style="flex-wrap:wrap;">
        <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">Valuation date *</label><input class="form-control" type="date" name="vd" [(ngModel)]="f.valuationDate" /></div>
        <div class="form-group mb-2" style="flex:2; min-width:12rem;"><label class="form-label">Valuer *</label><input class="form-control" name="vn" [(ngModel)]="f.valuerName" maxlength="200" /></div>
        <div class="form-group mb-2" style="flex:2; min-width:12rem;"><label class="form-label">Firm</label><input class="form-control" name="vf" [(ngModel)]="f.valuerFirm" maxlength="200" /></div>
      </div>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <div class="form-group mb-2" style="min-width:9rem;"><label class="form-label">Purpose</label>
          <select class="form-select" name="vp" [(ngModel)]="f.purpose"><option *ngFor="let x of purposes" [ngValue]="x.value">{{ x.label }}</option></select></div>
        <div class="form-group mb-2" style="min-width:11rem;"><label class="form-label">Method</label>
          <select class="form-select" name="vm" [(ngModel)]="f.method"><option *ngFor="let x of methods" [ngValue]="x.value">{{ x.label }}</option></select></div>
        <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Market value *</label><input class="form-control" type="number" min="0" name="mv" [(ngModel)]="f.marketValue" /></div>
        <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Forced-sale value</label><input class="form-control" type="number" min="0" name="fv" [(ngModel)]="f.forcedSaleValue" [placeholder]="editId ? '0 to clear' : ''" /></div>
        <div class="form-group mb-2" style="min-width:12rem;"><label class="form-label">Monthly rent</label><input class="form-control" type="number" min="0" name="mr" [(ngModel)]="f.monthlyRent" [placeholder]="editId ? '0 to clear' : ''" /></div>
        <div class="form-group mb-2" style="min-width:10rem;"><label class="form-label">Valid until</label><input class="form-control" type="date" name="vu" [(ngModel)]="f.validUntil" /></div>
      </div>
      <div class="form-group mb-2"><label class="form-label">Notes</label><textarea class="form-control" rows="2" name="vt" [(ngModel)]="f.notes" maxlength="2000"></textarea></div>
      <div class="flex gap-2">
        <button class="btn btn-sm btn-primary" type="submit" [disabled]="busy">{{ busy ? 'Saving…' : 'Save' }}</button>
        <button class="btn btn-sm btn-outline-secondary" type="button" (click)="formOpen = false">Cancel</button>
      </div>
    </form>

    <table class="table-sm" *ngIf="list.length">
      <thead><tr><th>Date</th><th>Valuer</th><th>Purpose</th><th class="text-end">Market value</th><th class="text-end">Forced sale</th><th class="text-end">Rent / month</th><th>Valid until</th><th></th></tr></thead>
      <tbody>
        <ng-container *ngFor="let v of list">
          <tr>
            <td style="white-space:nowrap;">{{ v.valuationDate | date: 'd MMM y' }}
              <span class="badge badge-success" *ngIf="v.current" style="margin-left:0.3rem;">Current</span>
              <span class="badge badge-danger" *ngIf="v.expired" style="margin-left:0.3rem;">Expired</span></td>
            <td>{{ v.valuerName }}<div class="text-muted" style="font-size:0.8rem;">{{ v.valuerFirm || '' }}{{ v.valuerFirm ? ' · ' : '' }}{{ methodName(v.method) }}</div></td>
            <td>{{ purposeName(v.purpose) }}</td>
            <td class="text-end">{{ v.marketValue | number: '1.0-0' }}
              <div class="text-muted" style="font-size:0.75rem;" *ngIf="v.vsAskingPercent != null">{{ v.vsAskingPercent > 0 ? '+' : '' }}{{ v.vsAskingPercent | number: '1.0-1' }}% vs asking</div></td>
            <td class="text-end">{{ v.forcedSaleValue != null ? (v.forcedSaleValue | number: '1.0-0') : '—' }}</td>
            <td class="text-end">{{ v.monthlyRent != null ? (v.monthlyRent | number: '1.0-0') : '—' }}</td>
            <td style="white-space:nowrap;">{{ v.validUntil ? (v.validUntil | date: 'd MMM y') : '—' }}</td>
            <td style="white-space:nowrap; text-align:right;">
              <button class="btn btn-sm btn-outline-secondary" type="button" (click)="toggleDocs(v)">Report{{ v.documents ? ' (' + v.documents + ')' : '' }}</button>
              <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="canManage" style="margin-left:0.2rem;" (click)="openForm(v)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="canManage" style="margin-left:0.2rem;" (click)="remove(v)">Delete</button>
            </td>
          </tr>
          <tr *ngIf="v.notes"><td colspan="8" class="text-muted" style="font-size:0.85rem; border-top:0;">{{ v.notes }}</td></tr>
          <tr *ngIf="openDocs === v.id">
            <td colspan="8"><app-re-documents subjectType="VALUATION" [subjectId]="v.id" [canManage]="canManage" [title]="null" defaultCategory="VALUATION_REPORT" (changed)="docsChanged(v, $event)"></app-re-documents></td>
          </tr>
        </ng-container>
      </tbody>
    </table>
    <div class="text-muted" *ngIf="!list.length && loaded">No valuations yet.</div>
  `,
})
export class ReValuationsComponent implements OnChanges {
  @Input({ required: true }) propertyId!: number;
  @Input() canManage = false;
  @Output() changed = new EventEmitter<void>();

  purposes = VALUATION_PURPOSES;
  methods = VALUATION_METHODS;
  list: ValuationDto[] = [];
  loaded = false;
  formOpen = false;
  editId: number | null = null;
  hadValidUntil = false;
  busy = false;
  error: string | null = null;
  openDocs: number | null = null;
  f = this.blank();

  constructor(private api: RealEstateApiService) {}

  private blank() {
    return {
      valuationDate: new Date().toISOString().slice(0, 10), valuerName: '', valuerFirm: '', purpose: 'SALE' as ValuationPurpose, method: 'COMPARABLE' as ValuationMethod,
      marketValue: null as number | null, forcedSaleValue: null as number | null, monthlyRent: null as number | null, validUntil: '', notes: '',
    };
  }

  ngOnChanges(): void {
    this.load();
  }

  private load(): void {
    this.api.valuations({ propertyId: this.propertyId }).subscribe({
      next: (l) => { this.list = l; this.loaded = true; },
      error: (e) => { this.loaded = true; this.error = e?.error?.message ?? 'Could not load the valuations'; },
    });
  }

  purposeName(p: string): string { return VALUATION_PURPOSES.find((x) => x.value === p)?.label ?? p; }
  methodName(m: string): string { return VALUATION_METHODS.find((x) => x.value === m)?.label ?? m; }

  openForm(v: ValuationDto | null): void {
    this.error = null;
    this.formOpen = true;
    this.editId = v?.id ?? null;
    this.hadValidUntil = !!v?.validUntil;
    this.f = v
      ? { valuationDate: v.valuationDate, valuerName: v.valuerName, valuerFirm: v.valuerFirm ?? '', purpose: v.purpose, method: v.method, marketValue: v.marketValue, forcedSaleValue: v.forcedSaleValue,
          monthlyRent: v.monthlyRent, validUntil: v.validUntil ?? '', notes: v.notes ?? '' }
      : this.blank();
  }

  save(): void {
    this.error = null;
    const f = this.f;
    if (!f.valuationDate) { this.error = 'Give the valuation date.'; return; }
    if (!f.valuerName.trim()) { this.error = 'Give the valuer\'s name.'; return; }
    if (!num(f.marketValue)) { this.error = 'Enter the market value.'; return; }
    const edit = this.editId != null;
    const p = {
      valuationDate: f.valuationDate, valuerName: f.valuerName.trim(), valuerFirm: f.valuerFirm, purpose: f.purpose, method: f.method, marketValue: num(f.marketValue),
      // On an edit, an empty box means 0 = clear the optional amount.
      forcedSaleValue: edit ? (num(f.forcedSaleValue) ?? 0) : num(f.forcedSaleValue), monthlyRent: edit ? (num(f.monthlyRent) ?? 0) : num(f.monthlyRent),
      validUntil: f.validUntil || null, clearValidUntil: edit && this.hadValidUntil && !f.validUntil, notes: f.notes,
    };
    this.busy = true;
    const call = edit ? this.api.updateValuation(this.editId!, p) : this.api.createValuation({ ...p, propertyId: this.propertyId });
    call.subscribe({
      next: () => { this.busy = false; this.formOpen = false; this.load(); this.changed.emit(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'Could not save the valuation'; },
    });
  }

  toggleDocs(v: ValuationDto): void {
    this.openDocs = this.openDocs === v.id ? null : v.id;
  }

  docsChanged(v: ValuationDto, n: number): void {
    v.documents = n;
  }

  remove(v: ValuationDto): void {
    if (!confirm('Delete this valuation and its documents?')) return;
    this.error = null;
    this.api.deleteValuation(v.id).subscribe({ next: () => { this.load(); this.changed.emit(); }, error: (e) => (this.error = e?.error?.message ?? 'Could not delete the valuation') });
  }
}
