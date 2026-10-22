import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { PosSettings } from '../core/models';

/** POS > Settings: the rules for cash at the till (allowed difference, notes, counts, paid-out approval, safe-drop reminder). */
@Component({
  selector: 'app-pos-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">Point of sale settings</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">Saved.</div>
      <div *ngIf="s" class="card" style="padding:1rem; max-width:720px;">
        <h4 style="font-size:1rem;" class="mb-2">Cash at the till</h4>
        <div class="form-group mb-3">
          <label class="form-label">A difference up to this amount at closing is accepted without comment</label>
          <input class="form-control ps-tolerance" type="number" min="0" step="1" style="width:160px;" [(ngModel)]="s.varianceTolerance" name="tol" />
        </div>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <label><input class="ps-note" type="checkbox" [(ngModel)]="s.varianceNoteRequired" name="note" /> A bigger difference needs a note saying why</label>
          <label><input class="ps-denoms" type="checkbox" [(ngModel)]="s.denominationCountRequired" name="den" /> Closing a shift needs the notes and coins counted</label>
        </div>
        <div class="form-group mb-3">
          <label class="form-label">A paid-out from the drawer above this amount needs a manager's PIN (0 = no limit)</label>
          <input class="form-control ps-paidout" type="number" min="0" step="1" style="width:160px;" [(ngModel)]="s.paidOutApprovalOver" name="po" />
        </div>
        <div class="form-group mb-3">
          <label class="form-label">Remind the cashier to drop cash into the safe when the drawer holds more than (0 = no reminder)</label>
          <input class="form-control ps-drop" type="number" min="0" step="1" style="width:160px;" [(ngModel)]="s.dropAlertAmount" name="drop" />
        </div>
        <h4 style="font-size:1rem;" class="mb-2 mt-3">Voids and returns</h4>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <label><input class="ps-voidappr" type="checkbox" [(ngModel)]="s.voidRequiresApproval" name="va" /> Voiding a sale needs a manager (their sign-in or PIN)</label>
          <label><input class="ps-voidshift" type="checkbox" [(ngModel)]="s.voidSameShiftOnly" name="vs" /> A sale can be voided only while its shift is open (afterwards it is a return)</label>
          <label><input class="ps-retreason" type="checkbox" [(ngModel)]="s.returnReasonRequired" name="rr" /> A return needs a reason</label>
        </div>
        <div class="form-group mb-3">
          <label class="form-label">Returns of sales older than this many days need a manager (0 = no limit)</label>
          <input class="form-control ps-window" type="number" min="0" step="1" style="width:160px;" [(ngModel)]="s.returnWindowDays" name="rw" />
        </div>
        <div class="form-group mb-3">
          <label class="form-label">A refund above this amount needs a manager (0 = no limit)</label>
          <input class="form-control ps-retover" type="number" min="0" step="1" style="width:160px;" [(ngModel)]="s.returnApprovalOver" name="ro" />
        </div>
        <div class="form-group mb-3">
          <label class="form-label">Reasons offered for a return (separated by commas)</label>
          <input class="form-control ps-reasons" type="text" maxlength="400" [(ngModel)]="s.returnReasons" name="rs" />
        </div>
        <h4 style="font-size:1rem;" class="mb-2 mt-3">Discounts and prices at the till</h4>
        <div class="form-group mb-3">
          <label class="form-label">A cashier may give a discount up to this percent alone; above it a manager approves (0 = every discount needs a manager)</label>
          <input class="form-control ps-cashmax" type="number" min="0" max="100" step="0.5" style="width:160px;" [(ngModel)]="s.cashierDiscountMax" name="cdm" />
        </div>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <label><input class="ps-discreason" type="checkbox" [(ngModel)]="s.discountReasonRequired" name="drr" /> A discount needs a reason</label>
          <label><input class="ps-priceallow" type="checkbox" [(ngModel)]="s.priceOverrideAllowed" name="poa" /> A manager may change a price at the till</label>
          <label><input class="ps-belowcost" type="checkbox" [(ngModel)]="s.priceBelowCostBlocked" name="pbc" /> Refuse a price below the item's cost</label>
        </div>
        <div class="form-group mb-3">
          <label class="form-label">Reasons offered for a discount (separated by commas)</label>
          <input class="form-control ps-discreasons" type="text" maxlength="400" [(ngModel)]="s.discountReasons" name="dr" />
        </div>
        <h4 style="font-size:1rem;" class="mb-2 mt-3">Receipts</h4>
        <div class="form-group mb-3">
          <label class="form-label">Paper</label>
          <select class="form-select ps-paper" style="width:auto;" [(ngModel)]="s.receiptPaper" name="paper">
            <option value="80">80 mm thermal roll</option>
            <option value="58">58 mm thermal roll</option>
          </select>
        </div>
        <div class="form-group mb-3">
          <label class="form-label">Extra lines under the business details (opening hours, phone, VAT note...)</label>
          <textarea class="form-control ps-rheader" rows="2" maxlength="300" [ngModel]="s.receiptHeader" (ngModelChange)="s.receiptHeader = $event" name="rh"></textarea>
        </div>
        <div class="form-group mb-3">
          <label class="form-label">Text at the bottom of every receipt</label>
          <textarea class="form-control ps-rfooter" rows="2" maxlength="300" [(ngModel)]="s.receiptFooter" name="rf"></textarea>
        </div>
        <div class="flex gap-3 mb-3" style="flex-wrap:wrap;">
          <label><input class="ps-rlogo" type="checkbox" [(ngModel)]="s.receiptShowLogo" name="rl" /> Print the business logo</label>
          <label><input class="ps-rqr" type="checkbox" [(ngModel)]="s.receiptShowQr" name="rq" /> Print a QR code of the invoice number (sales sent to EFRIS keep their own QR code)</label>
        </div>
        <p class="text-muted" style="font-size:0.85rem;">The first printing of a receipt is the original; every later printing is marked <strong>COPY 1, COPY 2...</strong> and listed in Voids &amp; returns.</p>
        <div><button class="btn btn-primary ps-save" (click)="save()">Save</button></div>
      </div>
    </app-shell>
  `,
})
export class PosSettingsComponent implements OnInit {
  s: PosSettings | null = null;
  error: string | null = null;
  saved = false;

  constructor(private registerApi: RegisterApiService) {}

  ngOnInit(): void {
    this.registerApi.posSettings().subscribe({ next: (s) => (this.s = s), error: (err) => (this.error = err?.error?.message ?? 'Failed to load the settings') });
  }

  save(): void {
    if (!this.s) return;
    this.error = null;
    this.saved = false;
    this.registerApi.savePosSettings(this.s).subscribe({
      next: (s) => {
        this.s = s;
        this.saved = true;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Could not save'),
    });
  }
}
