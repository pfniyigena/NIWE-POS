import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { BackupApiService, BackupFile } from '../core/api/backup-api.service';
import { DataImportExportApiService, ImportResult } from '../core/api/data-import-export-api.service';
import { downloadBlob } from '../core/download-file';

type ImportKind = 'products' | 'customers' | 'opening-stock';

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
          Load products, customers, or opening stock quantities from a CSV file. A row that conflicts with
          existing data is skipped and reported below — the rest of the file still imports.
        </p>

        <div *ngFor="let kind of importKinds" class="form-group" style="border-top:1px solid var(--border-color, #ddd); padding-top:0.75rem;">
          <label class="form-label">{{ importLabel(kind) }}</label>
          <div class="flex gap-2">
            <button type="button" class="btn btn-sm btn-outline-secondary" (click)="downloadTemplate(kind)">Download template</button>
          </div>
          <div class="flex gap-2" style="margin-top:0.5rem;">
            <input type="file" accept=".csv" (change)="onImportFileSelected($event, kind)" />
            <button class="btn btn-outline-primary" [disabled]="!importFiles[kind] || importing[kind]" (click)="runImport(kind)">
              {{ importing[kind] ? 'Importing…' : 'Import' }}
            </button>
          </div>
          <div class="alert alert-danger" *ngIf="importErrors[kind]" style="margin-top:0.5rem;">{{ importErrors[kind] }}</div>
          <div class="alert alert-success" *ngIf="importResults[kind]" style="margin-top:0.5rem;">
            Imported {{ importResults[kind]!.imported }}, skipped {{ importResults[kind]!.skipped }}.
          </div>
          <table *ngIf="importResults[kind] && importResults[kind]!.skippedRows.length > 0" style="margin-top:0.5rem;">
            <thead>
              <tr>
                <th>Row</th>
                <th>Reason</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let r of importResults[kind]!.skippedRows">
                <td>{{ r.rowNumber }}</td>
                <td>{{ r.reason }}</td>
              </tr>
            </tbody>
          </table>
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
  importKinds: ImportKind[] = ['products', 'customers', 'opening-stock'];
  importFiles: Record<ImportKind, File | null> = { products: null, customers: null, 'opening-stock': null };
  importing: Record<ImportKind, boolean> = { products: false, customers: false, 'opening-stock': false };
  importResults: Record<ImportKind, ImportResult | null> = { products: null, customers: null, 'opening-stock': null };
  importErrors: Record<ImportKind, string | null> = { products: null, customers: null, 'opening-stock': null };

  // ---- Export & year-end archive ----
  archiveYear = new Date().getFullYear();
  archiving = false;

  constructor(private backupApi: BackupApiService, private dataApi: DataImportExportApiService) {}

  ngOnInit(): void {
    this.loadBackups();
  }

  importLabel(kind: ImportKind): string {
    switch (kind) {
      case 'products':
        return 'Products';
      case 'customers':
        return 'Customers';
      case 'opening-stock':
        return 'Opening stock';
    }
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

  /** Fourier: "a button to download a template to upload" — a filled-in example CSV with the exact columns that importer expects. */
  downloadTemplate(kind: ImportKind): void {
    const call =
      kind === 'products'
        ? this.dataApi.downloadProductsTemplate()
        : kind === 'customers'
        ? this.dataApi.downloadCustomersTemplate()
        : this.dataApi.downloadOpeningStockTemplate();
    call.subscribe({ next: (blob) => downloadBlob(blob, `${kind}-import-template.csv`) });
  }

  onImportFileSelected(event: Event, kind: ImportKind): void {
    const input = event.target as HTMLInputElement;
    this.importFiles[kind] = input.files && input.files.length > 0 ? input.files[0] : null;
  }

  runImport(kind: ImportKind): void {
    const file = this.importFiles[kind];
    if (!file) return;
    this.importErrors[kind] = null;
    this.importResults[kind] = null;
    this.importing[kind] = true;
    const call =
      kind === 'products'
        ? this.dataApi.importProducts(file)
        : kind === 'customers'
        ? this.dataApi.importCustomers(file)
        : this.dataApi.importOpeningStock(file);
    call.subscribe({
      next: (result) => {
        this.importing[kind] = false;
        this.importResults[kind] = result;
      },
      error: (err) => {
        this.importing[kind] = false;
        this.importErrors[kind] = err?.error?.message ?? 'Import failed';
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
