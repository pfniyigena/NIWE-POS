import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { BillingType, LawApiService } from '../../core/api/law-api.service';
import { LawCostRatesComponent } from './law-cost-rates.component';
import { EscalationItem, EscalationResult, ReminderItem, RemindResult } from '../../core/api/law-api.service';

/** Law Firm > Settings (admin): practice areas, jurisdictions, reminders and the defaults for a new matter. */
@Component({
  selector: 'app-law-settings',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent, LawCostRatesComponent],
  template: `
    <app-shell>
      <h3 class="mb-3">{{ 'Law firm settings' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="saved">{{ 'Saved.' | translate }}</div>
      <form class="card" style="padding:1rem; max-width:40rem;" (ngSubmit)="save()">
        <div class="form-group mb-2"><label class="form-label">{{ 'Practice areas' | translate }} <span class="text-muted">{{ '(one per line)' | translate }}</span></label>
          <textarea class="form-control" rows="8" name="pa" [(ngModel)]="practiceAreas"></textarea></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Jurisdictions' | translate }} <span class="text-muted">{{ '(one per line)' | translate }}</span></label>
          <textarea class="form-control" rows="3" name="ju" [(ngModel)]="jurisdictions"></textarea></div>
        <div class="flex gap-2">
          <div class="form-group mb-2"><label class="form-label">{{ '"Due soon" means within (days)' | translate }}</label><input class="form-control" type="number" min="1" max="60" name="rd" [(ngModel)]="reminderDays" /></div>
          <div class="form-group mb-2"><label class="form-label">{{ 'New matters are billed' | translate }}</label>
            <select class="form-select" name="bt" [(ngModel)]="billingType">
              <option value="HOURLY">{{ 'Hourly' | translate }}</option><option value="FIXED">{{ 'Fixed fee' | translate }}</option><option value="RETAINER">{{ 'Retainer' | translate }}</option><option value="CONTINGENCY">{{ 'Contingency' | translate }}</option><option value="PRO_BONO">{{ 'Pro bono / legal aid' | translate }}</option>
            </select></div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="dg" [(ngModel)]="digest" /> {{ 'E-mail each lawyer a daily list of deadlines and tasks that are due soon or overdue' | translate }}</label>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-3"><input type="checkbox" name="rs" [(ngModel)]="restricted" /> {{ 'New matters are restricted by default (only their team and admins can open them)' | translate }}</label>
        <h4 class="mb-1">{{ 'Limitation date warnings' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="ee" [(ngModel)]="escalationEnabled" /> {{ 'E-mail the lead lawyer when a limitation date is close' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Missing a limitation date can lose the case. The first warning goes out this many days before the date, a second one 3 days before, and a last one if the date has passed and the deadline is still open. The person the deadline is assigned to gets the same e-mail. Mark the deadline as done to stop them. E-mail must be set up in Settings.' | translate }}</div>
        <div class="form-group mb-3" *ngIf="escalationEnabled" style="max-width:16rem;"><label class="form-label">{{ 'First warning (days before, 4 to 90)' | translate }}</label><input class="form-control" type="number" min="4" max="90" name="ed" [(ngModel)]="escalationDays" /></div>
        <h4 class="mb-1">{{ 'Unpaid fees' | translate }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group mb-2"><label class="form-label">{{ 'Usual payment term (days, 0 to 365)' | translate }}</label><input class="form-control" type="number" min="0" max="365" name="dt" [(ngModel)]="defaultTermDays" /></div>
        </div>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'An invoice without a due date is treated as late this many days after its date.' | translate }}</div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="ce" [(ngModel)]="collectionsEnabled" /> {{ 'E-mail clients by themselves when a fee is late' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'The daily run at about 06:35 mails each client one reminder listing the invoices that are late, with their statement of fees attached. A client without an e-mail address is skipped. E-mail must be set up in Settings.' | translate }}</div>
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="collectionsEnabled">
          <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'First reminder (days late, 1 to 90)' | translate }}</label><input class="form-control" type="number" min="1" max="90" name="cf" [(ngModel)]="collectionsFirstDays" /></div>
          <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'Repeat after (days, 3 to 90)' | translate }}</label><input class="form-control" type="number" min="3" max="90" name="cr" [(ngModel)]="collectionsRepeatDays" /></div>
          <div class="form-group" style="min-width:10rem;"><label class="form-label">{{ 'At most (reminders, 1 to 10)' | translate }}</label><input class="form-control" type="number" min="1" max="10" name="cm" [(ngModel)]="collectionsMax" /></div>
        </div>
        <h4 class="mb-1">{{ 'Client money (trust account)' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="te" [(ngModel)]="trustEnabled" /> {{ 'The firm holds money for clients in a trust account and keeps a ledger of it' | translate }}</label>
        <div class="text-muted mb-2" style="font-size:0.85rem;">{{ 'Money received for a client is kept apart from the firm\\'s own money and posted to its own accounts. Payouts and fee transfers need a second person to approve them. It cannot be switched off while client money is still held. Check the rules that apply to your firm with your accountant.' | translate }}</div>
        <div class="flex gap-2 mb-3" style="flex-wrap:wrap;" *ngIf="trustEnabled">
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Bank' | translate }}</label><input class="form-control" name="tb" [(ngModel)]="trustBank" /></div>
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">{{ 'Account number' | translate }}</label><input class="form-control" name="tn" [(ngModel)]="trustNumber" /></div>
        </div>
        <h4 class="mb-1">{{ 'Client portal' | translate }}</h4>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="pe" [(ngModel)]="portalEnabled" /> {{ 'Clients can sign in to a portal to follow their matters' | translate }}</label>
        <div class="text-muted mb-3" style="font-size:0.85rem;">{{ 'Each matter still has to be opened to the portal on its own Client portal tab, and you choose what the client sees there. Switching this off stops every client from signing in at once. Clients sign in at' | translate }} <strong>{{ '/portal' | translate }}</strong> {{ 'on this site.' | translate }}</div>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? ('Saving…' | translate) : ('Save settings' | translate) }}</button></div>
      </form>
      <div class="card mb-3 escalation-box" style="padding:1rem; max-width:40rem;">
        <strong>{{ 'Limitation warnings waiting to go out' | translate }}</strong>
        <div class="text-muted" style="font-size:0.85rem;">{{ 'This uses the saved settings. The daily run at about 06:25 sends them by itself when the box above is ticked; here you can look at them and send them now.' | translate }}</div>
        <div class="mt-2" style="display:flex; gap:0.5rem; flex-wrap:wrap;">
          <button type="button" class="btn btn-sm btn-outline-secondary esc-preview" (click)="previewEscalations()" [disabled]="escBusy">{{ 'See what would be sent' | translate }}</button>
          <button type="button" class="btn btn-sm btn-primary esc-send" (click)="sendEscalations()" [disabled]="escBusy || !escItems?.length">{{ 'Send now' | translate }}</button>
        </div>
        <div class="alert alert-danger mt-2" *ngIf="escError">{{ escError }}</div>
        <div class="alert alert-success mt-2 esc-result" *ngIf="escResult">{{ 'Sent {p0} e-mail{p1} for {p2} deadline{p3}' | translate: { p0: (escResult.mailsSent), p1: (escResult.mailsSent === 1 ? '' : 's'), p2: (escResult.deadlines), p3: (escResult.deadlines === 1 ? '' : 's') } }}<span *ngIf="escResult.skipped"> {{ '({p0} could not be sent)' | translate: { p0: (escResult.skipped) } }}</span>.</div>
        <p class="text-muted mt-2 mb-0" *ngIf="escItems && !escItems.length">{{ 'Nothing is waiting.' | translate }}</p>
        <table class="stack table-sm mt-2" *ngIf="escItems?.length">
          <thead><tr><th>{{ 'Date' | translate }}</th><th>{{ 'Step' | translate }}</th><th>{{ 'Deadline' | translate }}</th><th>{{ 'Matter' | translate }}</th><th>{{ 'Goes to' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let x of escItems">
              <td data-label="Date">{{ x.dueDate }} <span class="text-muted" style="font-size:0.8rem;">({{ x.daysLeft < 0 ? (0 - x.daysLeft) + ' d late' : x.daysLeft + ' d' }})</span></td>
              <td data-label="Step">{{ x.stageLabel }}</td>
              <td data-label="Deadline">{{ x.title }}</td>
              <td data-label="Matter">{{ x.matterNumber }} {{ x.matterTitle }}</td>
              <td data-label="Goes to"><span *ngIf="x.recipients.length">{{ x.recipients.join(', ') }}</span><span style="color:#b91c1c;" *ngIf="x.problem">{{ x.problem }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="card mb-3 collections-box" style="padding:1rem; max-width:40rem;">
        <strong>{{ 'Fee reminders waiting to go out' | translate }}</strong>
        <div class="text-muted" style="font-size:0.85rem;">{{ 'This uses the saved settings. Here you can look at the reminders the daily run would send, and send them now.' | translate }}</div>
        <div class="mt-2" style="display:flex; gap:0.5rem; flex-wrap:wrap;">
          <button type="button" class="btn btn-sm btn-outline-secondary col-preview" (click)="previewReminders()" [disabled]="colBusy">{{ 'See what would be sent' | translate }}</button>
          <button type="button" class="btn btn-sm btn-primary col-send" (click)="sendReminders()" [disabled]="colBusy || !colItems?.length">{{ 'Send now' | translate }}</button>
        </div>
        <div class="alert alert-danger mt-2" *ngIf="colError">{{ colError }}</div>
        <div class="alert alert-success mt-2 col-result" *ngIf="colResult">{{ 'Sent {p0} e-mail{p1} for {p2} invoice{p3}' | translate: { p0: (colResult.mails), p1: (colResult.mails === 1 ? '' : 's'), p2: (colResult.invoices), p3: (colResult.invoices === 1 ? '' : 's') } }}<span *ngIf="colResult.skipped"> {{ '({p0} client{p1} could not be reached)' | translate: { p0: (colResult.skipped), p1: (colResult.skipped === 1 ? '' : 's') } }}</span>.</div>
        <p class="text-muted mt-2 mb-0" *ngIf="colItems && !colItems.length">{{ 'Nothing is waiting.' | translate }}</p>
        <table class="stack table-sm mt-2" *ngIf="colItems?.length">
          <thead><tr><th>{{ 'Client' | translate }}</th><th>{{ 'E-mail' | translate }}</th><th class="text-end">{{ 'Invoices' | translate }}</th><th class="text-end">{{ 'Still to pay' | translate }}</th></tr></thead>
          <tbody>
            <tr *ngFor="let x of colItems">
              <td data-label="Client">{{ x.clientName }}</td>
              <td data-label="E-mail"><span *ngIf="x.email">{{ x.email }}</span><span style="color:#b91c1c;" *ngIf="x.problem">{{ x.problem }}</span></td>
              <td class="text-end" data-label="Invoices">{{ x.invoices }}</td>
              <td class="text-end" data-label="Still to pay">{{ x.outstanding | number: '1.0-2' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <app-law-cost-rates></app-law-cost-rates>
    </app-shell>
  `,
})
export class LawSettingsComponent implements OnInit {
  practiceAreas = '';
  jurisdictions = '';
  reminderDays = 7;
  billingType: BillingType = 'HOURLY';
  digest = true;
  restricted = false;
  trustEnabled = false;
  trustBank = '';
  trustNumber = '';
  portalEnabled = false;
  escalationEnabled = false;
  escalationDays = 14;
  escItems: EscalationItem[] | null = null;
  escResult: EscalationResult | null = null;
  escError: string | null = null;
  escBusy = false;
  collectionsEnabled = false;
  collectionsFirstDays = 7;
  collectionsRepeatDays = 14;
  collectionsMax = 3;
  defaultTermDays = 30;
  colItems: ReminderItem[] | null = null;
  colResult: RemindResult | null = null;
  colError: string | null = null;
  colBusy = false;
  saving = false;
  saved = false;
  error: string | null = null;

  constructor(private api: LawApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({
      next: (s) => {
        this.practiceAreas = s.practiceAreas.join('\n');
        this.jurisdictions = s.jurisdictions.join('\n');
        this.reminderDays = s.reminderDays;
        this.billingType = s.defaultBillingType;
        this.digest = s.digestEnabled;
        this.restricted = s.restrictedByDefault;
        this.trustEnabled = s.trustEnabled;
        this.trustBank = s.trustBankName ?? '';
        this.trustNumber = s.trustAccountNumber ?? '';
        this.portalEnabled = s.portalEnabled;
        this.escalationEnabled = s.escalationEnabled;
        this.escalationDays = s.escalationDays;
        this.collectionsEnabled = s.collectionsEnabled;
        this.collectionsFirstDays = s.collectionsFirstDays;
        this.collectionsRepeatDays = s.collectionsRepeatDays;
        this.collectionsMax = s.collectionsMax;
        this.defaultTermDays = s.defaultTermDays;
      },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings'),
    });
  }

  save(): void {
    this.saving = true;
    this.saved = false;
    this.error = null;
    this.api.updateSettings({ practiceAreas: this.practiceAreas, jurisdictions: this.jurisdictions, reminderDays: this.reminderDays, digestEnabled: this.digest,
      restrictedByDefault: this.restricted, defaultBillingType: this.billingType, trustEnabled: this.trustEnabled, trustBankName: this.trustBank, trustAccountNumber: this.trustNumber, portalEnabled: this.portalEnabled,
      escalationEnabled: this.escalationEnabled, escalationDays: this.escalationDays,
      collectionsEnabled: this.collectionsEnabled, collectionsFirstDays: this.collectionsFirstDays, collectionsRepeatDays: this.collectionsRepeatDays, collectionsMax: this.collectionsMax, defaultTermDays: this.defaultTermDays }).subscribe({
      next: () => { this.saving = false; this.saved = true; },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the settings'; },
    });
  }

  previewEscalations(): void {
    this.escBusy = true; this.escError = null; this.escResult = null;
    this.api.escalations().subscribe({
      next: (l) => { this.escBusy = false; this.escItems = l; },
      error: (e) => { this.escBusy = false; this.escError = e?.error?.message ?? 'Could not load the list'; },
    });
  }

  sendEscalations(): void {
    this.escBusy = true; this.escError = null; this.escResult = null;
    this.api.sendEscalations().subscribe({
      next: (r) => { this.escBusy = false; this.escResult = r; this.previewEscalations(); this.escResult = r; },
      error: (e) => { this.escBusy = false; this.escError = e?.error?.message ?? 'Could not send the e-mails'; },
    });
  }

  previewReminders(): void {
    this.colBusy = true; this.colError = null; this.colResult = null;
    this.api.reminderPreview().subscribe({
      next: (l) => { this.colBusy = false; this.colItems = l; },
      error: (e) => { this.colBusy = false; this.colError = e?.error?.message ?? 'Could not load the list'; },
    });
  }

  sendReminders(): void {
    this.colBusy = true; this.colError = null; this.colResult = null;
    this.api.runReminders().subscribe({
      next: (r) => { this.colBusy = false; this.previewReminders(); this.colResult = r; },
      error: (e) => { this.colBusy = false; this.colError = e?.error?.message ?? 'Could not send the e-mails'; },
    });
  }
}
