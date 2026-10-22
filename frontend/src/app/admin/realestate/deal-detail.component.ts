import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ShellComponent } from '../../shared/shell.component';
import { ReDocumentsComponent } from './re-documents.component';
import { RePaymentsComponent, RePrintComponent } from './re-print.component';
import { AccountApiService } from '../../core/api/account-api.service';
import { Account } from '../../core/models';
import { AuthService } from '../../core/auth.service';
import {
  CommissionBasis, CommissionDto, DEAL_STAGE_LABEL, DealDto, MILESTONE_KIND_LABEL, MILESTONE_STATUS_LABEL, MilestoneDto, MilestoneKind, Option, PayeeKind, RealEstateApiService,
} from '../../core/api/realestate-api.service';

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v));
const today = (): string => new Date().toISOString().slice(0, 10);

/** Real Estate > Deals > one deal: the stages, the buyer's payment schedule (each payment becomes an invoice), and who earns what commission. */
@Component({
  selector: 'app-deal-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent, ReDocumentsComponent, RePrintComponent, RePaymentsComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!d && !error" class="text-muted">Loading…</div>
      <ng-container *ngIf="d">
        <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/realestate/deals" style="font-size:0.85rem;">← Deals</a>
            <h3>{{ d.code }} · {{ d.propertyCode }}
              <span class="badge" [class.badge-success]="d.stage === 'COMPLETED'" [class.badge-danger]="d.stage === 'CANCELLED'" [class.badge-warning]="d.stage === 'RESERVED' || d.stage === 'AGREEMENT'" [class.badge-secondary]="d.stage === 'OFFER'" style="font-size:0.8rem; vertical-align:middle;">{{ stageName(d.stage) }}</span></h3>
          </div>
          <div class="flex gap-2" *ngIf="d.canManage" style="flex-wrap:wrap;">
            <a class="btn btn-outline-secondary" *ngIf="live()" [routerLink]="['/admin/realestate/deals', d.id, 'edit']">Edit</a>
            <button class="btn btn-outline-danger" type="button" *ngIf="d.stage === 'OFFER' || d.stage === 'CANCELLED'" (click)="remove()">Delete</button>
          </div>
        </div>

        <app-re-print kind="deal" [id]="d.id" [stage]="d.stage"></app-re-print>
        <div class="steps mb-3">
          <span *ngFor="let s of steps" class="step" [class.done]="stepDone(s)" [class.now]="d.stage === s">{{ stageName(s) }}</span>
          <span class="step cancelled" *ngIf="d.stage === 'CANCELLED'">Cancelled</span>
        </div>
        <div class="alert alert-danger" *ngIf="actionError">{{ actionError }}</div>
        <div class="alert alert-warning" *ngIf="d.stage === 'CANCELLED' && d.cancelReason">Cancelled: {{ d.cancelReason }}</div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(22rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">The sale</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">Property</td><td><a [routerLink]="['/admin/realestate/properties', d.propertyId]">{{ d.propertyCode }} · {{ d.propertyTitle }}</a><div class="text-muted" style="font-size:0.8rem;">{{ d.propertyArea || '' }}</div></td></tr>
              <tr><td class="text-muted">Buyer</td><td>{{ d.buyerName }}<div class="text-muted" style="font-size:0.8rem;">{{ d.buyerPhone || '' }} {{ d.buyerEmail || '' }}</div></td></tr>
              <tr *ngIf="d.leadId"><td class="text-muted">Lead</td><td><a [routerLink]="['/admin/realestate/leads', d.leadId]">{{ d.leadName }}</a></td></tr>
              <tr *ngIf="d.buyerCustomerId"><td class="text-muted">Customer</td><td><a [routerLink]="['/admin/customers', d.buyerCustomerId]">Open the customer record</a></td></tr>
              <tr><td class="text-muted">Agent</td><td>{{ d.agentName || '—' }}</td></tr>
              <tr><td class="text-muted">Offer</td><td>{{ d.offerDate ? (d.offerDate | date: 'd MMM y') : '—' }}<span *ngIf="d.offerValidUntil"> · valid to {{ d.offerValidUntil | date: 'd MMM y' }}</span></td></tr>
              <tr *ngIf="d.reservationExpiry"><td class="text-muted">Reserved until</td><td>{{ d.reservationExpiry | date: 'd MMM y' }}</td></tr>
              <tr *ngIf="d.agreementDate"><td class="text-muted">Agreement signed</td><td>{{ d.agreementDate | date: 'd MMM y' }}</td></tr>
              <tr><td class="text-muted">Completion</td><td>{{ d.completedDate ? ('done ' + (d.completedDate | date: 'd MMM y')) : d.expectedCompletion ? ('expected ' + (d.expectedCompletion | date: 'd MMM y')) : '—' }}</td></tr>
              <tr *ngIf="d.notes"><td class="text-muted">Notes</td><td style="white-space:pre-wrap;">{{ d.notes }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">The money</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:11rem;">Asking price</td><td class="text-end">{{ d.listPrice != null ? (d.listPrice | number: '1.0-0') : '—' }}</td></tr>
              <tr><td class="text-muted">Offered</td><td class="text-end">{{ d.offerAmount | number: '1.0-0' }}</td></tr>
              <tr><td class="text-muted"><strong>Agreed price</strong></td><td class="text-end"><strong>{{ d.agreedPrice != null ? (d.agreedPrice | number: '1.0-0') : '—' }}</strong></td></tr>
              <ng-container *ngIf="d.agreedPrice != null">
                <tr><td class="text-muted">Scheduled in payments</td><td class="text-end" [style.color]="d.scheduled !== d.agreedPrice ? '#b45309' : ''">{{ d.scheduled | number: '1.0-0' }}</td></tr>
                <tr><td class="text-muted">Invoiced</td><td class="text-end">{{ d.invoiced | number: '1.0-0' }}</td></tr>
                <tr><td class="text-muted">Paid by the buyer</td><td class="text-end">{{ d.paid | number: '1.0-0' }}</td></tr>
                <tr><td class="text-muted">Still to pay</td><td class="text-end">{{ (d.outstanding ?? 0) | number: '1.0-0' }}</td></tr>
              </ng-container>
              <tr><td class="text-muted">Commission</td><td class="text-end">{{ d.commissionTotal | number: '1.0-0' }}<span class="text-muted" *ngIf="d.commissionPercent && !d.commissionFixed"> ({{ d.commissionPercent }}%)</span></td></tr>
            </tbody></table>
          </div>
        </div>

        <!-- what to do next -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="d.canManage && live()">
          <h4 class="mb-2">Next step</h4>
          <ng-container *ngIf="d.stage === 'OFFER'">
            <p class="text-muted mb-2" style="font-size:0.9rem;">When the seller accepts, reserve the property for this buyer. It is taken off the market and a deposit and balance are put in the payment schedule.</p>
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">Agreed price</label><input class="form-control" style="width:11rem;" type="number" min="0" name="rp" [(ngModel)]="rAgreed" /></div>
              <div><label class="form-label">Deposit</label><input class="form-control" style="width:10rem;" type="number" min="0" name="rd" [(ngModel)]="rDeposit" /></div>
              <div><label class="form-label">Reserved until</label><input class="form-control" type="date" name="rx" [(ngModel)]="rExpiry" /></div>
              <div><label class="form-label">Completion expected</label><input class="form-control" type="date" name="rc" [(ngModel)]="rCompletion" /></div>
              <button class="btn btn-primary" type="button" (click)="reserve()" [disabled]="busy">Accept and reserve</button>
            </div>
          </ng-container>
          <ng-container *ngIf="d.stage === 'RESERVED'">
            <p class="text-muted mb-2" style="font-size:0.9rem;">Once the sale agreement is signed, record it. The payment schedule must add up to the agreed price first.</p>
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">Signed on</label><input class="form-control" type="date" name="ad" [(ngModel)]="agreementDate" /></div>
              <button class="btn btn-primary" type="button" (click)="agreement()" [disabled]="busy">Record the agreement</button>
            </div>
          </ng-container>
          <ng-container *ngIf="d.stage === 'RESERVED' || d.stage === 'AGREEMENT'">
            <div class="flex gap-2 mt-3" style="flex-wrap:wrap; align-items:flex-end;" *ngIf="d.stage === 'AGREEMENT'">
              <div><label class="form-label">Completed on</label><input class="form-control" type="date" name="cd" [(ngModel)]="completedDate" /></div>
              <button class="btn btn-success" type="button" (click)="complete(false)" [disabled]="busy">Complete the sale</button>
              <label *ngIf="isAdmin" style="display:flex; gap:0.4rem; align-items:center;" title="Only an administrator can do this"><input type="checkbox" name="fc" [(ngModel)]="forceComplete" /> Even though it is not fully paid</label>
              <button class="btn btn-outline-danger" type="button" *ngIf="forceComplete && isAdmin" (click)="complete(true)" [disabled]="busy">Complete anyway</button>
            </div>
            <div class="text-muted mt-2" style="font-size:0.85rem;" *ngIf="d.stage === 'AGREEMENT'">Completing needs every payment invoiced and paid. The property is then marked sold and the commission becomes due.</div>
          </ng-container>
          <div class="mt-3">
            <button class="btn btn-sm btn-outline-danger" type="button" (click)="cancelOpen = !cancelOpen">Cancel this deal…</button>
            <div class="flex gap-2 mt-2" *ngIf="cancelOpen">
              <input class="form-control" style="max-width:26rem;" name="cr" [(ngModel)]="cancelReason" maxlength="300" placeholder="Why? (e.g. buyer could not raise the money)" />
              <button class="btn btn-sm btn-danger" type="button" (click)="cancel()" [disabled]="busy">Cancel the deal</button>
            </div>
            <div class="text-muted" style="font-size:0.8rem;" *ngIf="cancelOpen">Unpaid invoices of this deal are voided or deleted. If the buyer has already paid, cancel the invoice and refund first, in Invoices.</div>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="d.agreedPrice != null">
          <app-re-payments kind="deals" [id]="d.id" [refresh]="d.paid"></app-re-payments>
        </div>
        <!-- payment schedule -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="d.agreedPrice != null">
          <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
            <h4>Buyer's payments</h4>
            <div class="flex gap-2" *ngIf="d.canManage && scheduleOpen()">
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="planOpen = !planOpen; addOpen = false">Make a plan…</button>
              <button class="btn btn-sm btn-outline-primary" type="button" (click)="addOpen = !addOpen; planOpen = false">+ Add a payment</button>
            </div>
          </div>
          <div class="card mb-2" style="padding:0.75rem;" *ngIf="planOpen">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">Deposit (% of what is left)</label><input class="form-control" style="width:9rem;" type="number" min="0" max="100" name="pd" [(ngModel)]="plan.depositPercent" /></div>
              <div><label class="form-label">Instalments</label><input class="form-control" style="width:7rem;" type="number" min="1" max="120" name="pn" [(ngModel)]="plan.instalments" /></div>
              <div><label class="form-label">First due</label><input class="form-control" type="date" name="pf" [(ngModel)]="plan.firstDue" /></div>
              <div><label class="form-label">Every (months)</label><input class="form-control" style="width:7rem;" type="number" min="1" max="24" name="pe" [(ngModel)]="plan.everyMonths" /></div>
              <button class="btn btn-primary" type="button" (click)="makePlan()" [disabled]="busy">Use this plan</button>
            </div>
            <div class="text-muted mt-1" style="font-size:0.85rem;">This replaces the payments that are not invoiced yet and spreads what is left of the price over the instalments.</div>
          </div>
          <div class="card mb-2" style="padding:0.75rem;" *ngIf="addOpen">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">What for</label><input class="form-control" style="width:14rem;" name="ml" [(ngModel)]="mNew.label" maxlength="120" placeholder="e.g. Second instalment" /></div>
              <div><label class="form-label">Kind</label><select class="form-select" name="mk" [(ngModel)]="mNew.kind"><option *ngFor="let k of kinds" [value]="k.value">{{ k.label }}</option></select></div>
              <div><label class="form-label">Amount</label><input class="form-control" style="width:11rem;" type="number" min="0" name="ma" [(ngModel)]="mNew.amount" /></div>
              <div><label class="form-label">Due</label><input class="form-control" type="date" name="md" [(ngModel)]="mNew.dueDate" /></div>
              <button class="btn btn-primary" type="button" (click)="addMilestone()" [disabled]="busy">Add</button>
            </div>
            <div class="text-muted mt-1" style="font-size:0.85rem;">{{ (d.agreedPrice - d.scheduled) | number: '1.0-0' }} of the price is not scheduled yet.</div>
          </div>
          <table class="stack table-sm" *ngIf="d.milestones.length; else nopay">
            <thead><tr><th>Payment</th><th>Due</th><th class="text-end">Amount</th><th>Invoice</th><th class="text-end">Paid</th><th></th></tr></thead>
            <tbody>
              <ng-container *ngFor="let m of d.milestones">
                <tr>
                  <td data-label="Payment">{{ m.label }}<div class="text-muted" style="font-size:0.75rem;">{{ kindName(m.kind) }}</div></td>
                  <td [style.color]="m.overdue ? '#b91c1c' : ''" data-label="Due">{{ m.dueDate ? (m.dueDate | date: 'd MMM y') : '—' }}<span *ngIf="m.overdue"> (late)</span></td>
                  <td class="text-end" data-label="Amount">{{ m.amount | number: '1.0-0' }}</td>
                  <td data-label="Invoice"><span class="badge" [class.badge-success]="m.status === 'PAID'" [class.badge-warning]="m.status === 'ISSUED' || m.status === 'DRAFT'" [class.badge-secondary]="m.status === 'NOT_INVOICED' || m.status === 'VOID'">{{ statusName(m.status) }}</span>
                    <a *ngIf="m.invoiceId" [routerLink]="['/admin/invoices', m.invoiceId]" style="margin-left:0.4rem;">{{ m.invoiceNumber }}</a></td>
                  <td class="text-end" data-label="Paid">{{ m.paid | number: '1.0-0' }}</td>
                  <td style="white-space:nowrap; text-align:right;" data-label="">
                    <ng-container *ngIf="d.canManage && scheduleOpen()">
                      <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="m.status === 'NOT_INVOICED' || m.status === 'VOID'" (click)="invoice(m, false)" [disabled]="busy">Invoice</button>
                      <button class="btn btn-sm btn-primary" type="button" style="margin-left:0.2rem;" *ngIf="m.status === 'NOT_INVOICED' || m.status === 'VOID'" (click)="invoice(m, true)" [disabled]="busy" title="Create the invoice and issue it to the buyer">Invoice and issue</button>
                      <button class="btn btn-sm btn-primary" type="button" *ngIf="m.status === 'DRAFT'" (click)="issue(m)" [disabled]="busy">Issue</button>
                      <a class="btn btn-sm btn-outline-secondary" style="margin-left:0.2rem;" *ngIf="m.status === 'ISSUED' && m.invoiceId" [routerLink]="['/admin/invoices', m.invoiceId, 'record-payment']">Record payment</a>
                      <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="editM(m)">Edit</button>
                      <button class="btn btn-sm btn-outline-danger" type="button" style="margin-left:0.2rem;" (click)="removeM(m)" [disabled]="busy">✕</button>
                    </ng-container>
                  </td>
                </tr>
                <tr *ngIf="mEditId === m.id">
                  <td colspan="6">
                    <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
                      <div><label class="form-label">What for</label><input class="form-control" style="width:14rem;" name="el" [(ngModel)]="mEdit.label" maxlength="120" [disabled]="m.status !== 'NOT_INVOICED' && m.status !== 'VOID'" /></div>
                      <div><label class="form-label">Amount</label><input class="form-control" style="width:11rem;" type="number" min="0" name="ea" [(ngModel)]="mEdit.amount" [disabled]="m.status !== 'NOT_INVOICED' && m.status !== 'VOID'" /></div>
                      <div><label class="form-label">Due</label><input class="form-control" type="date" name="ed" [(ngModel)]="mEdit.dueDate" /></div>
                      <button class="btn btn-sm btn-primary" type="button" (click)="saveM(m)" [disabled]="busy">Save</button>
                      <button class="btn btn-sm btn-outline-secondary" type="button" (click)="mEditId = null">Cancel</button>
                    </div>
                    <div class="text-muted" style="font-size:0.8rem;" *ngIf="m.status !== 'NOT_INVOICED' && m.status !== 'VOID'">This payment is invoiced, so only its due date can change.</div>
                  </td>
                </tr>
              </ng-container>
            </tbody>
          </table>
          <ng-template #nopay><div class="text-muted">No payments scheduled yet.</div></ng-template>
          <div class="text-muted mt-2" style="font-size:0.85rem;" *ngIf="d.milestones.length">Payments are recorded on the invoices (Record payment); this table follows them. The invoices go to the buyer as a customer.</div>
        </div>

        <!-- commission -->
        <div class="card mb-3" style="padding:1rem;" *ngIf="d.stage !== 'CANCELLED' || d.commissions.length">
          <div class="flex justify-between align-center mb-2" style="flex-wrap:wrap; gap:0.5rem;">
            <h4>Commission</h4>
            <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="d.canManage && d.stage !== 'CANCELLED'" (click)="openCommission()">+ Add a share</button>
          </div>
          <p class="text-muted mb-2" style="font-size:0.9rem;">
            The agency earns <strong>{{ d.commissionTotal | number: '1.0-0' }}</strong> on this sale<span *ngIf="d.commissionPercent && !d.commissionFixed"> ({{ d.commissionPercent }}% of the price)</span>.
            Shared out: {{ d.commissionAllocated | number: '1.0-0' }} · kept by the agency: {{ d.commissionKept | number: '1.0-0' }}.
            <span *ngIf="d.stage !== 'COMPLETED'">Shares can be posted to the accounts once the sale is complete.</span>
          </p>
          <div class="card mb-2" style="padding:0.75rem;" *ngIf="cOpen">
            <div class="flex gap-2" style="flex-wrap:wrap; align-items:flex-end;">
              <div><label class="form-label">Role</label><input class="form-control" style="width:12rem;" name="cr1" [(ngModel)]="c.role" maxlength="60" list="re-roles" placeholder="Selling agent" />
                <datalist id="re-roles"><option value="Selling agent"></option><option value="Listing agent"></option><option value="Referral"></option><option value="Co-broker"></option><option value="Team leader"></option></datalist></div>
              <div><label class="form-label">Paid to</label><select class="form-select" name="ck" [(ngModel)]="c.payeeKind"><option value="STAFF">Staff member</option><option value="EXTERNAL">Someone outside</option></select></div>
              <div *ngIf="c.payeeKind === 'STAFF'"><label class="form-label">Staff member</label><select class="form-select" name="cu" [(ngModel)]="c.payeeUserId"><option [ngValue]="null">— choose —</option><option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option></select></div>
              <div *ngIf="c.payeeKind === 'EXTERNAL'"><label class="form-label">Name</label><input class="form-control" style="width:14rem;" name="cn" [(ngModel)]="c.payeeName" maxlength="200" placeholder="Person or agency" /></div>
              <div><label class="form-label">As</label><select class="form-select" name="cb" [(ngModel)]="c.basis"><option value="SHARE_PERCENT">% of the commission</option><option value="FIXED">A fixed amount</option></select></div>
              <div *ngIf="c.basis === 'SHARE_PERCENT'"><label class="form-label">Share %</label><input class="form-control" style="width:8rem;" type="number" min="0" max="100" step="0.01" name="cs" [(ngModel)]="c.sharePercent" /></div>
              <div *ngIf="c.basis === 'FIXED'"><label class="form-label">Amount</label><input class="form-control" style="width:11rem;" type="number" min="0" name="cf" [(ngModel)]="c.fixedAmount" /></div>
              <div><label class="form-label">Note</label><input class="form-control" style="width:12rem;" name="cno" [(ngModel)]="c.note" maxlength="300" /></div>
              <button class="btn btn-primary" type="button" (click)="saveCommission()" [disabled]="busy">{{ cEditId ? 'Save' : 'Add' }}</button>
              <button class="btn btn-outline-secondary" type="button" (click)="cOpen = false">Cancel</button>
            </div>
          </div>
          <table class="stack table-sm" *ngIf="d.commissions.length; else nocom">
            <thead><tr><th>Role</th><th>Paid to</th><th>Basis</th><th class="text-end">Amount</th><th>State</th><th></th></tr></thead>
            <tbody>
              <tr *ngFor="let x of d.commissions" [style.opacity]="x.status === 'CANCELLED' ? 0.5 : 1">
                <td data-label="Role">{{ x.role }}<div class="text-muted" style="font-size:0.75rem;" *ngIf="x.note">{{ x.note }}</div></td>
                <td data-label="Paid to">{{ x.payeeName }}<div class="text-muted" style="font-size:0.75rem;">{{ x.payeeKind === 'STAFF' ? 'staff' : 'outside' }}</div></td>
                <td data-label="Basis">{{ x.basis === 'SHARE_PERCENT' ? (x.sharePercent + '% of the commission') : 'fixed' }}</td>
                <td class="text-end" data-label="Amount">{{ x.amount | number: '1.0-0' }}</td>
                <td data-label="State">
                  <span class="badge badge-secondary" *ngIf="x.status === 'PLANNED' && !x.due">Planned</span>
                  <span class="badge badge-warning" *ngIf="x.status === 'PLANNED' && x.due">To post</span>
                  <span class="badge badge-warning" *ngIf="x.status === 'POSTED' && !x.expensePaid">Posted: {{ expenseName(x) }}</span>
                  <span class="badge badge-success" *ngIf="x.status === 'POSTED' && x.expensePaid">Paid</span>
                  <span class="badge badge-secondary" *ngIf="x.status === 'CANCELLED'">Cancelled</span>
                </td>
                <td style="white-space:nowrap; text-align:right;" *ngIf="d.canManage" data-label="">
                  <ng-container *ngIf="x.status === 'PLANNED'">
                    <button class="btn btn-sm btn-primary" type="button" *ngIf="d.stage === 'COMPLETED'" (click)="post(x)" [disabled]="busy">Post to accounts</button>
                    <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="editC(x)">Edit</button>
                    <button class="btn btn-sm btn-outline-danger" type="button" style="margin-left:0.2rem;" (click)="removeC(x)" [disabled]="busy">✕</button>
                  </ng-container>
                  <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="x.status === 'POSTED' && (x.expenseStatus === 'REJECTED' || x.expenseStatus === 'VOIDED' || x.expenseStatus === 'MISSING')" (click)="unpost(x)" [disabled]="busy" title="The expense was rejected or voided: take the share back so it can be posted again">Take back</button>
                </td>
              </tr>
            </tbody>
          </table>
          <ng-template #nocom><div class="text-muted">No shares yet. Add the selling agent, the listing agent, a referrer or another agency.</div></ng-template>
          <div class="flex gap-2 mt-2 align-center" *ngIf="d.stage === 'COMPLETED' && d.canManage && accounts.length" style="flex-wrap:wrap;">
            <label class="text-muted" style="font-size:0.85rem;">Expense account for posting</label>
            <select class="form-select" style="width:auto;" name="pa" [(ngModel)]="postAccountId"><option [ngValue]="null">The one in the settings</option><option *ngFor="let a of accounts" [ngValue]="a.id">{{ a.code }} · {{ a.name }}</option></select>
          </div>
          <div class="text-muted mt-1" style="font-size:0.85rem;" *ngIf="d.stage === 'COMPLETED'">Posting logs an expense that waits for approval in Expenses; approve it there, and settle it when the commission is paid out.</div>

          <!-- the agency's own commission -->
          <div *ngIf="d.stage === 'COMPLETED' && d.commissionTotal > 0" class="mt-3" style="border-top:1px solid rgba(127,127,127,0.3); padding-top:0.75rem;">
            <h5 class="mb-1">The agency's commission from the seller</h5>
            <ng-container *ngIf="d.feeInvoice; else nofee">
              <div>Invoice <a [routerLink]="['/admin/invoices', d.feeInvoice.invoiceId]">{{ d.feeInvoice.invoiceNumber }}</a> for {{ d.feeInvoice.total | number: '1.0-0' }} to {{ d.sellerName }}:
                <span class="badge" [class.badge-success]="d.feeInvoice.status === 'PAID'" [class.badge-warning]="d.feeInvoice.status !== 'PAID'">{{ statusName(d.feeInvoice.status) }}</span></div>
            </ng-container>
            <ng-template #nofee>
              <div class="text-muted mb-1" style="font-size:0.9rem;">When the seller pays the agency (rather than the buyer paying the full price to you), invoice the commission to the seller.</div>
              <div class="flex gap-2" style="flex-wrap:wrap;" *ngIf="d.canManage">
                <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="!d.sellerCustomerId" (click)="seller()" [disabled]="busy" title="Use the owner entered on the property as the seller (made a customer if needed)">Use the property's owner as the seller</button>
                <span *ngIf="d.sellerCustomerId">Seller: <a [routerLink]="['/admin/customers', d.sellerCustomerId]">{{ d.sellerName }}</a></span>
                <button class="btn btn-sm btn-outline-primary" type="button" *ngIf="d.sellerCustomerId" (click)="fee(false)" [disabled]="busy">Draft the invoice</button>
                <button class="btn btn-sm btn-primary" type="button" *ngIf="d.sellerCustomerId" (click)="fee(true)" [disabled]="busy">Invoice and issue</button>
              </div>
            </ng-template>
          </div>
        </div>
        <div class="card mb-3" style="padding:1rem;">
          <app-re-documents subjectType="DEAL" [subjectId]="d.id" [canManage]="d.canManage" defaultCategory="SALE_AGREEMENT"></app-re-documents>
        </div>
      </ng-container>
    </app-shell>
  `,
  styles: [`
    .steps { display: flex; gap: 0.35rem; flex-wrap: wrap; }
    .step { padding: 0.25rem 0.7rem; border-radius: 1rem; font-size: 0.8rem; background: rgba(127, 127, 127, 0.15); }
    .step.done { background: #0f766e; color: #fff; }
    .step.now { background: #2563eb; color: #fff; font-weight: 600; }
    .step.cancelled { background: #b91c1c; color: #fff; }
  `],
})
export class DealDetailComponent implements OnInit {
  d: DealDto | null = null;
  error: string | null = null;
  actionError: string | null = null;
  busy = false;
  isAdmin = false;
  agents: Option[] = [];
  accounts: Account[] = [];
  postAccountId: number | null = null;
  steps: ('OFFER' | 'RESERVED' | 'AGREEMENT' | 'COMPLETED')[] = ['OFFER', 'RESERVED', 'AGREEMENT', 'COMPLETED'];
  kinds = (Object.keys(MILESTONE_KIND_LABEL) as MilestoneKind[]).map((k) => ({ value: k, label: MILESTONE_KIND_LABEL[k] }));

  rAgreed: number | null = null;
  rDeposit: number | null = null;
  rExpiry = '';
  rCompletion = '';
  agreementDate = today();
  completedDate = today();
  forceComplete = false;
  cancelOpen = false;
  cancelReason = '';

  planOpen = false;
  addOpen = false;
  plan = { depositPercent: 10 as number | null, instalments: 3 as number | null, firstDue: '', everyMonths: 1 as number | null };
  mNew = { label: '', kind: 'INSTALMENT' as MilestoneKind, amount: null as number | null, dueDate: '' };
  mEditId: number | null = null;
  mEdit = { label: '', amount: null as number | null, dueDate: '' };

  cOpen = false;
  cEditId: number | null = null;
  c = { role: '', payeeKind: 'STAFF' as PayeeKind, payeeUserId: null as number | null, payeeName: '', basis: 'SHARE_PERCENT' as CommissionBasis, sharePercent: null as number | null, fixedAmount: null as number | null, note: '' };

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router, private accountApi: AccountApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.isAdmin = this.auth.isAdmin();
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
    this.accountApi.listActiveByType('EXPENSE').subscribe({ next: (a) => (this.accounts = a), error: () => (this.accounts = []) });
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.deal(id).subscribe({
      next: (d) => this.show(d),
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the deal'),
    });
  }

  private show(d: DealDto): void {
    this.d = d;
    if (this.rAgreed == null) this.rAgreed = d.offerAmount;
    if (!this.rCompletion && d.expectedCompletion) this.rCompletion = d.expectedCompletion;
  }

  /** Runs one action: shows the new state, or the reason it was refused. */
  private run(call: Observable<DealDto>, after?: () => void): void {
    this.busy = true;
    this.actionError = null;
    call.subscribe({
      next: (d) => { this.busy = false; this.show(d); after?.(); },
      error: (e) => { this.busy = false; this.actionError = e?.error?.message ?? 'That did not work'; },
    });
  }

  live(): boolean { return !!this.d && (this.d.stage === 'OFFER' || this.d.stage === 'RESERVED' || this.d.stage === 'AGREEMENT'); }
  scheduleOpen(): boolean { return !!this.d && (this.d.stage === 'RESERVED' || this.d.stage === 'AGREEMENT'); }
  stageName(s: string): string { return (DEAL_STAGE_LABEL as Record<string, string>)[s] ?? s; }
  kindName(s: string): string { return (MILESTONE_KIND_LABEL as Record<string, string>)[s] ?? s; }
  statusName(s: string): string { return MILESTONE_STATUS_LABEL[s] ?? s; }
  expenseName(x: CommissionDto): string { return x.expenseStatus === 'PENDING' ? 'awaiting approval' : x.expenseStatus === 'APPROVED' ? 'approved, unpaid' : (x.expenseStatus ?? '').toLowerCase(); }

  stepDone(s: string): boolean {
    if (!this.d || this.d.stage === 'CANCELLED') return false;
    return this.steps.indexOf(s as never) < this.steps.indexOf(this.d.stage as never) || this.d.stage === 'COMPLETED';
  }

  // ---- stages
  reserve(): void {
    if (!this.d) return;
    this.run(this.api.reserveDeal(this.d.id, { agreedPrice: num(this.rAgreed), deposit: num(this.rDeposit), reservationExpiry: this.rExpiry || null, expectedCompletion: this.rCompletion || null }));
  }
  agreement(): void { if (this.d) this.run(this.api.dealAgreement(this.d.id, { agreementDate: this.agreementDate || null, expectedCompletion: null })); }
  complete(force: boolean): void {
    if (!this.d) return;
    if (force && !confirm('Complete this sale although the buyer has not paid everything? The property will be marked sold.')) return;
    this.run(this.api.completeDeal(this.d.id, force, this.completedDate || null));
  }
  cancel(): void {
    if (!this.d) return;
    if (!this.cancelReason.trim()) { this.actionError = 'Say why the deal is cancelled.'; return; }
    this.run(this.api.cancelDeal(this.d.id, this.cancelReason.trim()), () => { this.cancelOpen = false; this.cancelReason = ''; });
  }
  remove(): void {
    if (!this.d || !confirm('Delete this deal? This cannot be undone.')) return;
    this.api.deleteDeal(this.d.id).subscribe({ next: () => this.router.navigate(['/admin/realestate/deals']), error: (e) => (this.actionError = e?.error?.message ?? 'Could not delete the deal') });
  }

  // ---- payments
  makePlan(): void {
    if (!this.d) return;
    this.run(this.api.dealPlan(this.d.id, { depositPercent: num(this.plan.depositPercent) ?? 0, instalments: num(this.plan.instalments) ?? 1, firstDue: this.plan.firstDue || null, everyMonths: num(this.plan.everyMonths) ?? 1 }),
      () => (this.planOpen = false));
  }
  addMilestone(): void {
    if (!this.d) return;
    const amount = num(this.mNew.amount);
    if (!this.mNew.label.trim() || !amount) { this.actionError = 'Name the payment and enter the amount.'; return; }
    this.run(this.api.addMilestone(this.d.id, { kind: this.mNew.kind, label: this.mNew.label.trim(), amount, dueDate: this.mNew.dueDate || null }),
      () => { this.mNew = { label: '', kind: 'INSTALMENT', amount: null, dueDate: '' }; this.addOpen = false; });
  }
  editM(m: MilestoneDto): void { this.mEditId = this.mEditId === m.id ? null : m.id; this.mEdit = { label: m.label, amount: m.amount, dueDate: m.dueDate ?? '' }; }
  saveM(m: MilestoneDto): void {
    if (!this.d) return;
    this.run(this.api.updateMilestone(this.d.id, m.id, { label: this.mEdit.label, amount: num(this.mEdit.amount) ?? undefined, dueDate: this.mEdit.dueDate || null }), () => (this.mEditId = null));
  }
  removeM(m: MilestoneDto): void {
    if (!this.d || !confirm('Remove the payment "' + m.label + '"?')) return;
    this.run(this.api.deleteMilestone(this.d.id, m.id));
  }
  invoice(m: MilestoneDto, issue: boolean): void { if (this.d) this.run(this.api.invoiceMilestone(this.d.id, m.id, issue)); }
  issue(m: MilestoneDto): void { if (this.d) this.run(this.api.issueMilestone(this.d.id, m.id)); }

  // ---- commission
  openCommission(): void {
    this.cEditId = null;
    this.c = { role: this.d?.commissions.length ? '' : 'Selling agent', payeeKind: 'STAFF', payeeUserId: this.d?.commissions.length ? null : this.d?.agentUserId ?? null, payeeName: '', basis: 'SHARE_PERCENT', sharePercent: null, fixedAmount: null, note: '' };
    this.cOpen = true;
  }
  editC(x: CommissionDto): void {
    this.cEditId = x.id;
    this.c = { role: x.role, payeeKind: x.payeeKind, payeeUserId: x.payeeUserId, payeeName: x.payeeKind === 'EXTERNAL' ? x.payeeName : '', basis: x.basis, sharePercent: x.sharePercent, fixedAmount: x.fixedAmount, note: x.note ?? '' };
    this.cOpen = true;
  }
  saveCommission(): void {
    if (!this.d) return;
    const c = this.c;
    const body = { role: c.role.trim(), payeeKind: c.payeeKind, payeeUserId: c.payeeKind === 'STAFF' ? c.payeeUserId : null, payeeName: c.payeeKind === 'EXTERNAL' ? c.payeeName.trim() : null, basis: c.basis,
      sharePercent: c.basis === 'SHARE_PERCENT' ? num(c.sharePercent) : null, fixedAmount: c.basis === 'FIXED' ? num(c.fixedAmount) : null, note: c.note };
    const call = this.cEditId ? this.api.updateCommission(this.d.id, this.cEditId, body) : this.api.addCommission(this.d.id, body);
    this.run(call, () => (this.cOpen = false));
  }
  removeC(x: CommissionDto): void {
    if (!this.d || !confirm('Remove the share for ' + x.payeeName + '?')) return;
    this.run(this.api.deleteCommission(this.d.id, x.id));
  }
  post(x: CommissionDto): void { if (this.d) this.run(this.api.postCommission(this.d.id, x.id, this.postAccountId)); }
  unpost(x: CommissionDto): void { if (this.d) this.run(this.api.unpostCommission(this.d.id, x.id)); }
  seller(): void { if (this.d) this.run(this.api.dealSeller(this.d.id)); }
  fee(issue: boolean): void { if (this.d) this.run(this.api.dealFeeInvoice(this.d.id, issue)); }
}
