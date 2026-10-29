import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { WoSettings, WorkOrderApiService } from '../../core/api/work-order-api.service';
import { WorkOrderTabsComponent } from './work-order-tabs.component';

/** Work Orders > Settings: whether new customer jobs need the customer's approval, and whether the customer is e-mailed when a job is done. */
@Component({
  selector: 'app-work-order-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, WorkOrderTabsComponent],
  template: `
    <app-shell>
      <h3 class="mb-2">Work orders</h3>
      <app-work-order-tabs></app-work-order-tabs>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="notice">{{ notice }}</div>
      <form class="card" style="padding:1rem; max-width:44rem;" *ngIf="s" (ngSubmit)="save()">
        <label style="display:flex; gap:0.5rem; align-items:flex-start;" class="mb-3">
          <input type="checkbox" name="ra" [(ngModel)]="s.requireApprovalDefault" />
          <span><strong>New customer jobs need the customer's approval</strong><br />
            <span class="text-muted">Work cannot start until an estimate has been sent and the customer has approved it. You can switch this off for a single job.</span></span>
        </label>
        <label style="display:flex; gap:0.5rem; align-items:flex-start;" class="mb-3">
          <input type="checkbox" name="ec" [(ngModel)]="s.emailCustomerOnComplete" />
          <span><strong>E-mail the customer when a job is completed</strong><br />
            <span class="text-muted">A completion note (PDF) is sent to the customer's e-mail address. Needs e-mail to be set up in Settings. If it fails, the job is still completed.</span></span>
        </label>
        <div><button class="btn btn-primary" type="submit" [disabled]="busy">Save</button></div>
      </form>
    </app-shell>
  `,
})
export class WorkOrderSettingsComponent implements OnInit {
  s: WoSettings | null = null;
  busy = false;
  error: string | null = null;
  notice: string | null = null;

  constructor(private api: WorkOrderApiService) {}

  ngOnInit(): void {
    this.api.settings().subscribe({ next: (s) => (this.s = s), error: (e) => (this.error = e?.error?.message ?? 'Could not load the settings') });
  }

  save(): void {
    if (!this.s) return;
    this.busy = true;
    this.api.saveSettings(this.s).subscribe({
      next: (s) => { this.busy = false; this.s = s; this.error = null; this.notice = 'Saved.'; },
      error: (e) => { this.busy = false; this.notice = null; this.error = e?.error?.message ?? 'Could not save'; },
    });
  }
}
