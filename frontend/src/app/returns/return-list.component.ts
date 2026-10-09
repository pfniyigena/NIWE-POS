import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { ReturnsApiService } from '../core/api/returns-api.service';
import { SalesReturn } from '../core/models';

@Component({
  selector: 'app-return-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>Returns</h3>
        <a class="btn btn-primary" routerLink="/returns/new">+ Process a return</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>Return date</th>
            <th>Invoice</th>
            <th>Refund total</th>
            <th>Method</th>
            <th>Reason</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of returns">
            <td>{{ r.returnDate | date: 'yyyy-MM-dd HH:mm' }}</td>
            <td>{{ r.invoiceNumber }}</td>
            <td>{{ r.refundTotal | number: '1.2-2' }}</td>
            <td>{{ r.refundMethod }}</td>
            <td>{{ r.reason }}</td>
            <td>
              <!-- Task: "print out the return" — reprint later, no autoPrint here (manual, like the Receipts list). -->
              <a class="btn btn-sm btn-outline-secondary" [routerLink]="['/returns', r.id, 'receipt']">Print</a>
            </td>
          </tr>
          <tr *ngIf="returns.length === 0">
            <td colspan="6" class="text-muted">No returns processed yet.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class ReturnListComponent implements OnInit {
  returns: SalesReturn[] = [];

  constructor(private returnsApi: ReturnsApiService) {}

  ngOnInit(): void {
    this.returnsApi.list().subscribe((r) => (this.returns = r));
  }
}
