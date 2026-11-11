import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { WorkOrderApiService, WorkOrderSummary } from '../core/api/work-order-api.service';

import { T } from '../core/i18n/t';
/** Work Orders > My jobs: the jobs assigned to the signed-in person. Open to every signed-in user. */
@Component({
  selector: 'app-my-jobs',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">{{ 'My jobs' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="card mb-2" style="padding:0.8rem;" *ngFor="let w of active()">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <div>
            <a [routerLink]="['/work-orders', w.id]"><strong>{{ w.number }}</strong> · {{ w.title }}</a>
            <div class="text-muted" style="font-size:0.85rem;">
              {{ w.type === 'CUSTOMER' ? (w.customerName || ('Customer job' | translate)) : ('Internal' | translate) }}<span *ngIf="w.assetName"> · {{ w.assetName }}</span>
              {{ '· due' | translate }} <span [style.color]="w.overdue ? '#c0392b' : ''">{{ w.dueDate || 'no date' }}</span>
              {{ '· checklist {p0}/{p1} · {p2} h' | translate: { p0: (w.checklistDone), p1: (w.checklistTotal), p2: (w.hours | number: '1.0-2') } }}</div>
          </div>
          <span class="badge" [class.badge-primary]="w.status === 'IN_PROGRESS'" [class.badge-warning]="w.status === 'ON_HOLD'" [class.badge-secondary]="w.status === 'OPEN'">{{ w.status.replace('_', ' ') }}</span>
        </div>
      </div>
      <p class="text-muted" *ngIf="loaded && !active().length">{{ 'Nothing is assigned to you right now.' | translate }}</p>
      <details *ngIf="done().length" class="mt-3">
        <summary class="text-muted">{{ 'Finished ({p0})' | translate: { p0: (done().length) } }}</summary>
        <table class="table-sm mt-2"><tbody>
          <tr *ngFor="let w of done()"><td><a [routerLink]="['/work-orders', w.id]">{{ w.number }}</a></td><td>{{ w.title }}</td><td>{{ w.status }}</td></tr>
        </tbody></table>
      </details>
    </app-shell>
  `,
})
export class MyJobsComponent implements OnInit {
  all: WorkOrderSummary[] = [];
  loaded = false;
  error: string | null = null;

  constructor(private api: WorkOrderApiService) {}

  ngOnInit(): void {
    this.api.mine().subscribe({
      next: (l) => { this.all = l; this.loaded = true; },
      error: (e) => (this.error = e?.error?.message ?? T('Could not load your jobs')),
    });
  }

  active(): WorkOrderSummary[] { return this.all.filter((w) => w.status !== 'COMPLETED' && w.status !== 'CANCELLED'); }
  done(): WorkOrderSummary[] { return this.all.filter((w) => w.status === 'COMPLETED'); }
}
