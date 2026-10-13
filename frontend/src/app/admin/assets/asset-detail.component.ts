import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { BranchApiService } from '../../core/api/branch-api.service';
import { Branch } from '../../core/models';
import { AssetApiService, AssetTransfer, DepreciationEntry, FixedAsset } from '../../core/api/asset-api.service';

/** Assets > one asset: its details, the monthly depreciation history, and Dispose (sell / write off). */
@Component({
  selector: 'app-asset-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <ng-container *ngIf="asset">
        <div class="flex justify-between align-center mb-3">
          <h3>{{ asset.code }} — {{ asset.name }}
            <span class="badge ml-2" [class.badge-success]="asset.status === 'ACTIVE'" [class.badge-secondary]="asset.status !== 'ACTIVE'">{{ statusLabel(asset.status) }}</span>
          </h3>
          <div class="flex gap-2">
            <a class="btn btn-outline-primary" routerLink="/admin/assets">Back</a>
            <a class="btn btn-outline-primary" *ngIf="asset.status !== 'DISPOSED'" [routerLink]="['/admin/assets', asset.id, 'edit']">Edit</a>
            <button class="btn btn-outline-primary" type="button" *ngIf="asset.status !== 'DISPOSED'" (click)="transferring = !transferring; disposing = false">Transfer…</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="asset.status !== 'DISPOSED'" (click)="disposing = !disposing; transferring = false">Dispose…</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="canDelete()" (click)="remove()">Delete</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="transferring">
          <strong class="mb-2">Transfer this asset</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            Depreciation up to the month before the transfer stays with the current branch; later months are charged to the new one.
            Past accounting entries are not changed.
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" *ngIf="branches.length > 1"><label class="form-label">To branch</label>
              <select class="form-select" name="toBranch" [(ngModel)]="move.toBranchId">
                <option [ngValue]="null">Keep {{ asset.branchName }}</option>
                <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">New location</label>
              <input class="form-control" name="toLocation" [(ngModel)]="move.toLocation" /></div>
            <div class="form-group"><label class="form-label">Assigned to</label>
              <input class="form-control" name="moveAssigned" [(ngModel)]="move.assignedTo" /></div>
            <div class="form-group"><label class="form-label">Date</label>
              <app-date-picker name="moveDate" [(ngModel)]="move.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">Reason</label>
              <input class="form-control" name="moveReason" [(ngModel)]="move.reason" /></div>
            <button class="btn btn-primary" type="button" (click)="transfer()" [disabled]="busy">Transfer</button>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="disposing">
          <strong class="mb-2">Dispose of this asset</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            Depreciation is charged for every full month before the disposal month, then Accounting records the cash received, removes the asset and its accumulated depreciation, and books the gain or loss. This can't be undone.
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">Disposal date</label>
              <app-date-picker name="disposalDate" [(ngModel)]="disposal.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">Sale proceeds (0 = written off)</label>
              <input class="form-control" type="number" min="0" step="0.01" name="proceeds" [(ngModel)]="disposal.proceeds" /></div>
            <div class="form-group"><label class="form-label">Note</label>
              <input class="form-control" name="note" [(ngModel)]="disposal.note" /></div>
            <button class="btn btn-primary" type="button" (click)="dispose()" [disabled]="busy">Dispose</button>
          </div>
        </div>

        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Cost</div><strong>{{ asset.cost | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Accumulated depreciation</div><strong>{{ asset.accumulatedDepreciation | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">Book value</div><strong>{{ asset.bookValue | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;" *ngIf="asset.status === 'DISPOSED'">
            <div class="text-muted">{{ (asset.disposalGainLoss || 0) >= 0 ? 'Gain on disposal' : 'Loss on disposal' }}</div>
            <strong>{{ asset.disposalGainLoss | number: '1.2-2' }}</strong>
          </div>
        </div>

        <table class="table-sm mb-3" style="max-width:640px;">
          <tbody>
            <tr><th>Category</th><td>{{ asset.categoryName }}</td></tr>
            <tr *ngIf="branches.length > 1"><th>Branch</th><td>{{ asset.branchName || '—' }}</td></tr>
            <tr><th>Purchased</th><td>{{ asset.purchaseDate }}<span *ngIf="asset.supplierName"> from {{ asset.supplierName }}</span><span *ngIf="asset.purchaseOrderNumber"> — purchase order {{ asset.purchaseOrderNumber }}</span></td></tr>
            <tr><th>Depreciation</th><td>{{ asset.method === 'STRAIGHT_LINE' ? 'Straight line' : 'Declining balance' }} over {{ asset.usefulLifeMonths }} months, from {{ asset.depreciationStartDate }}<span *ngIf="asset.prorateFirstMonth"> (first month by days)</span>; salvage value {{ asset.salvageValue | number: '1.2-2' }}</td></tr>
            <tr><th>Charged up to</th><td>{{ asset.lastDepreciatedThrough || 'Not yet' }}</td></tr>
            <tr><th>Location</th><td>{{ asset.location || '—' }}</td></tr>
            <tr><th>Assigned to</th><td>{{ asset.assignedTo || '—' }}</td></tr>
            <tr><th>Serial number</th><td>{{ asset.serialNumber || '—' }}</td></tr>
            <tr *ngIf="asset.notes"><th>Notes</th><td>{{ asset.notes }}</td></tr>
            <tr *ngIf="asset.status === 'DISPOSED'"><th>Disposed</th><td>{{ asset.disposalDate }} for {{ asset.disposalProceeds | number: '1.2-2' }}<span *ngIf="asset.disposalNote"> — {{ asset.disposalNote }}</span></td></tr>
          </tbody>
        </table>

        <ng-container *ngIf="transfers.length">
          <h4 class="mb-2">Transfers</h4>
          <table class="table-sm mb-3">
            <thead><tr><th>Date</th><th>From</th><th>To</th><th>Reason</th></tr></thead>
            <tbody>
              <tr *ngFor="let t of transfers">
                <td>{{ t.transferDate }}</td>
                <td>{{ t.fromBranchName }}<span *ngIf="t.fromLocation"> — {{ t.fromLocation }}</span></td>
                <td>{{ t.toBranchName }}<span *ngIf="t.toLocation"> — {{ t.toLocation }}</span></td>
                <td>{{ t.reason || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>

        <h4 class="mb-2">Depreciation history</h4>
        <table class="table-sm">
          <thead><tr><th>Month end</th><th class="text-end">Depreciation</th><th class="text-end">Accumulated</th><th class="text-end">Book value</th></tr></thead>
          <tbody>
            <tr *ngFor="let e of entries">
              <td>{{ e.periodEnd }}</td>
              <td class="text-end">{{ e.amount | number: '1.2-2' }}</td>
              <td class="text-end">{{ e.accumulatedAfter | number: '1.2-2' }}</td>
              <td class="text-end">{{ e.bookValueAfter | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="entries.length === 0">No depreciation charged yet — it is charged at the end of each month, or use Run now on the register.</p>
      </ng-container>
    </app-shell>
  `,
})
export class AssetDetailComponent implements OnInit {
  asset: FixedAsset | null = null;
  entries: DepreciationEntry[] = [];
  error: string | null = null;
  disposing = false;
  transferring = false;
  branches: Branch[] = [];
  transfers: AssetTransfer[] = [];
  move = { toBranchId: null as number | null, toLocation: '', assignedTo: '', date: new Date().toISOString().slice(0, 10), reason: '' };
  busy = false;
  disposal = { date: new Date().toISOString().slice(0, 10), proceeds: 0 as number | null, note: '' };

  constructor(private api: AssetApiService, private branchApi: BranchApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.get(id).subscribe({
      next: (a) => (this.asset = a),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the asset'),
    });
    this.api.depreciation(id).subscribe({ next: (d) => (this.entries = d), error: () => {} });
    this.api.transfers(id).subscribe({ next: (t) => (this.transfers = t), error: () => {} });
  }

  statusLabel(s: string): string {
    return s === 'ACTIVE' ? 'Active' : s === 'FULLY_DEPRECIATED' ? 'Fully depreciated' : 'Disposed';
  }

  canDelete(): boolean {
    return !!this.asset && !this.asset.hasDepreciation && !this.asset.acquisitionPosted && this.asset.status !== 'DISPOSED';
  }

  transfer(): void {
    if (!this.asset) return;
    this.error = null;
    this.busy = true;
    this.api
      .transfer(this.asset.id, {
        toBranchId: this.move.toBranchId,
        transferDate: this.move.date,
        toLocation: this.move.toLocation || null,
        assignedTo: this.move.assignedTo || null,
        reason: this.move.reason || null,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.transferring = false;
          this.move = { ...this.move, toBranchId: null, toLocation: '', assignedTo: '', reason: '' };
          this.load();
        },
        error: (e) => {
          this.busy = false;
          this.error = e?.error?.message ?? 'Could not transfer the asset';
        },
      });
  }

  dispose(): void {
    if (!this.asset) return;
    if (!confirm(`Dispose of ${this.asset.code} — ${this.asset.name}? This posts to Accounting and can't be undone.`)) return;
    this.error = null;
    this.busy = true;
    this.api.dispose(this.asset.id, { disposalDate: this.disposal.date, proceeds: this.disposal.proceeds ?? 0, note: this.disposal.note || null }).subscribe({
      next: () => {
        this.busy = false;
        this.disposing = false;
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? 'Could not dispose of the asset';
      },
    });
  }

  remove(): void {
    if (!this.asset || !confirm(`Delete ${this.asset.code} — ${this.asset.name}?`)) return;
    this.api.delete(this.asset.id).subscribe({
      next: () => this.router.navigate(['/admin/assets']),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the asset'),
    });
  }
}
