import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { RegisterSession, SessionReport } from '../core/models';

@Component({
  selector: 'app-session-close',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <ng-container *ngIf="session">
        <h3 class="mb-1">Close session — {{ session.registerName }}</h3>
        <p class="text-muted">Opened {{ session.openedAt | date: 'yyyy-MM-dd HH:mm' }} by {{ session.cashierName }}</p>

        <table class="table-sm mb-3" *ngIf="report">
          <tbody>
            <tr>
              <td>Opening float</td>
              <td class="text-end">{{ session.openingFloat | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>Cash sales</td>
              <td class="text-end">{{ report.cashIn | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>Cash refunds</td>
              <td class="text-end">−{{ report.cashRefundsOut | number: '1.2-2' }}</td>
            </tr>
            <tr style="font-weight:700;">
              <td>Expected cash in drawer</td>
              <td class="text-end">{{ report.expectedCash | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>Non-cash sales (card/mobile/bank)</td>
              <td class="text-end">{{ report.nonCashIn | number: '1.2-2' }}</td>
            </tr>
            <tr>
              <td>Total sales this session</td>
              <td class="text-end">{{ report.saleCount }} sale(s), {{ report.grossSales | number: '1.2-2' }}</td>
            </tr>
          </tbody>
        </table>

        <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
        <form (ngSubmit)="submit()">
          <div class="form-group">
            <label class="form-label">Counted cash in drawer</label>
            <input class="form-control" type="number" step="0.01" name="countedCash" [(ngModel)]="countedCash" />
          </div>
          <button class="btn btn-danger" type="submit">Close session</button>
        </form>
      </ng-container>
    </app-shell>
  `,
})
export class SessionCloseComponent implements OnInit {
  sessionId!: number;
  session: RegisterSession | null = null;
  report: SessionReport | null = null;
  countedCash = 0;
  error: string | null = null;

  constructor(private route: ActivatedRoute, private router: Router, private registerApi: RegisterApiService) {}

  ngOnInit(): void {
    this.sessionId = Number(this.route.snapshot.paramMap.get('id'));
    this.registerApi.getSession(this.sessionId).subscribe((s) => (this.session = s));
    this.registerApi.getSessionReport(this.sessionId).subscribe((r) => (this.report = r));
  }

  submit(): void {
    this.error = null;
    this.registerApi.closeSession(this.sessionId, this.countedCash).subscribe({
      // Task: "Add print session report (Z-report) same we print receipt" — auto-opens the print
      // dialog on arrival, same ?autoPrint=1 convention checkout/return-form already use.
      next: () => this.router.navigate(['/sessions', this.sessionId, 'report'], { queryParams: { autoPrint: '1' } }),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to close session'),
    });
  }
}
