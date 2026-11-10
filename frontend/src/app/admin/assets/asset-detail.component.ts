import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { DatePickerComponent } from '../../shared/date-picker.component';
import { AuthService } from '../../core/auth.service';
import { BranchApiService } from '../../core/api/branch-api.service';
import { ModuleApiService } from '../../core/api/module-api.service';
import { downloadBlob, openPdfForPrint } from '../../core/download-file';
import { Branch } from '../../core/models';
import {
  AcquisitionFunding, AssetAdjustment, AssetApiService, AssetCustody, AssetEvent, AssetFile, AssetSettings, AssetTransfer, Custodian,
  DepreciationEntry, DepreciationMethod, FixedAsset,
} from '../../core/api/asset-api.service';

type Panel = '' | 'transfer' | 'dispose' | 'improve' | 'impair' | 'terms' | 'out' | 'in' | 'back' | 'undo';

/**
 * Assets > one asset: details, warranty and insurance, who holds it (check-out / check-in and a handover sheet), the cost and value
 * changes (improvement, impairment, new terms), the monthly depreciation history, photos and files, and the activity history.
 */
@Component({
  selector: 'app-asset-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, DatePickerComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <ng-container *ngIf="asset">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <h3>{{ asset.code }} — {{ asset.name }}
            <span class="badge ml-2" [class.badge-success]="asset.status === 'ACTIVE'" [class.badge-secondary]="asset.status !== 'ACTIVE'">{{ statusLabel(asset.status) }}</span>
          </h3>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <a class="btn btn-outline-primary" routerLink="/admin/assets">{{ 'Back' | translate }}</a>
            <a class="btn btn-outline-primary" *ngIf="modules.on('WORK_ORDERS')" routerLink="/admin/work-orders" [queryParams]="{ assetId: asset.id }">{{ 'Maintenance history' | translate }}</a>
            <a class="btn btn-outline-primary" *ngIf="canManage" [routerLink]="['/admin/assets/labels']" [queryParams]="{ ids: asset.id }">{{ 'Print tag' | translate }}</a>
            <a class="btn btn-outline-primary" *ngIf="canManage && asset.status !== 'DISPOSED'" [routerLink]="['/admin/assets', asset.id, 'edit']">{{ 'Edit' | translate }}</a>
            <button class="btn btn-outline-primary" type="button" *ngIf="canManage && asset.status !== 'DISPOSED'" (click)="toggle('transfer')">{{ 'Transfer…' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="canManage && asset.status !== 'DISPOSED'" (click)="toggle('dispose')">{{ 'Dispose…' | translate }}</button>
            <button class="btn btn-outline-primary" type="button" *ngIf="canManage && asset.status === 'DISPOSED' && canApproveDisposal" (click)="toggle('undo')">{{ 'Undo disposal…' | translate }}</button>
            <button class="btn btn-outline-secondary" type="button" *ngIf="canManage && canDelete()" (click)="remove()">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <div class="alert alert-warning" *ngIf="asset.warrantyState === 'EXPIRING' || asset.warrantyState === 'EXPIRED'">
          {{ 'Warranty {p0} on {p1}.' | translate: { p0: (asset.warrantyState === 'EXPIRED' ? 'ended' : 'ends'), p1: (asset.warrantyExpiresOn) } }}
        </div>
        <div class="alert alert-warning" *ngIf="asset.insuranceState === 'EXPIRING' || asset.insuranceState === 'EXPIRED'">
          {{ 'Insurance {p0} on {p1}' | translate: { p0: (asset.insuranceState === 'EXPIRED' ? 'ended' : 'ends'), p1: (asset.insuranceExpiresOn) } }}<span *ngIf="asset.insurancePolicy"> {{ '(policy {p0})' | translate: { p0: (asset.insurancePolicy) } }}</span>.
        </div>

        <!-- Transfer -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'transfer'">
          <strong class="mb-2">{{ 'Transfer this asset' | translate }}</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            {{ 'Depreciation up to the month before the transfer stays with the current branch; later months are charged to the new one. Past accounting entries are not changed.' | translate }}
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group" *ngIf="branches.length > 1"><label class="form-label">{{ 'To branch' | translate }}</label>
              <select class="form-select" name="toBranch" [(ngModel)]="move.toBranchId">
                <option [ngValue]="null">{{ 'Keep {p0}' | translate: { p0: (asset.branchName) } }}</option>
                <option *ngFor="let b of branches" [ngValue]="b.id">{{ b.name }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'New location' | translate }}</label>
              <input class="form-control" name="toLocation" [(ngModel)]="move.toLocation" /></div>
            <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label>
              <app-date-picker name="moveDate" [(ngModel)]="move.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label>
              <input class="form-control" name="moveReason" [(ngModel)]="move.reason" /></div>
            <button class="btn btn-primary" type="button" (click)="transfer()" [disabled]="busy">{{ 'Transfer' | translate }}</button>
          </div>
        </div>

        <!-- Dispose -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'dispose'">
          <strong class="mb-2">{{ 'Dispose of this asset' | translate }}</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            {{ 'Depreciation is charged for every full month before the disposal month, then Accounting records the cash received, removes the asset and its accumulated depreciation, and books the gain or loss. Someone with approval rights can undo a disposal if it was a mistake.' | translate }}
          </p>
          <p class="text-muted" style="font-size:0.9rem;" *ngIf="settings && settings.disposalApprovalLimit > 0">
            {{ 'A book value above {p0} needs an administrator or a user allowed to approve large asset disposals.' | translate: { p0: (settings.disposalApprovalLimit | number: '1.2-2') } }}
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Disposal date' | translate }}</label>
              <app-date-picker name="disposalDate" [(ngModel)]="disposal.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Sale proceeds (0 = written off)' | translate }}</label>
              <input class="form-control" type="number" min="0" step="0.01" name="proceeds" [(ngModel)]="disposal.proceeds" /></div>
            <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label>
              <input class="form-control" name="note" [(ngModel)]="disposal.note" /></div>
            <button class="btn btn-primary" type="button" (click)="dispose()" [disabled]="busy">{{ 'Dispose' | translate }}</button>
          </div>
        </div>

        <!-- Undo disposal -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'undo'">
          <strong class="mb-2">{{ 'Undo this disposal' | translate }}</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            {{ 'Accounting gets a reversing entry, the asset becomes active again with the same cost and depreciation, and depreciation can carry on from the month of the disposal.' | translate }}
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label>
              <input class="form-control" name="undoReason" [(ngModel)]="reason" /></div>
            <button class="btn btn-primary" type="button" (click)="undo()" [disabled]="busy">{{ 'Undo disposal' | translate }}</button>
          </div>
        </div>

        <!-- Improvement -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'improve'">
          <strong class="mb-2">{{ 'Add an improvement' | translate }}</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            {{ 'Money spent that makes the asset worth more or last longer is added to its cost. Depreciation is spread again over the remaining life from the month after the date you choose.' | translate }}
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="impDate" [(ngModel)]="imp.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" type="number" min="0.01" step="0.01" name="impAmount" [(ngModel)]="imp.amount" /></div>
            <div class="form-group"><label class="form-label">{{ 'Paid' | translate }}</label>
              <select class="form-select" name="impFunding" [(ngModel)]="imp.funding">
                <option value="NONE">{{ 'Already recorded elsewhere' | translate }}</option>
                <option value="CASH">{{ 'With cash' | translate }}</option>
                <option value="PAYABLE">{{ 'On credit (supplier bill)' | translate }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Remaining life (months)' | translate }}</label><input class="form-control" type="number" min="1" step="1" name="impLife" [(ngModel)]="imp.remainingLifeMonths" [placeholder]="'keep' | translate" /></div>
            <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="impNote" [(ngModel)]="imp.note" /></div>
            <button class="btn btn-primary" type="button" (click)="improve()" [disabled]="busy">{{ 'Add improvement' | translate }}</button>
          </div>
        </div>

        <!-- Impairment -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'impair'">
          <strong class="mb-2">{{ 'Write the value down (impairment)' | translate }}</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            {{ 'Use this when the asset is damaged or worth less than its book value. The amount is charged as an expense and added to the accumulated depreciation.' | translate }}
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="impairDate" [(ngModel)]="imr.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Amount' | translate }}</label><input class="form-control" type="number" min="0.01" step="0.01" name="impairAmount" [(ngModel)]="imr.amount" /></div>
            <div class="form-group"><label class="form-label">{{ 'Remaining life (months)' | translate }}</label><input class="form-control" type="number" min="1" step="1" name="impairLife" [(ngModel)]="imr.remainingLifeMonths" [placeholder]="'keep' | translate" /></div>
            <div class="form-group"><label class="form-label">{{ 'Why' | translate }}</label><input class="form-control" name="impairNote" [(ngModel)]="imr.note" /></div>
            <button class="btn btn-primary" type="button" (click)="impair()" [disabled]="busy">{{ 'Write down' | translate }}</button>
          </div>
        </div>

        <!-- New terms -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'terms'">
          <strong class="mb-2">{{ 'Change the depreciation terms' | translate }}</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            {{ 'Months already charged are not changed. From the month after the date you choose, the rest of the value is spread over the new remaining life.' | translate }}
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="termDate" [(ngModel)]="trm.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Remaining life (months)' | translate }}</label><input class="form-control" type="number" min="1" step="1" name="termLife" [(ngModel)]="trm.remainingLifeMonths" /></div>
            <div class="form-group"><label class="form-label">{{ 'Salvage value' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="termSalvage" [(ngModel)]="trm.salvageValue" [placeholder]="'keep' | translate" /></div>
            <div class="form-group"><label class="form-label">{{ 'Method' | translate }}</label>
              <select class="form-select" name="termMethod" [(ngModel)]="trm.method">
                <option [ngValue]="null">{{ 'Keep' | translate }}</option>
                <option value="STRAIGHT_LINE">{{ 'Straight line' | translate }}</option>
                <option value="DECLINING_BALANCE">{{ 'Declining balance' | translate }}</option>
              </select></div>
            <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="termNote" [(ngModel)]="trm.note" /></div>
            <button class="btn btn-primary" type="button" (click)="terms()" [disabled]="busy">{{ 'Change terms' | translate }}</button>
          </div>
        </div>

        <!-- Take back last month -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'back'">
          <strong class="mb-2">{{ 'Take back the last month of depreciation' | translate }}</strong>
          <p class="text-muted" style="font-size:0.9rem;">
            {{ 'Removes the charge for {p0} and posts a reversing entry to Accounting. It cannot be done if an improvement, write-down or change of terms is dated after that month.' | translate: { p0: (asset.lastDepreciatedThrough) } }}
          </p>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Reason' | translate }}</label><input class="form-control" name="backReason" [(ngModel)]="reason" /></div>
            <button class="btn btn-primary" type="button" (click)="takeBack()" [disabled]="busy">{{ 'Take it back' | translate }}</button>
          </div>
        </div>

        <!-- Check out / in -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'out'">
          <strong class="mb-2">{{ 'Hand this asset to someone' | translate }}</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'A user' | translate }}</label>
              <select class="form-select" name="outUser" [(ngModel)]="out.userId">
                <option [ngValue]="null">{{ '— or type a name —' | translate }}</option>
                <option *ngFor="let u of custodians" [ngValue]="u.id">{{ u.name }}</option>
              </select></div>
            <div class="form-group" *ngIf="!out.userId"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="outName" [(ngModel)]="out.name" /></div>
            <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="outDate" [(ngModel)]="out.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Condition' | translate }}</label><input class="form-control" name="outCond" [(ngModel)]="out.condition" [placeholder]="'e.g. good, small scratch' | translate" /></div>
            <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="outNote" [(ngModel)]="out.note" /></div>
            <button class="btn btn-primary" type="button" (click)="checkOut()" [disabled]="busy">{{ 'Hand over' | translate }}</button>
          </div>
        </div>
        <div class="card mb-3" style="padding:1rem;" *ngIf="panel === 'in'">
          <strong class="mb-2">{{ 'Take this asset back from {p0}' | translate: { p0: (asset.custodianName) } }}</strong>
          <div class="flex gap-3" style="flex-wrap:wrap; align-items:flex-end;">
            <div class="form-group"><label class="form-label">{{ 'Date' | translate }}</label><app-date-picker name="inDate" [(ngModel)]="back.date"></app-date-picker></div>
            <div class="form-group"><label class="form-label">{{ 'Condition' | translate }}</label><input class="form-control" name="inCond" [(ngModel)]="back.condition" /></div>
            <div class="form-group"><label class="form-label">{{ 'Note' | translate }}</label><input class="form-control" name="inNote" [(ngModel)]="back.note" /></div>
            <button class="btn btn-primary" type="button" (click)="checkIn()" [disabled]="busy">{{ 'Take back' | translate }}</button>
          </div>
        </div>

        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Cost' | translate }}</div><strong>{{ asset.cost | number: '1.2-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;" *ngIf="asset.originalCost !== asset.cost">{{ 'first cost {p0}' | translate: { p0: (asset.originalCost | number: '1.2-2') } }}</div></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Accumulated depreciation' | translate }}</div><strong>{{ asset.accumulatedDepreciation | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;"><div class="text-muted">{{ 'Book value' | translate }}</div><strong>{{ asset.bookValue | number: '1.2-2' }}</strong></div>
          <div class="card" style="padding:0.75rem 1rem;" *ngIf="asset.status !== 'DISPOSED' && asset.nextCharge != null">
            <div class="text-muted">{{ 'Next month\\'s charge' | translate }}</div><strong>{{ asset.nextCharge | number: '1.2-2' }}</strong>
            <div class="text-muted" style="font-size:0.75rem;" *ngIf="asset.monthsLeft != null">{{ '{p0} month(s) left' | translate: { p0: (asset.monthsLeft) } }}</div></div>
          <div class="card" style="padding:0.75rem 1rem;" *ngIf="asset.status === 'DISPOSED'">
            <div class="text-muted">{{ (asset.disposalGainLoss || 0) >= 0 ? ('Gain on disposal' | translate) : ('Loss on disposal' | translate) }}</div>
            <strong>{{ asset.disposalGainLoss | number: '1.2-2' }}</strong>
          </div>
        </div>

        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="canManage && asset.status !== 'DISPOSED'">
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="toggle('improve')">{{ 'Add improvement…' | translate }}</button>
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="toggle('impair')">{{ 'Write value down…' | translate }}</button>
          <button class="btn btn-sm btn-outline-primary" type="button" (click)="toggle('terms')">{{ 'Change terms…' | translate }}</button>
          <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="asset.hasDepreciation" (click)="toggle('back')">{{ 'Take back last month…' | translate }}</button>
        </div>

        <table class="table-sm mb-3" style="max-width:680px;">
          <tbody>
            <tr><th>{{ 'Category' | translate }}</th><td>{{ asset.categoryName }}</td></tr>
            <tr *ngIf="branches.length > 1"><th>{{ 'Branch' | translate }}</th><td>{{ asset.branchName || '—' }}</td></tr>
            <tr><th>{{ 'Purchased' | translate }}</th><td>{{ asset.purchaseDate }}<span *ngIf="asset.supplierName"> {{ 'from {p0}' | translate: { p0: (asset.supplierName) } }}</span><span *ngIf="asset.purchaseOrderNumber"> {{ '— purchase order {p0}' | translate: { p0: (asset.purchaseOrderNumber) } }}</span></td></tr>
            <tr><th>{{ 'Depreciation' | translate }}</th><td>{{ '{p0} over {p1} months, from {p2}' | translate: { p0: (asset.method === 'STRAIGHT_LINE' ? 'Straight line' : 'Declining balance'), p1: (asset.usefulLifeMonths), p2: (asset.depreciationStartDate) } }}<span *ngIf="asset.prorateFirstMonth"> {{ '(first month by days)' | translate }}</span>{{ '; salvage value {p0}' | translate: { p0: (asset.salvageValue | number: '1.2-2') } }}<span *ngIf="asset.rebased"> {{ '— terms changed after depreciation started' | translate }}</span></td></tr>
            <tr><th>{{ 'Charged up to' | translate }}</th><td>{{ asset.lastDepreciatedThrough || ('Not yet' | translate) }}</td></tr>
            <tr><th>{{ 'Location' | translate }}</th><td>{{ asset.location || '—' }}</td></tr>
            <tr><th>{{ 'Held by' | translate }}</th><td>{{ asset.custodianName || asset.assignedTo || '—' }}<span *ngIf="asset.checkedOutOn" class="text-muted"> {{ 'since {p0}' | translate: { p0: (asset.checkedOutOn) } }}</span></td></tr>
            <tr><th>{{ 'Serial number' | translate }}</th><td>{{ asset.serialNumber || '—' }}</td></tr>
            <tr><th>{{ 'Warranty until' | translate }}</th><td>{{ asset.warrantyExpiresOn || '—' }}</td></tr>
            <tr><th>{{ 'Insurance until' | translate }}</th><td>{{ asset.insuranceExpiresOn || '—' }}<span *ngIf="asset.insurancePolicy"> {{ '— policy {p0}' | translate: { p0: (asset.insurancePolicy) } }}</span></td></tr>
            <tr *ngIf="asset.notes"><th>{{ 'Notes' | translate }}</th><td>{{ asset.notes }}</td></tr>
            <tr *ngIf="asset.status === 'DISPOSED'"><th>{{ 'Disposed' | translate }}</th><td>{{ '{p0} for {p1}' | translate: { p0: (asset.disposalDate), p1: (asset.disposalProceeds | number: '1.2-2') } }}<span *ngIf="asset.disposalNote"> — {{ asset.disposalNote }}</span></td></tr>
          </tbody>
        </table>

        <!-- Custody -->
        <div class="mb-3">
          <div class="flex justify-between align-center mb-2">
            <h4>{{ 'Who holds it' | translate }}</h4>
            <div class="flex gap-2" *ngIf="canManage && asset.status !== 'DISPOSED'">
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="!asset.custodianName" (click)="toggle('out')">{{ 'Hand over…' | translate }}</button>
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="asset.custodianName" (click)="toggle('in')">{{ 'Take back…' | translate }}</button>
              <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="asset.custodianName" (click)="handover()">{{ 'Handover sheet (PDF)' | translate }}</button>
            </div>
          </div>
          <table class="table-sm" *ngIf="custody.length">
            <thead><tr><th>{{ 'Held by' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Condition out' | translate }}</th><th>{{ 'Condition back' | translate }}</th><th>{{ 'Note' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let c of custody">
                <td>{{ c.custodianName }}</td><td>{{ c.checkedOutOn }}</td><td>{{ c.checkedInOn || ('now' | translate) }}</td>
                <td>{{ c.outCondition || '—' }}</td><td>{{ c.inCondition || '—' }}</td><td>{{ c.note || '—' }}</td>
              </tr>
            </tbody>
          </table>
          <p class="text-muted" *ngIf="!custody.length">{{ 'Nobody has held this asset yet.' | translate }}</p>
        </div>

        <!-- Adjustments -->
        <div class="mb-3" *ngIf="adjustments.length">
          <h4 class="mb-2">{{ 'Cost and value changes' | translate }}</h4>
          <table class="table-sm">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'What' | translate }}</th><th class="text-end">{{ 'Amount' | translate }}</th><th>{{ 'Remaining life' | translate }}</th><th class="text-end">{{ 'Cost after' | translate }}</th><th class="text-end">{{ 'Book value after' | translate }}</th><th>{{ 'Note' | translate }}</th></tr></thead>
            <tbody>
              <tr *ngFor="let a of adjustments">
                <td>{{ a.adjustmentDate }}</td>
                <td>{{ a.kind === 'IMPROVEMENT' ? 'Improvement' : a.kind === 'IMPAIRMENT' ? ('Write-down' | translate) : ('New terms' | translate) }}</td>
                <td class="text-end">{{ a.kind === 'TERMS' ? '' : (a.amount | number: '1.2-2') }}</td>
                <td>{{ a.remainingLifeMonths != null ? a.remainingLifeMonths + ' months' : '—' }}</td>
                <td class="text-end">{{ a.costAfter | number: '1.2-2' }}</td>
                <td class="text-end">{{ a.bookValueAfter | number: '1.2-2' }}</td>
                <td>{{ a.note || '' }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <ng-container *ngIf="transfers.length">
          <h4 class="mb-2">{{ 'Transfers' | translate }}</h4>
          <table class="table-sm mb-3">
            <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'From' | translate }}</th><th>{{ 'To' | translate }}</th><th>{{ 'Reason' | translate }}</th></tr></thead>
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

        <!-- Files -->
        <div class="mb-3">
          <h4 class="mb-2">{{ 'Photos and files' | translate }}</h4>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <div *ngFor="let f of files" style="width:8rem;">
              <img *ngIf="f.image && thumbs[f.id]" [src]="thumbs[f.id]" [alt]="f.fileName" style="width:100%; height:5.5rem; object-fit:cover; border-radius:4px; cursor:pointer;" (click)="openFile(f)" />
              <div *ngIf="!f.image || !thumbs[f.id]" style="height:5.5rem; display:flex; align-items:center; justify-content:center; font-size:0.75rem; text-align:center; word-break:break-all;">{{ f.fileName }}</div>
              <div style="font-size:0.75rem; word-break:break-all;"><a href="javascript:void(0)" (click)="openFile(f)">{{ f.fileName }}</a></div>
              <div class="text-muted" style="font-size:0.7rem;">{{ '{p0} KB · {p1}' | translate: { p0: (f.sizeBytes / 1024 | number: '1.0-0'), p1: (f.uploadedByName || '') } }}</div>
              <button type="button" class="btn btn-link btn-sm" style="padding:0;" *ngIf="canManage" (click)="removeFile(f)">{{ 'Remove' | translate }}</button>
            </div>
          </div>
          <p class="text-muted" *ngIf="files.length === 0">{{ 'No files yet.' | translate }}</p>
          <div class="mt-2" *ngIf="canManage">
            <input type="file" multiple accept="image/jpeg,image/png,image/gif,image/webp,application/pdf,text/plain" (change)="addFiles($event)" />
            <span class="text-muted" style="font-size:0.8rem;"> {{ 'Photos, invoices, warranty cards or manuals — up to 10 MB each, 20 per asset.' | translate }}</span>
          </div>
        </div>

        <h4 class="mb-2">{{ 'Depreciation history' | translate }}</h4>
        <table class="table-sm mb-3">
          <thead><tr><th>{{ 'Month end' | translate }}</th><th class="text-end">{{ 'Depreciation' | translate }}</th><th class="text-end">{{ 'Accumulated' | translate }}</th><th class="text-end">{{ 'Book value' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let e of entries">
              <td>{{ e.periodEnd }}</td>
              <td class="text-end">{{ e.amount | number: '1.2-2' }}</td>
              <td class="text-end">{{ e.accumulatedAfter | number: '1.2-2' }}</td>
              <td class="text-end">{{ e.bookValueAfter | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>
        <p class="text-muted" *ngIf="entries.length === 0">{{ 'No depreciation charged yet — it is charged at the end of each month, or use Run now on the register.' | translate }}</p>

        <h4 class="mb-2">{{ 'Activity' | translate }}</h4>
        <table class="table-sm">
          <tbody>
            <tr *ngFor="let ev of events">
              <td style="white-space:nowrap;">{{ ev.createdAt | date: 'yyyy-MM-dd HH:mm' }}</td>
              <td>{{ eventLabel(ev.kind) }}</td><td>{{ ev.detail }}</td><td>{{ ev.createdByName }}</td>
            </tr>
          </tbody>
        </table>
      </ng-container>
    </app-shell>
  `,
})
export class AssetDetailComponent implements OnInit {
  asset: FixedAsset | null = null;
  entries: DepreciationEntry[] = [];
  transfers: AssetTransfer[] = [];
  adjustments: AssetAdjustment[] = [];
  custody: AssetCustody[] = [];
  events: AssetEvent[] = [];
  files: AssetFile[] = [];
  thumbs: Record<number, string> = {};
  custodians: Custodian[] = [];
  settings: AssetSettings | null = null;
  branches: Branch[] = [];
  error: string | null = null;
  notice: string | null = null;
  busy = false;
  panel: Panel = '';
  reason = '';
  private today = new Date().toISOString().slice(0, 10);
  move = { toBranchId: null as number | null, toLocation: '', date: this.today, reason: '' };
  disposal = { date: this.today, proceeds: 0 as number | null, note: '' };
  imp = { date: this.today, amount: null as number | null, funding: 'NONE' as AcquisitionFunding, remainingLifeMonths: null as number | null, note: '' };
  imr = { date: this.today, amount: null as number | null, remainingLifeMonths: null as number | null, note: '' };
  trm = { date: this.today, remainingLifeMonths: null as number | null, salvageValue: null as number | null, method: null as DepreciationMethod | null, note: '' };
  out = { userId: null as number | null, name: '', date: this.today, condition: '', note: '' };
  back = { date: this.today, condition: '', note: '' };

  constructor(
    private api: AssetApiService, private branchApi: BranchApiService, private route: ActivatedRoute, private router: Router,
    public modules: ModuleApiService, private auth: AuthService
  ) {}

  get canManage(): boolean {
    return this.auth.canManage('ACCOUNTING');
  }

  get canApproveDisposal(): boolean {
    return this.auth.isAdmin() || this.auth.hasPermission('APPROVE_ASSET_DISPOSAL');
  }

  private get id(): number {
    return Number(this.route.snapshot.paramMap.get('id'));
  }

  ngOnInit(): void {
    this.branchApi.loadChoices().subscribe((b) => (this.branches = b));
    this.api.custodians().subscribe({ next: (c) => (this.custodians = c), error: () => {} });
    this.api.settings().subscribe({ next: (s) => (this.settings = s), error: () => {} });
    this.load();
  }

  load(): void {
    const id = this.id;
    this.api.get(id).subscribe({
      next: (a) => (this.asset = a),
      error: (e) => (this.error = e?.error?.message ?? 'Failed to load the asset'),
    });
    this.api.depreciation(id).subscribe({ next: (d) => (this.entries = d), error: () => {} });
    this.api.transfers(id).subscribe({ next: (t) => (this.transfers = t), error: () => {} });
    this.api.adjustments(id).subscribe({ next: (a) => (this.adjustments = a), error: () => {} });
    this.api.custody(id).subscribe({ next: (c) => (this.custody = c), error: () => {} });
    this.api.events(id).subscribe({ next: (e) => (this.events = e), error: () => {} });
    this.api.files(id).subscribe({
      next: (l) => {
        this.files = l;
        l.filter((f) => f.image && !this.thumbs[f.id]).forEach((f) =>
          this.api.fileBlob(id, f.id).subscribe({ next: (b) => (this.thumbs = { ...this.thumbs, [f.id]: URL.createObjectURL(b) }), error: () => {} })
        );
      },
      error: () => {},
    });
  }

  statusLabel(s: string): string {
    return s === 'ACTIVE' ? 'Active' : s === 'FULLY_DEPRECIATED' ? 'Fully depreciated' : 'Disposed';
  }

  eventLabel(k: string): string {
    const m: Record<string, string> = {
      REGISTERED: 'Registered', UPDATED: 'Edited', TRANSFER: 'Moved', DISPOSED: 'Disposed', DISPOSAL_UNDONE: 'Disposal undone', IMPROVEMENT: 'Improvement',
      IMPAIRMENT: 'Written down', TERMS: 'New terms', DEPRECIATION_BACK: 'Depreciation taken back', CHECK_OUT: 'Handed over', CHECK_IN: 'Taken back',
      FILE: 'File', VERIFIED: 'Verified', LOCATION: 'Place changed',
    };
    return m[k] ?? k;
  }

  canDelete(): boolean {
    return !!this.asset && !this.asset.hasDepreciation && !this.asset.acquisitionPosted && this.asset.status !== 'DISPOSED';
  }

  toggle(p: Panel): void {
    this.panel = this.panel === p ? '' : p;
    this.error = this.notice = null;
    this.reason = '';
  }

  /** Runs one action: shows the error from the server, or closes the panel, says what was done and reloads. */
  private act(call: import('rxjs').Observable<unknown>, done: string, failure: string): void {
    this.error = this.notice = null;
    this.busy = true;
    call.subscribe({
      next: () => {
        this.busy = false;
        this.panel = '';
        this.notice = done;
        this.load();
      },
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? failure;
      },
    });
  }

  transfer(): void {
    if (!this.asset) return;
    this.act(
      this.api.transfer(this.asset.id, { toBranchId: this.move.toBranchId, transferDate: this.move.date, toLocation: this.move.toLocation || null, assignedTo: null, reason: this.move.reason || null }),
      'The asset was moved.', 'Could not transfer the asset'
    );
    this.move = { ...this.move, toBranchId: null, toLocation: '', reason: '' };
  }

  dispose(): void {
    if (!this.asset) return;
    if (!confirm(`Dispose of ${this.asset.code} — ${this.asset.name}? This posts to Accounting. An administrator can undo it later.`)) return;
    this.act(this.api.dispose(this.asset.id, { disposalDate: this.disposal.date, proceeds: this.disposal.proceeds ?? 0, note: this.disposal.note || null }), 'The asset was disposed of.', 'Could not dispose of the asset');
  }

  undo(): void {
    if (!this.asset || !confirm(`Undo the disposal of ${this.asset.code}? Accounting gets a reversing entry.`)) return;
    this.act(this.api.undoDisposal(this.asset.id, this.reason.trim() || null), 'The disposal was undone.', 'Could not undo the disposal');
  }

  improve(): void {
    if (!this.asset) return;
    if (!this.imp.amount || this.imp.amount <= 0) {
      this.error = 'Enter the amount of the improvement.';
      return;
    }
    this.act(
      this.api.improve(this.asset.id, { date: this.imp.date, amount: this.imp.amount, funding: this.imp.funding, remainingLifeMonths: this.imp.remainingLifeMonths || null, note: this.imp.note.trim() || null }),
      'The improvement was added.', 'Could not add the improvement'
    );
  }

  impair(): void {
    if (!this.asset) return;
    if (!this.imr.amount || this.imr.amount <= 0 || !this.imr.note.trim()) {
      this.error = 'Enter the amount and say why the value is lower.';
      return;
    }
    this.act(
      this.api.impair(this.asset.id, { date: this.imr.date, amount: this.imr.amount, remainingLifeMonths: this.imr.remainingLifeMonths || null, note: this.imr.note.trim() }),
      'The value was written down.', 'Could not write the value down'
    );
  }

  terms(): void {
    if (!this.asset) return;
    if (!this.trm.remainingLifeMonths || this.trm.remainingLifeMonths < 1) {
      this.error = 'Enter the remaining life in months.';
      return;
    }
    this.act(
      this.api.changeTerms(this.asset.id, { date: this.trm.date, remainingLifeMonths: this.trm.remainingLifeMonths, salvageValue: this.trm.salvageValue, method: this.trm.method, note: this.trm.note.trim() || null }),
      'The depreciation terms were changed.', 'Could not change the terms'
    );
  }

  takeBack(): void {
    if (!this.asset || !confirm(`Take back the depreciation charged for ${this.asset.lastDepreciatedThrough}?`)) return;
    this.act(this.api.reverseDepreciation(this.asset.id, this.reason.trim() || null), 'The last month of depreciation was taken back.', 'Could not take back the depreciation');
  }

  checkOut(): void {
    if (!this.asset) return;
    if (!this.out.userId && !this.out.name.trim()) {
      this.error = 'Choose a user or type a name.';
      return;
    }
    this.act(
      this.api.checkOut(this.asset.id, { custodianUserId: this.out.userId, custodianName: this.out.userId ? null : this.out.name.trim(), date: this.out.date, condition: this.out.condition.trim() || null, note: this.out.note.trim() || null }),
      'The asset was handed over.', 'Could not hand the asset over'
    );
    this.out = { ...this.out, userId: null, name: '', condition: '', note: '' };
  }

  checkIn(): void {
    if (!this.asset) return;
    this.act(this.api.checkIn(this.asset.id, { date: this.back.date, condition: this.back.condition.trim() || null, note: this.back.note.trim() || null }), 'The asset was taken back.', 'Could not take the asset back');
    this.back = { ...this.back, condition: '', note: '' };
  }

  handover(): void {
    if (!this.asset) return;
    this.api.handoverPdf(this.asset.id).subscribe({
      next: (b) => openPdfForPrint(b),
      error: (e) => (this.error = e?.error?.message ?? 'Could not make the handover sheet'),
    });
  }

  addFiles(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const list = Array.from(input.files ?? []);
    input.value = '';
    if (!this.asset || !list.length) return;
    this.error = null;
    let pending = list.length;
    list.forEach((f) =>
      this.api.addFile(this.asset!.id, f).subscribe({
        next: () => {
          if (--pending === 0) this.load();
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Could not add ' + f.name;
          if (--pending === 0) this.load();
        },
      })
    );
  }

  openFile(f: AssetFile): void {
    this.api.fileBlob(this.id, f.id).subscribe({
      next: (b) => (f.contentType === 'text/plain' ? downloadBlob(b, f.fileName) : openPdfForPrint(b)),
      error: (e) => (this.error = e?.error?.message ?? 'Could not open the file'),
    });
  }

  removeFile(f: AssetFile): void {
    if (!confirm('Remove ' + f.fileName + '?')) return;
    this.api.deleteFile(this.id, f.id).subscribe({
      next: () => this.load(),
      error: (e) => (this.error = e?.error?.message ?? 'Could not remove the file'),
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
