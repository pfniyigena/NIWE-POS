import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { API_BASE } from '../../core/constants';

interface Slice { label: string; value: number; }
interface Attention { kind: 'DEADLINE' | 'UPLOAD' | 'MESSAGE' | 'CONFLICT'; matterId: number; matterNumber: string; text: string; date: string | null; }
interface LawOverview {
  openMatters: number; onHold: number; openedThisMonth: number; closedThisMonth: number; deadlinesThisWeek: number; overdueDeadlines: number; overdueTasks: number;
  conflictsNotCleared: number; hoursThisMonth: number; billableHoursThisMonth: number; billedThisMonth: number; unbilledWork: number; outstanding: number;
  outstandingOver60Days: number; trustEnabled: boolean; clientMoneyHeld: number; portalEnabled: boolean; waitingUploads: number; unansweredMessages: number;
  openByPracticeArea: Slice[]; billedByMonth: Slice[]; outstandingByAge: Slice[]; hoursByLawyer: Slice[]; attention: Attention[];
}

/**
 * Law Firm > Overview: the first page for a partner. It shows the matters the signed-in person may see, what has been billed and is still owed, the time
 * logged this month and what needs attention. The charts are plain bars (no chart library); every number comes from GET /api/law/overview.
 */
@Component({
  selector: 'app-law-overview',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Law Firm overview</h3>
        <span class="text-muted" style="font-size:0.85rem;">Only the matters you may open are counted.</span>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div *ngIf="!o && !error" class="text-muted">Loading…</div>

      <ng-container *ngIf="o">
        <div style="display:grid; grid-template-columns:repeat(auto-fill, minmax(11rem, 1fr)); gap:0.75rem;" class="mb-3">
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">Open matters</div><div class="kpi">{{ o.openMatters }}</div><div class="text-muted kpi-sub">{{ o.onHold }} on hold · {{ o.openedThisMonth }} opened and {{ o.closedThisMonth }} closed this month</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">Deadlines this week</div><div class="kpi">{{ o.deadlinesThisWeek }}</div><div class="kpi-sub" [style.color]="o.overdueDeadlines ? '#b91c1c' : ''">{{ o.overdueDeadlines }} overdue · {{ o.overdueTasks }} tasks overdue</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">Hours this month</div><div class="kpi">{{ o.hoursThisMonth | number: '1.0-1' }}</div><div class="text-muted kpi-sub">{{ o.billableHoursThisMonth | number: '1.0-1' }} billable</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">Billed this month</div><div class="kpi">{{ o.billedThisMonth | number: '1.0-0' }}</div><div class="text-muted kpi-sub">{{ o.unbilledWork | number: '1.0-0' }} of work not yet billed</div></div>
          <div class="card" style="padding:0.85rem;"><div class="text-muted kpi-label">Owed by clients</div><div class="kpi">{{ o.outstanding | number: '1.0-0' }}</div><div class="kpi-sub" [style.color]="o.outstandingOver60Days ? '#b91c1c' : ''">{{ o.outstandingOver60Days | number: '1.0-0' }} older than 60 days</div></div>
          <div class="card" style="padding:0.85rem;" *ngIf="o.trustEnabled"><div class="text-muted kpi-label">Client money held</div><div class="kpi">{{ o.clientMoneyHeld | number: '1.0-0' }}</div><div class="text-muted kpi-sub">in the trust account</div></div>
          <div class="card" style="padding:0.85rem;" *ngIf="o.portalEnabled"><div class="text-muted kpi-label">Client portal</div><div class="kpi">{{ o.waitingUploads + o.unansweredMessages }}</div><div class="text-muted kpi-sub">{{ o.waitingUploads }} documents to review · {{ o.unansweredMessages }} messages</div></div>
          <div class="card" style="padding:0.85rem;" *ngIf="o.conflictsNotCleared"><div class="text-muted kpi-label">Conflict checks open</div><div class="kpi" style="color:#b45309;">{{ o.conflictsNotCleared }}</div><div class="text-muted kpi-sub">matters not cleared</div></div>
        </div>

        <div class="card mb-3" style="padding:1rem;" *ngIf="o.attention.length">
          <h4 class="mb-2">Needs attention</h4>
          <table class="table-sm"><tbody>
            <tr *ngFor="let a of o.attention">
              <td style="width:9rem;"><span class="badge">{{ kindLabel(a.kind) }}</span></td>
              <td><a [routerLink]="['/law/matters', a.matterId]"><strong>{{ a.matterNumber }}</strong></a> · {{ a.text }}<span class="text-muted" *ngIf="a.date"> · {{ a.date | date: 'd MMM y' }}</span></td>
            </tr>
          </tbody></table>
        </div>

        <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(20rem, 1fr)); gap:0.75rem;">
          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Fees billed, last six months</h4>
            <div style="display:flex; align-items:flex-end; gap:0.5rem; height:9rem;" *ngIf="max(o.billedByMonth) > 0; else nobills">
              <div *ngFor="let s of o.billedByMonth" style="flex:1; display:flex; flex-direction:column; justify-content:flex-end; align-items:center; height:100%;">
                <div class="text-muted" style="font-size:0.7rem;">{{ s.value | number: '1.0-0' }}</div>
                <div [style.height.%]="pct(s.value, o.billedByMonth)" style="width:100%; min-height:2px; background:#2563eb; border-radius:3px 3px 0 0;"></div>
                <div class="text-muted" style="font-size:0.7rem; margin-top:2px;">{{ s.label.slice(2) }}</div>
              </div>
            </div>
            <ng-template #nobills><div class="text-muted">Nothing billed in these months.</div></ng-template>
          </div>

          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Owed by clients, by age</h4>
            <div *ngIf="max(o.outstandingByAge) > 0; else noowed">
              <div *ngFor="let s of o.outstandingByAge" class="bar-row">
                <span class="bar-label">{{ s.label }}</span>
                <span class="bar-track"><span class="bar-fill" [style.width.%]="pct(s.value, o.outstandingByAge)" [style.background]="s.label.startsWith('Over') || s.label.startsWith('61') ? '#b91c1c' : '#2563eb'"></span></span>
                <span class="bar-num">{{ s.value | number: '1.0-0' }}</span>
              </div>
            </div>
            <ng-template #noowed><div class="text-muted">No unpaid invoices.</div></ng-template>
          </div>

          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Open matters by practice area</h4>
            <div *ngFor="let s of o.openByPracticeArea" class="bar-row">
              <span class="bar-label">{{ s.label }}</span>
              <span class="bar-track"><span class="bar-fill" [style.width.%]="pct(s.value, o.openByPracticeArea)" style="background:#0f766e;"></span></span>
              <span class="bar-num">{{ s.value }}</span>
            </div>
            <div class="text-muted" *ngIf="!o.openByPracticeArea.length">No open matters.</div>
          </div>

          <div class="card" style="padding:1rem;">
            <h4 class="mb-2">Hours this month, by lawyer</h4>
            <div *ngFor="let s of o.hoursByLawyer" class="bar-row">
              <span class="bar-label">{{ s.label }}</span>
              <span class="bar-track"><span class="bar-fill" [style.width.%]="pct(s.value, o.hoursByLawyer)" style="background:#7c3aed;"></span></span>
              <span class="bar-num">{{ s.value | number: '1.0-1' }}</span>
            </div>
            <div class="text-muted" *ngIf="!o.hoursByLawyer.length">No time logged yet this month.</div>
          </div>
        </div>
      </ng-container>
    </app-shell>
  `,
  styles: [`
    .kpi-label { font-size: 0.8rem; }
    .kpi { font-size: 1.75rem; font-weight: 600; line-height: 1.2; }
    .kpi-sub { font-size: 0.75rem; }
    .bar-row { display: flex; align-items: center; gap: 0.5rem; margin-bottom: 0.4rem; }
    .bar-label { width: 8rem; font-size: 0.85rem; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .bar-track { flex: 1; height: 0.9rem; background: rgba(127, 127, 127, 0.15); border-radius: 3px; overflow: hidden; }
    .bar-fill { display: block; height: 100%; min-width: 2px; border-radius: 3px; }
    .bar-num { width: 5rem; text-align: right; font-size: 0.85rem; }
  `],
})
export class LawOverviewComponent implements OnInit {
  o: LawOverview | null = null;
  error: string | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.http.get<LawOverview>(`${API_BASE}/law/overview`).subscribe({
      next: (o) => (this.o = o),
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the overview'),
    });
  }

  max(list: Slice[]): number { return list.reduce((m, s) => Math.max(m, s.value), 0); }

  pct(value: number, list: Slice[]): number {
    const m = this.max(list);
    return m > 0 ? Math.max(0, Math.min(100, (value / m) * 100)) : 0;
  }

  kindLabel(k: Attention['kind']): string {
    return { DEADLINE: 'Overdue date', UPLOAD: 'Client upload', MESSAGE: 'Client message', CONFLICT: 'Conflict check' }[k];
  }
}
