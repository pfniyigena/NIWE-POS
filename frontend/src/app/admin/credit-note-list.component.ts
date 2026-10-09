import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { InvoiceApiService } from '../core/api/invoice-api.service';
import { Invoice } from '../core/models';
import { openPdfForPrint } from '../core/download-file';

/**
 * Admin > Sales > Credit notes — Fourier: "Add credit note Menu (Void) Where we can printout them".
 * Every voided invoice, store-wide, newest-voided first. A credit note is not a separate document
 * type in this app: it's the same Invoice entity once it reaches VOID, carrying its own "CN-" number
 * (see the backend Invoice.creditNoteNumber's javadoc) stamped at void time. "Print" opens that
 * credit note's own PDF in a new tab, same pattern as the Invoice detail screen's own "Print" button.
 */
@Component({
  selector: 'app-credit-note-list',
  standalone: true,
  imports: [CommonModule, RouterLink, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3 style="margin-bottom:0;">Credit notes</h3>
      </div>
      <table>
        <thead>
          <tr>
            <th>Credit note #</th>
            <th>Invoice #</th>
            <th>Customer</th>
            <th>Voided</th>
            <th>Reason</th>
            <th class="text-end">Amount</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let inv of creditNotes">
            <td>{{ inv.creditNoteNumber }}</td>
            <td>{{ inv.invoiceNumber }}</td>
            <td>{{ inv.customerName }}</td>
            <td>{{ inv.voidedAt | date: 'medium' }}</td>
            <td>{{ inv.voidReason || '—' }}</td>
            <td class="text-end">{{ inv.totalAmount | number: '1.2-2' }}</td>
            <td>
              <a class="btn btn-sm btn-outline-primary" [routerLink]="['/admin/invoices', inv.id]">View invoice</a>
              <button class="btn btn-sm btn-outline-secondary" (click)="print(inv)" [disabled]="printingId === inv.id">Print</button>
            </td>
          </tr>
          <tr *ngIf="creditNotes.length === 0">
            <td colspan="7" class="text-muted">No credit notes yet — voiding an invoice issues one automatically.</td>
          </tr>
        </tbody>
      </table>
    </app-shell>
  `,
})
export class CreditNoteListComponent implements OnInit {
  creditNotes: Invoice[] = [];
  error: string | null = null;
  printingId: number | null = null;

  constructor(private invoiceApi: InvoiceApiService) {}

  ngOnInit(): void {
    this.invoiceApi.listCreditNotes().subscribe({
      next: (list) => (this.creditNotes = list),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load credit notes'),
    });
  }

  print(inv: Invoice): void {
    this.printingId = inv.id;
    this.invoiceApi.creditNotePdf(inv.id).subscribe({
      next: (blob) => {
        this.printingId = null;
        openPdfForPrint(blob);
      },
      error: (err) => {
        this.printingId = null;
        this.error = err?.error?.message ?? 'Failed to open credit note PDF';
      },
    });
  }
}
