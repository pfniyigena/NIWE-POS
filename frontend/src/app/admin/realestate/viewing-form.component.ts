import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LeadRow, Option, PropertyRow, RealEstateApiService, ViewingDto } from '../../core/api/realestate-api.service';

import { T } from '../../core/i18n/t';
/**
 * Books a viewing (or moves one). Either the property or the lead (or both) can be fixed by the page it is shown on; the other is picked from a list.
 * When the agent already has a viewing at that time the server answers 409 and the form offers to keep both.
 */
@Component({
  selector: 'app-viewing-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="card mb-3" style="padding:1rem;">
      <h4 class="mb-2">{{ existing ? ('Move viewing' | translate) : ('Book a viewing' | translate) }}</h4>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-warning" *ngIf="clash">
        {{ clash }}
        <div class="mt-2"><button class="btn btn-sm btn-outline-secondary" type="button" (click)="save(true)" [disabled]="saving">{{ 'Keep both' | translate }}</button></div>
      </div>
      <div class="flex gap-2" style="flex-wrap:wrap;">
        <div class="form-group" style="flex:1; min-width:14rem;" *ngIf="!existing">
          <label class="form-label">{{ 'Property' | translate }}</label>
          <select class="form-select" name="vp" [(ngModel)]="propertyId" [disabled]="!!fixedPropertyId">
            <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
            <option *ngFor="let p of properties" [ngValue]="p.id">{{ p.code }} · {{ p.title }}</option>
          </select>
        </div>
        <div class="form-group" style="flex:1; min-width:14rem;" *ngIf="!existing">
          <label class="form-label">{{ 'Who is viewing' | translate }}</label>
          <select class="form-select" name="vl" [(ngModel)]="leadId" [disabled]="!!fixedLeadId">
            <option [ngValue]="null">{{ 'Choose…' | translate }}</option>
            <option *ngFor="let l of leads" [ngValue]="l.id">{{ l.name }}<ng-container *ngIf="l.phone"> · {{ l.phone }}</ng-container></option>
          </select>
        </div>
        <div class="form-group" style="min-width:12rem;">
          <label class="form-label">{{ 'Date and time' | translate }}</label>
          <input class="form-control" type="datetime-local" name="vs" [(ngModel)]="start" />
        </div>
        <div class="form-group" style="min-width:10rem;">
          <label class="form-label">{{ 'Ends' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
          <input class="form-control" type="datetime-local" name="ve" [(ngModel)]="end" />
        </div>
        <div class="form-group" style="min-width:12rem;">
          <label class="form-label">{{ 'Agent' | translate }}</label>
          <select class="form-select" name="va" [(ngModel)]="agentId">
            <option [ngValue]="null">{{ 'The property\\'s agent' | translate }}</option>
            <option *ngFor="let a of agents" [ngValue]="a.id">{{ a.name }}</option>
          </select>
        </div>
      </div>
      <div class="flex gap-2">
        <button class="btn btn-primary" type="button" (click)="save(false)" [disabled]="saving">{{ saving ? 'Saving…' : existing ? ('Move' | translate) : ('Book viewing' | translate) }}</button>
        <button class="btn btn-outline-secondary" type="button" (click)="closed.emit()">{{ 'Cancel' | translate }}</button>
      </div>
    </div>
  `,
})
export class ViewingFormComponent implements OnInit {
  @Input() fixedPropertyId: number | null = null;
  @Input() fixedLeadId: number | null = null;
  @Input() existing: ViewingDto | null = null;
  @Output() saved = new EventEmitter<ViewingDto>();
  @Output() closed = new EventEmitter<void>();

  properties: PropertyRow[] = [];
  leads: LeadRow[] = [];
  agents: Option[] = [];
  propertyId: number | null = null;
  leadId: number | null = null;
  agentId: number | null = null;
  start = '';
  end = '';
  saving = false;
  error: string | null = null;
  clash: string | null = null;

  constructor(private api: RealEstateApiService) {}

  ngOnInit(): void {
    this.propertyId = this.fixedPropertyId;
    this.leadId = this.fixedLeadId;
    if (this.existing) {
      this.agentId = this.existing.agentUserId;
      this.start = this.local(this.existing.startAt);
      this.end = this.local(this.existing.endAt);
    } else {
      const d = new Date();
      d.setDate(d.getDate() + 1);
      d.setHours(10, 0, 0, 0);
      this.start = this.local(d.toISOString());
      this.api.properties({ status: 'AVAILABLE' }).subscribe({ next: (l) => (this.properties = l), error: () => {} });
      this.api.leads({ openOnly: true }).subscribe({ next: (l) => (this.leads = l), error: () => {} });
    }
    this.api.agents().subscribe({ next: (a) => (this.agents = a), error: () => {} });
  }

  /** An instant as the yyyy-MM-ddTHH:mm text a datetime-local box wants, in the browser's time zone. */
  private local(iso: string): string {
    const d = new Date(iso);
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  save(force: boolean): void {
    this.error = null;
    if (!this.start) { this.error = T('Choose the date and time.'); return; }
    if (!this.existing && (!this.propertyId || !this.leadId)) { this.error = T('Choose the property and who is viewing.'); return; }
    const startAt = new Date(this.start).toISOString();
    const endAt = this.end ? new Date(this.end).toISOString() : null;
    this.saving = true;
    const body = { propertyId: this.propertyId ?? this.existing!.propertyId, leadId: this.leadId ?? this.existing!.leadId, agentUserId: this.agentId, startAt, endAt, force };
    const call = this.existing ? this.api.reschedule(this.existing.id, body) : this.api.schedule(body);
    call.subscribe({
      next: (v) => { this.saving = false; this.clash = null; this.saved.emit(v); },
      error: (e) => {
        this.saving = false;
        if (e?.status === 409) { this.clash = e?.error?.message ?? 'The agent is busy at that time.'; this.error = null; }
        else { this.clash = null; this.error = e?.error?.message ?? T('Could not save the viewing'); }
      },
    });
  }
}
