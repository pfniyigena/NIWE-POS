import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { ReDocumentsComponent } from './re-documents.component';
import { RePhotoComponent } from './re-photo.component';
import { ViewingFormComponent } from './viewing-form.component';
import {
  INTEREST_LABEL, LEAD_STATUS_LABEL, LeadDto, LeadStatus, Match, NOTE_KIND_LABEL, NoteKind, PURPOSE_LABEL, RealEstateApiService, SOURCE_LABEL, typeLabel, ViewingDto,
  VIEWING_STATUS_LABEL, ViewingStatus,
} from '../../core/api/realestate-api.service';

/** Real Estate > Leads > one lead: contact details, what they want, the diary of calls, viewings, and the properties that suit them. */
@Component({
  selector: 'app-lead-detail',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent, ReDocumentsComponent, RePhotoComponent, ViewingFormComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!l && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <ng-container *ngIf="l">
        <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a routerLink="/admin/realestate/leads" style="font-size:0.85rem;">{{ '← Leads' | translate }}</a>
            <h3>{{ l.name }}
              <span class="badge" [class.badge-success]="l.status === 'WON'" [class.badge-danger]="l.status === 'LOST'" [class.badge-warning]="l.status === 'NEGOTIATING' || l.status === 'VIEWING'" [class.badge-secondary]="l.status === 'NEW' || l.status === 'CONTACTED'" style="font-size:0.8rem; vertical-align:middle;">{{ stageName(l.status) }}</span></h3>
          </div>
          <div class="flex gap-2" *ngIf="l.canManage" style="flex-wrap:wrap;">
            <a class="btn btn-outline-secondary" [routerLink]="['/admin/realestate/leads', l.id, 'edit']">{{ 'Edit' | translate }}</a>
            <button class="btn btn-outline-primary" type="button" (click)="showViewing = !showViewing" *ngIf="open()">{{ 'Book viewing' | translate }}</button>
            <a class="btn btn-outline-primary" routerLink="/admin/realestate/deals/new" [queryParams]="{ leadId: l.id, propertyId: l.propertyId }" *ngIf="open() && l.interest !== 'RENT'">{{ 'Record an offer' | translate }}</a>
            <button class="btn btn-outline-danger" type="button" (click)="remove()">{{ 'Delete' | translate }}</button>
          </div>
        </div>

        <app-viewing-form *ngIf="showViewing" [fixedLeadId]="l.id" (saved)="viewingSaved()" (closed)="showViewing = false"></app-viewing-form>
        <app-viewing-form *ngIf="moving" [existing]="moving" (saved)="viewingSaved()" (closed)="moving = null"></app-viewing-form>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(22rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Contact' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">{{ 'Phone' | translate }}</td><td>{{ l.phone || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'E-mail' | translate }}</td><td>{{ l.email || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Came through' | translate }}</td><td>{{ sourceName(l.source) }}</td></tr>
              <tr><td class="text-muted">{{ 'Looked after by' | translate }}</td><td>{{ l.assignedName || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Added' | translate }}</td><td>{{ '{p0} by {p1}' | translate: { p0: (l.createdAt | date: 'd MMM y'), p1: (l.createdByName || '—') } }}</td></tr>
              <tr><td class="text-muted">{{ 'Last contact' | translate }}</td><td>{{ l.lastContactAt ? (l.lastContactAt | date: 'd MMM y, HH:mm') : 'None yet' }}</td></tr>
              <tr><td class="text-muted">{{ 'Next follow-up' | translate }}</td><td>{{ l.nextFollowUp ? (l.nextFollowUp | date: 'd MMM y') : '—' }}</td></tr>
              <tr *ngIf="l.customerId"><td class="text-muted">{{ 'Customer' | translate }}</td><td><a [routerLink]="['/admin/customers', l.customerId]">{{ 'Open the customer record' | translate }}</a></td></tr>
              <tr *ngIf="l.lostReason"><td class="text-muted">{{ 'Why lost' | translate }}</td><td>{{ l.lostReason }}</td></tr>
            </tbody></table>
          </div>
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'What they want' | translate }}</h4>
            <table class="table-sm"><tbody>
              <tr><td class="text-muted" style="width:9rem;">{{ 'Looking to' | translate }}</td><td>{{ interestName(l.interest) }}</td></tr>
              <tr><td class="text-muted">{{ 'Budget' | translate }}</td><td>{{ budget() }}</td></tr>
              <tr><td class="text-muted">{{ 'Area' | translate }}</td><td>{{ l.preferredArea || '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Type' | translate }}</td><td>{{ l.preferredType ? typeName(l.preferredType) : 'Any' }}</td></tr>
              <tr><td class="text-muted">{{ 'Bedrooms' | translate }}</td><td>{{ l.minBedrooms != null ? l.minBedrooms + ' or more' : '—' }}</td></tr>
              <tr><td class="text-muted">{{ 'Asked about' | translate }}</td><td><a *ngIf="l.propertyId" [routerLink]="['/admin/realestate/properties', l.propertyId]">{{ l.propertyCode }} · {{ l.propertyTitle }}</a><span *ngIf="!l.propertyId">—</span></td></tr>
              <tr *ngIf="l.requirements"><td class="text-muted">{{ 'Other' | translate }}</td><td>{{ l.requirements }}</td></tr>
            </tbody></table>
          </div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="l.canManage">
          <h4 class="mb-2">{{ 'Move the lead along' | translate }}</h4>
          <div class="alert alert-danger" *ngIf="stageError">{{ stageError }}</div>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <button *ngFor="let s of nextStages()" class="btn btn-sm btn-outline-primary" type="button" (click)="setStage(s)" [disabled]="busy">{{ stageName(s) }}</button>
            <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="open() && !l.customerId" (click)="convert()" [disabled]="busy" [title]="'Create (or find) the customer record so the lead can be won' | translate">{{ 'Make a customer' | translate }}</button>
            <button class="btn btn-sm btn-success" type="button" *ngIf="open()" (click)="setStage('WON')" [disabled]="busy">{{ 'Won' | translate }}</button>
            <button class="btn btn-sm btn-outline-danger" type="button" *ngIf="open()" (click)="lostOpen = !lostOpen">{{ 'Lost…' | translate }}</button>
            <button class="btn btn-sm btn-outline-secondary" type="button" *ngIf="!open()" (click)="setStage('CONTACTED')" [disabled]="busy">{{ 'Reopen' | translate }}</button>
          </div>
          <div class="flex gap-2 mt-2" *ngIf="lostOpen">
            <input class="form-control" style="max-width:24rem;" [placeholder]="'Why was it lost? (e.g. bought elsewhere)' | translate" name="lr" [(ngModel)]="lostReason" maxlength="300" />
            <button class="btn btn-sm btn-danger" type="button" (click)="setStage('LOST')" [disabled]="busy">{{ 'Mark lost' | translate }}</button>
          </div>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(26rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">{{ 'Diary' | translate }}</h4>
            <form *ngIf="l.canManage" (ngSubmit)="addNote()" class="mb-3">
              <div class="flex gap-2 mb-2" style="flex-wrap:wrap;">
                <select class="form-select" style="width:auto;" name="nk" [(ngModel)]="noteKind"><option *ngFor="let k of kinds" [value]="k.value">{{ k.label }}</option></select>
                <input class="form-control" style="width:11rem;" type="date" name="nf" [(ngModel)]="noteFollowUp" [title]="'Next follow-up (optional)' | translate" />
              </div>
              <textarea class="form-control mb-2" rows="2" name="nt" [(ngModel)]="noteText" maxlength="2000" placeholder="What was said or agreed?"></textarea>
              <button class="btn btn-primary btn-sm" type="submit" [disabled]="busy || !noteText.trim()">{{ 'Add to diary' | translate }}</button>
            </form>
            <div *ngFor="let n of l.notes" class="mb-2" style="border-left:3px solid rgba(127,127,127,0.4); padding-left:0.6rem;">
              <div class="text-muted" style="font-size:0.8rem;"><span class="badge badge-secondary">{{ kindName(n.kind) }}</span> {{ n.createdAt | date: 'd MMM y, HH:mm' }} · {{ n.createdByName }}
                <a href="#" *ngIf="l.canManage" (click)="$event.preventDefault(); removeNote(n.id)" style="margin-left:0.5rem; color:#b91c1c;">{{ 'remove' | translate }}</a></div>
              <div style="white-space:pre-wrap;">{{ n.text }}</div>
            </div>
            <div class="text-muted" *ngIf="!l.notes.length">{{ 'Nothing written yet.' | translate }}</div>
          </div>

          <div>
            <div class="card mb-3" style="padding:1rem;">
              <h4 class="mb-2">{{ 'Viewings' | translate }}</h4>
              <table class="table-sm" *ngIf="l.viewings.length; else noviews"><tbody>
                <tr *ngFor="let v of l.viewings">
                  <td style="white-space:nowrap;">{{ v.startAt | date: 'EEE d MMM, HH:mm' }}</td>
                  <td><a [routerLink]="['/admin/realestate/properties', v.propertyId]">{{ v.propertyCode }}</a> · {{ v.propertyTitle }}<div class="text-muted" style="font-size:0.8rem;">{{ 'with {p0}' | translate: { p0: (v.agentName || '—') } }}<span *ngIf="v.feedback"> · “{{ v.feedback }}”</span></div></td>
                  <td style="white-space:nowrap; text-align:right;">
                    <span class="badge" [class.badge-success]="v.status === 'DONE'" [class.badge-warning]="v.status === 'SCHEDULED'" [class.badge-secondary]="v.status === 'CANCELLED' || v.status === 'NO_SHOW'">{{ viewingName(v.status) }}</span>
                    <ng-container *ngIf="v.status === 'SCHEDULED' && v.canManage">
                      <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.3rem;" (click)="moving = v">{{ 'Move' | translate }}</button>
                      <button class="btn btn-sm btn-outline-secondary" type="button" style="margin-left:0.2rem;" (click)="finish(v)">{{ 'Outcome' | translate }}</button>
                    </ng-container>
                  </td>
                </tr>
              </tbody></table>
              <ng-template #noviews><div class="text-muted">{{ 'No viewings yet.' | translate }}</div></ng-template>
              <div class="mt-2" *ngIf="outcome">
                <div class="alert alert-danger" *ngIf="outcomeError">{{ outcomeError }}</div>
                <strong>{{ 'Outcome of the viewing on {p0}' | translate: { p0: (outcome.startAt | date: 'd MMM, HH:mm') } }}</strong>
                <div class="flex gap-2 mt-1" style="flex-wrap:wrap;">
                  <select class="form-select" style="width:auto;" name="os" [(ngModel)]="outcomeStatus"><option value="DONE">{{ 'Happened' | translate }}</option><option value="NO_SHOW">{{ 'No-show' | translate }}</option><option value="CANCELLED">{{ 'Cancelled' | translate }}</option></select>
                  <input class="form-control" style="flex:1; min-width:12rem;" name="of" [(ngModel)]="outcomeFeedback" maxlength="2000" [placeholder]="'Feedback (optional)' | translate" />
                  <button class="btn btn-sm btn-primary" type="button" (click)="saveOutcome()" [disabled]="busy">{{ 'Save' | translate }}</button>
                  <button class="btn btn-sm btn-outline-secondary" type="button" (click)="outcome = null">{{ 'Cancel' | translate }}</button>
                </div>
              </div>
            </div>

            <div class="card" style="padding:1rem;" *ngIf="open()">
              <h4 class="mb-2">{{ 'Properties that suit {p0}' | translate: { p0: (firstName()) } }}</h4>
              <div *ngIf="matches === null" class="text-muted">{{ 'Looking…' | translate }}</div>
              <div *ngFor="let m of matches" class="flex gap-2 mb-2 align-center">
                <app-re-photo [propertyId]="m.property.id" [photoId]="m.property.coverPhotoId"></app-re-photo>
                <div style="flex:1;">
                  <a [routerLink]="['/admin/realestate/properties', m.property.id]"><strong>{{ m.property.code }}</strong> · {{ m.property.title }}</a>
                  <div class="text-muted" style="font-size:0.8rem;">{{ m.property.area || '—' }} · {{ price(m.property) }}</div>
                  <div style="font-size:0.8rem;">{{ m.reasons.join(' · ') }}</div>
                </div>
              </div>
              <div class="text-muted" *ngIf="matches && !matches.length">{{ 'No available property fits yet. Check the budget, area and type.' | translate }}</div>
            </div>
          </div>
        </div>
        <div class="card mb-3" style="padding:1rem;">
          <app-re-documents subjectType="LEAD" [subjectId]="l.id" [canManage]="l.canManage" defaultCategory="ID_DOCUMENT"></app-re-documents>
        </div>
      </ng-container>
    </app-shell>
  `,
})
export class LeadDetailComponent implements OnInit {
  l: LeadDto | null = null;
  matches: Match[] | null = null;
  error: string | null = null;
  stageError: string | null = null;
  busy = false;
  showViewing = false;
  moving: ViewingDto | null = null;
  lostOpen = false;
  lostReason = '';
  noteKind: NoteKind = 'CALL';
  noteText = '';
  noteFollowUp = '';
  outcome: ViewingDto | null = null;
  outcomeStatus: ViewingStatus = 'DONE';
  outcomeFeedback = '';
  outcomeError: string | null = null;
  kinds = (Object.keys(NOTE_KIND_LABEL) as NoteKind[]).map((k) => ({ value: k, label: NOTE_KIND_LABEL[k] }));

  constructor(private api: RealEstateApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.reload();
  }

  private reload(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.api.lead(id).subscribe({
      next: (l) => { this.l = l; this.loadMatches(); },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the lead'),
    });
  }

  private loadMatches(): void {
    if (!this.l || !this.open()) { this.matches = []; return; }
    this.api.matches(this.l.id).subscribe({ next: (m) => (this.matches = m), error: () => (this.matches = []) });
  }

  open(): boolean { return !!this.l && this.l.status !== 'WON' && this.l.status !== 'LOST'; }
  firstName(): string { return (this.l?.name ?? '').split(' ')[0]; }
  stageName(s: string): string { return (LEAD_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  interestName(s: string): string { return (INTEREST_LABEL as Record<string, string>)[s] ?? s; }
  sourceName(s: string): string { return (SOURCE_LABEL as Record<string, string>)[s] ?? s; }
  kindName(s: string): string { return (NOTE_KIND_LABEL as Record<string, string>)[s] ?? s; }
  viewingName(s: string): string { return (VIEWING_STATUS_LABEL as Record<string, string>)[s] ?? s; }
  typeName(t: string): string { return typeLabel(t); }

  budget(): string {
    const l = this.l!;
    const f = (n: number) => Math.round(n).toLocaleString();
    if (l.budgetMin != null && l.budgetMax != null) return `${f(l.budgetMin)} – ${f(l.budgetMax)}`;
    if (l.budgetMax != null) return `up to ${f(l.budgetMax)}`;
    if (l.budgetMin != null) return `from ${f(l.budgetMin)}`;
    return '—';
  }

  price(p: { salePrice: number | null; rentPrice: number | null; purpose: string }): string {
    const parts: string[] = [];
    if (p.salePrice != null && p.purpose !== 'RENT') parts.push(Math.round(p.salePrice).toLocaleString());
    if (p.rentPrice != null && p.purpose !== 'SALE') parts.push(Math.round(p.rentPrice).toLocaleString() + ' / month');
    return parts.join(' or ') || (PURPOSE_LABEL as Record<string, string>)[p.purpose];
  }

  /** The stages one step either side of where the lead is now (won and lost have their own buttons). */
  nextStages(): LeadStatus[] {
    if (!this.l || !this.open()) return [];
    const order: LeadStatus[] = ['NEW', 'CONTACTED', 'VIEWING', 'NEGOTIATING'];
    return order.filter((s) => s !== this.l!.status && s !== 'NEW');
  }

  setStage(s: LeadStatus): void {
    if (!this.l) return;
    this.stageError = null;
    if (s === 'LOST' && !this.lostReason.trim()) { this.stageError = 'Say why the lead was lost.'; return; }
    this.busy = true;
    const id = this.l.id;
    const move = () => this.api.setLeadStatus(id, s, s === 'LOST' ? this.lostReason.trim() : null).subscribe({
      next: (l) => { this.busy = false; this.l = l; this.lostOpen = false; this.lostReason = ''; this.loadMatches(); },
      error: (e) => { this.busy = false; this.stageError = e?.error?.message ?? 'Could not change the stage'; },
    });
    // A won lead is a customer: make the customer record first if there is none yet.
    if (s === 'WON' && !this.l.customerId) {
      this.api.convertLead(id).subscribe({ next: (l) => { this.l = l; move(); }, error: (e) => { this.busy = false; this.stageError = e?.error?.message ?? 'Could not create the customer'; } });
    } else {
      move();
    }
  }

  convert(): void {
    if (!this.l) return;
    this.busy = true;
    this.stageError = null;
    this.api.convertLead(this.l.id).subscribe({
      next: (l) => { this.busy = false; this.l = l; },
      error: (e) => { this.busy = false; this.stageError = e?.error?.message ?? 'Could not create the customer'; },
    });
  }

  addNote(): void {
    if (!this.l || !this.noteText.trim()) return;
    this.busy = true;
    this.api.addNote(this.l.id, { kind: this.noteKind, text: this.noteText.trim(), nextFollowUp: this.noteFollowUp || null }).subscribe({
      next: (l) => { this.busy = false; this.l = l; this.noteText = ''; this.noteFollowUp = ''; },
      error: (e) => { this.busy = false; this.stageError = e?.error?.message ?? 'Could not save the note'; },
    });
  }

  removeNote(noteId: number): void {
    if (!this.l || !confirm('Remove this entry from the diary?')) return;
    this.api.deleteNote(this.l.id, noteId).subscribe({ next: (l) => (this.l = l), error: (e) => (this.stageError = e?.error?.message ?? 'Could not remove the entry') });
  }

  viewingSaved(): void {
    this.showViewing = false;
    this.moving = null;
    this.reload();
  }

  finish(v: ViewingDto): void {
    this.outcome = v;
    this.outcomeStatus = 'DONE';
    this.outcomeFeedback = '';
    this.outcomeError = null;
  }

  saveOutcome(): void {
    if (!this.outcome) return;
    this.busy = true;
    this.api.setViewingStatus(this.outcome.id, this.outcomeStatus, this.outcomeFeedback.trim() || null).subscribe({
      next: () => { this.busy = false; this.outcome = null; this.reload(); },
      error: (e) => { this.busy = false; this.outcomeError = e?.error?.message ?? 'Could not save the outcome'; },
    });
  }

  remove(): void {
    if (!this.l || !confirm('Delete this lead? This cannot be undone.')) return;
    this.api.deleteLead(this.l.id).subscribe({
      next: () => this.router.navigate(['/admin/realestate/leads']),
      error: (e) => (this.error = e?.error?.message ?? 'Could not delete the lead'),
    });
  }
}
