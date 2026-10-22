import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { BackupApiService, BackupFile } from '../core/api/backup-api.service';
import { DataImportExportApiService, ImportKind, ImportResult } from '../core/api/data-import-export-api.service';
import { downloadBlob } from '../core/download-file';

interface ImportInfo {
  kind: ImportKind;
  label: string;
  help: string;
}

interface ImportGroup {
  title: string;
  intro: string;
  items: ImportInfo[];
}

const IMPORT_GROUPS: ImportGroup[] = [
  {
    title: 'Lists',
    intro: 'Load your suppliers, categories, brands, products and customers. Do these first, because the balances below refer to them by name.',
    items: [
      { kind: 'suppliers', label: 'Suppliers', help: 'A supplier with the same name and phone is skipped, not duplicated.' },
      { kind: 'categories', label: 'Categories', help: 'Name and an optional colour like #3366cc.' },
      { kind: 'brands', label: 'Brands', help: 'One brand name per row.' },
      { kind: 'products', label: 'Products', help: 'New categories and brands named in the file are created for you; the check tells you which.' },
      { kind: 'customers', label: 'Customers', help: 'A customer already on file is skipped.' },
    ],
  },
  {
    title: 'Opening position',
    intro: 'What you had on the day you started using the system. Each balance is dated, posted against Owner\'s Equity (so it does not count as sales or purchases), and then collected or paid through the normal screens.',
    items: [
      { kind: 'opening-stock', label: 'Opening stock', help: 'Quantities already on the shelf.' },
      { kind: 'customer-balances', label: 'What customers owe you', help: 'One row per customer, with the date it is owed as of. The customer must already exist. Only one opening balance per customer.' },
      { kind: 'supplier-balances', label: 'What you owe suppliers', help: 'One row per supplier, with the date it is owed as of. The supplier must already exist. Only one opening balance per supplier.' },
      { kind: 'ledger-balances', label: 'Ledger opening balances', help: 'Cash and bank, equipment, loans, owner capital and so on: one row per account code with a debit or a credit, all with the same date. Posted together as one balanced entry, so every row must be right or nothing is imported. Receivables and payables come from the two files above.' },
    ],
  },
];

const ALL_KINDS: ImportKind[] = ['products', 'customers', 'suppliers', 'categories', 'brands', 'opening-stock', 'customer-balances', 'supplier-balances', 'ledger-balances'];

function emptyRecord<T>(value: T): Record<ImportKind, T> {
  const r = {} as Record<ImportKind, T>;
  for (const k of ALL_KINDS) r[k] = value;
  return r;
}

/**
 * Admin > Data (Fourier: "no backup/restore or bulk import-export anywhere in the backend ...
 * 'what if the server dies' and 'I need to bulk-load my existing product list' are both
 * unanswered today"). One screen, three sections — Backup & Restore, Bulk import, Export &
 * year-end archive — same "one entry point for a cluster of related admin actions" shape Reports
 * and Accounting > Reports already use, rather than a separate sidebar link per action.
 *
 * Restoring is the one genuinely destructive action on this screen — it replaces the *entire*
 * current database — so both restore paths go through a strongly-worded confirm() first, same
 * convention Stock Adjustment confirm/reject already use, and the UI calls out that restarting
 * the app afterward is recommended (see BackupService's own javadoc for why).
 */
@Component({
  selector: 'app-data-management',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Data</h3>

      <!-- ---- Backup & Restore ---- -->
      <div class="card" style="padding:1rem; margin-bottom:1.5rem;">
        <h4 class="mb-2" style="font-size:1.05rem;">Backup &amp; restore</h4>
        <p class="text-muted" style="font-size:0.85rem;">
          A full database backup runs automatically every night; you can also trigger one now. Restoring
          replaces the <strong>entire</strong> current database with the chosen backup. While it runs, the
          whole system goes into maintenance mode — everyone else (cashiers, other admins) sees a "please
          wait" screen until it's done. Restart the app afterward for a clean start.
        </p>
        <div class="alert alert-danger" *ngIf="backupError">{{ backupError }}</div>
        <div class="alert alert-success" *ngIf="backupMessage">{{ backupMessage }}</div>

        <button class="btn btn-primary mb-3" (click)="createBackup()" [disabled]="backingUp">
          {{ backingUp ? 'Backing up…' : 'Backup now' }}
        </button>

        <table *ngIf="backups.length > 0">
          <thead>
            <tr>
              <th>File</th>
              <th>Size</th>
              <th>Created</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let b of backups">
              <td>{{ b.filename }}</td>
              <td>{{ formatSize(b.sizeBytes) }}</td>
              <td>{{ b.createdAt | date: 'medium' }}</td>
              <td class="flex gap-2">
                <button class="btn btn-sm btn-outline-secondary" (click)="download(b)">Download</button>
                <button class="btn btn-sm btn-outline-danger" [disabled]="restoring" (click)="restoreFromStored(b)">Restore</button>
                <button class="btn btn-sm btn-outline-danger" [disabled]="restoring" (click)="deleteBackup(b)">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p *ngIf="backups.length === 0 && backupsLoaded" class="text-muted">No backups yet.</p>

        <div class="form-group" style="margin-top:1rem;">
          <label class="form-label">Restore from an uploaded backup file</label>
          <div class="flex gap-2">
            <input type="file" accept=".sql" (change)="onRestoreFileSelected($event)" />
            <button class="btn btn-outline-danger" [disabled]="!restoreUploadFile || restoring" (click)="restoreFromUpload()">
              {{ restoring ? 'Restoring…' : 'Restore' }}
            </button>
          </div>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">
            For a backup downloaded earlier, or migrated from another machine — not needed for one already listed above.
          </div>
        </div>
      </div>

      <!-- ---- Bulk import ---- -->
      <div class="card" style="padding:1rem; margin-bottom:1.5rem;">
        <h4 class="mb-2" style="font-size:1.05rem;">Bulk import</h4>
        <p class="text-muted" style="font-size:0.85rem;">
          Load data from a CSV file. Press <strong>Check file</strong> first: it shows exactly what would happen and saves nothing.
          Then press <strong>Import</strong> to do it. A row that cannot be used is skipped and listed with the reason; the rest of the file still imports.
        </p>

        <div *ngFor="let group of importGroups" style="margin-top:1rem;">
          <h5 style="font-size:0.95rem; margin-bottom:0.25rem;">{{ group.title }}</h5>
          <p class="text-muted" style="font-size:0.8rem;">{{ group.intro }}</p>

          <div *ngFor="let info of group.items" class="form-group imp-block" [attr.data-kind]="info.kind" style="border-top:1px solid var(--border-color, #ddd); padding-top:0.75rem;">
            <label class="form-label">{{ info.label }}</label>
            <div class="text-muted" style="font-size:0.8rem;">{{ info.help }}</div>
            <div class="flex gap-2" style="margin-top:0.25rem;">
              <button type="button" class="btn btn-sm btn-outline-secondary" (click)="downloadTemplate(info.kind)">Download template</button>
            </div>
            <label *ngIf="info.kind === 'ledger-balances'" class="imp-equity" style="display:flex; gap:0.4rem; align-items:center; margin-top:0.4rem; font-size:0.85rem;">
              <input type="checkbox" [(ngModel)]="balanceToEquity" name="balanceToEquity" /> Put any difference in Owner's Equity
            </label>
            <div class="flex gap-2" style="margin-top:0.5rem; flex-wrap:wrap;">
              <input type="file" accept=".csv" [id]="'imp-file-' + info.kind" (change)="onImportFileSelected($event, info.kind)" />
              <button class="btn btn-outline-primary imp-check" [disabled]="!importFiles[info.kind] || importing[info.kind]" (click)="check(info.kind)">
                {{ importing[info.kind] ? 'Working…' : 'Check file' }}
              </button>
            </div>
            <div class="alert alert-danger" *ngIf="importErrors[info.kind]" style="margin-top:0.5rem;">{{ importErrors[info.kind] }}</div>

            <div *ngIf="importResults[info.kind] as res" class="imp-result" style="margin-top:0.5rem;">
              <div class="alert alert-info" *ngIf="res.dryRun">
                <strong>Nothing saved yet.</strong>
                {{ res.total }} row(s) read: {{ res.imported }} can be imported, {{ res.skipped }} will be skipped.
              </div>
              <div class="alert alert-success" *ngIf="!res.dryRun">
                Imported {{ res.imported }}, skipped {{ res.skipped }}.
              </div>
              <div *ngFor="let note of res.notes" class="text-muted" style="font-size:0.85rem;">{{ note }}</div>

              <div *ngIf="res.skippedRows.length > 0" style="margin-top:0.5rem;">
                <div style="font-weight:600; font-size:0.85rem;">Rows that will be skipped</div>
                <table>
                  <thead><tr><th>Row</th><th>Reason</th></tr></thead>
                  <tbody>
                    <tr *ngFor="let r of res.skippedRows">
                      <td [attr.data-label]="'Row'">{{ r.rowNumber }}</td>
                      <td [attr.data-label]="'Reason'">{{ r.reason }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div *ngIf="res.dryRun && res.accepted && res.accepted.length > 0" style="margin-top:0.5rem;">
                <div style="font-weight:600; font-size:0.85rem;">
                  What will be created
                  <span class="text-muted" *ngIf="res.imported > res.accepted.length">(first {{ res.accepted.length }} of {{ res.imported }})</span>
                </div>
                <table>
                  <thead><tr><th>Row</th><th>Will be created</th></tr></thead>
                  <tbody>
                    <tr *ngFor="let r of res.accepted">
                      <td [attr.data-label]="'Row'">{{ r.rowNumber }}</td>
                      <td [attr.data-label]="'Will be created'">{{ r.reason }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div class="flex gap-2" style="margin-top:0.5rem;" *ngIf="res.dryRun">
                <button class="btn btn-primary imp-confirm" [disabled]="res.imported === 0 || importing[info.kind]" (click)="confirmImport(info.kind)">
                  {{ importing[info.kind] ? 'Importing…' : 'Import ' + res.imported + ' row(s)' }}
                </button>
                <button class="btn btn-outline-secondary" (click)="clearImport(info.kind)">Cancel</button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- ---- Export & year-end archive ---- -->
      <div class="card" style="padding:1rem;">
        <h4 class="mb-2" style="font-size:1.05rem;">Export &amp; year-end archive</h4>
        <p class="text-muted" style="font-size:0.85rem;">
          Export the current product/customer lists, or a year-end archive of that year's business activity
          (sales, purchases, payments, stock adjustments) — a curated snapshot for closing out a financial
          year, not a full database backup.
        </p>
        <div class="flex gap-2 mb-3">
          <button class="btn btn-outline-primary" (click)="exportProducts()">Export products</button>
          <button class="btn btn-outline-primary" (click)="exportCustomers()">Export customers</button>
        </div>
        <div class="form-group">
          <label class="form-label">Year-end archive</label>
          <div class="flex gap-2">
            <input class="form-control" type="number" style="max-width:120px;" [(ngModel)]="archiveYear" />
            <button class="btn btn-outline-primary" [disabled]="archiving" (click)="exportYearEnd()">
              {{ archiving ? 'Building…' : 'Export archive' }}
            </button>
          </div>
        </div>
      </div>
    </app-shell>
  `,
})
export class DataManagementComponent implements OnInit {
  // ---- Backup & restore ----
  backups: BackupFile[] = [];
  backupsLoaded = false;
  backingUp = false;
  restoring = false;
  backupError: string | null = null;
  backupMessage: string | null = null;
  restoreUploadFile: File | null = null;

  // ---- Bulk import ----
  importGroups = IMPORT_GROUPS;
  balanceToEquity = false;
  importFiles: Record<ImportKind, File | null> = emptyRecord<File | null>(null);
  importing: Record<ImportKind, boolean> = emptyRecord(false);
  importResults: Record<ImportKind, ImportResult | null> = emptyRecord<ImportResult | null>(null);
  importErrors: Record<ImportKind, string | null> = emptyRecord<string | null>(null);

  // ---- Export & year-end archive ----
  archiveYear = new Date().getFullYear();
  archiving = false;

  constructor(private backupApi: BackupApiService, private dataApi: DataImportExportApiService) {}

  ngOnInit(): void {
    this.loadBackups();
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  // ---- Backup & restore ----

  loadBackups(): void {
    this.backupApi.list().subscribe({
      next: (b) => {
        this.backups = b;
        this.backupsLoaded = true;
      },
      error: (err) => (this.backupError = err?.error?.message ?? 'Failed to load backups'),
    });
  }

  createBackup(): void {
    this.backupError = null;
    this.backupMessage = null;
    this.backingUp = true;
    this.backupApi.createNow().subscribe({
      next: (b) => {
        this.backingUp = false;
        this.backupMessage = `Backup ${b.filename} created.`;
        this.loadBackups();
      },
      error: (err) => {
        this.backingUp = false;
        this.backupError = err?.error?.message ?? 'Failed to create backup';
      },
    });
  }

  download(b: BackupFile): void {
    this.backupApi.download(b.filename).subscribe({
      next: (blob) => downloadBlob(blob, b.filename),
      error: (err) => (this.backupError = err?.error?.message ?? 'Failed to download backup'),
    });
  }

  restoreFromStored(b: BackupFile): void {
    if (
      !confirm(
        `This will ERASE the current database and replace it with "${b.filename}". This cannot be undone.\n\n` +
          `Everyone else will be locked out with a "please wait" screen until this finishes — then restart the app. Continue?`
      )
    )
      return;
    this.backupError = null;
    this.backupMessage = null;
    this.restoring = true;
    this.backupApi.restoreFromStored(b.filename).subscribe({
      next: () => {
        this.restoring = false;
        this.backupMessage = `Restored from ${b.filename}. Restart the app for a clean start.`;
      },
      error: (err) => {
        this.restoring = false;
        this.backupError = err?.error?.message ?? 'Failed to restore backup';
      },
    });
  }

  deleteBackup(b: BackupFile): void {
    if (!confirm(`Delete backup "${b.filename}"? This cannot be undone.`)) return;
    this.backupError = null;
    this.backupApi.delete(b.filename).subscribe({
      next: () => this.loadBackups(),
      error: (err) => (this.backupError = err?.error?.message ?? 'Failed to delete backup'),
    });
  }

  onRestoreFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.restoreUploadFile = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  restoreFromUpload(): void {
    if (!this.restoreUploadFile) return;
    if (
      !confirm(
        `This will ERASE the current database and replace it with the uploaded file "${this.restoreUploadFile.name}". This cannot be undone.\n\n` +
          `Everyone else will be locked out with a "please wait" screen until this finishes — then restart the app. Continue?`
      )
    )
      return;
    this.backupError = null;
    this.backupMessage = null;
    this.restoring = true;
    this.backupApi.restoreFromUpload(this.restoreUploadFile).subscribe({
      next: () => {
        this.restoring = false;
        this.backupMessage = 'Restored from the uploaded file. Restart the app for a clean start.';
        this.restoreUploadFile = null;
      },
      error: (err) => {
        this.restoring = false;
        this.backupError = err?.error?.message ?? 'Failed to restore from the uploaded file';
      },
    });
  }

  // ---- Bulk import ----

  downloadTemplate(kind: ImportKind): void {
    this.dataApi.downloadTemplate(kind).subscribe({ next: (blob) => downloadBlob(blob, `${kind}-import-template.csv`) });
  }

  onImportFileSelected(event: Event, kind: ImportKind): void {
    const input = event.target as HTMLInputElement;
    this.importFiles[kind] = input.files && input.files.length > 0 ? input.files[0] : null;
    this.importResults[kind] = null;
    this.importErrors[kind] = null;
  }

  /** Step 1: read the file and show what would happen. Nothing is saved. */
  check(kind: ImportKind): void {
    this.run(kind, true);
  }

  /** Step 2: do it for real (the file is read again, so the result matches what the check showed unless the data changed in between). */
  confirmImport(kind: ImportKind): void {
    this.run(kind, false);
  }

  clearImport(kind: ImportKind): void {
    this.importResults[kind] = null;
    this.importErrors[kind] = null;
  }

  private run(kind: ImportKind, dryRun: boolean): void {
    const file = this.importFiles[kind];
    if (!file) return;
    this.importErrors[kind] = null;
    this.importing[kind] = true;
    this.dataApi.importFile(kind, file, dryRun, kind === 'ledger-balances' && this.balanceToEquity).subscribe({
      next: (result) => {
        this.importing[kind] = false;
        this.importResults[kind] = result;
        if (!dryRun) {
          this.importFiles[kind] = null;
          const el = document.getElementById('imp-file-' + kind) as HTMLInputElement | null;
          if (el) el.value = '';
        }
      },
      error: (err) => {
        this.importing[kind] = false;
        this.importResults[kind] = null;
        this.importErrors[kind] = err?.error?.message ?? (dryRun ? 'Could not check the file' : 'Import failed');
      },
    });
  }

  // ---- Export & year-end archive ----

  exportProducts(): void {
    this.dataApi.exportProducts().subscribe({ next: (blob) => downloadBlob(blob, 'products.xlsx') });
  }

  exportCustomers(): void {
    this.dataApi.exportCustomers().subscribe({ next: (blob) => downloadBlob(blob, 'customers.xlsx') });
  }

  exportYearEnd(): void {
    this.archiving = true;
    this.dataApi.exportYearEnd(this.archiveYear).subscribe({
      next: (blob) => {
        this.archiving = false;
        downloadBlob(blob, `year-end-${this.archiveYear}.zip`);
      },
      error: () => {
        this.archiving = false;
      },
    });
  }
}
