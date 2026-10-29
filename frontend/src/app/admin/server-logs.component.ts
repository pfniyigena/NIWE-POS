import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { ServerLogApiService, ServerLogFile, ServerLogTail } from '../core/api/server-log-api.service';
import { downloadBlob } from '../core/download-file';

/**
 * v1.14.0: Admin > Server logs — read the end of the server's own log files without logging in to the server: the application log,
 * the errors-only log and the request (access) log. Filter by level or text, auto-refresh, or download the whole file.
 */
@Component({
  selector: 'app-server-logs',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>Server logs</h3>
      </div>

      <p class="text-muted" *ngIf="!filesLoading && files.length === 0">No log files yet. They appear once the server has written something.</p>

      <div class="flex gap-2 mb-3" style="align-items:center; flex-wrap:wrap;" *ngIf="files.length > 0">
        <select class="form-select" style="max-width:260px;" [(ngModel)]="selected" (ngModelChange)="onFileChange()" data-testid="log-file">
          <option *ngFor="let f of files" [ngValue]="f.name">{{ f.name }} ({{ sizeText(f.size) }})</option>
        </select>
        <select class="form-select" style="max-width:170px;" [(ngModel)]="level" (ngModelChange)="load()" data-testid="log-level">
          <option value="">All levels</option>
          <option value="INFO">Info and above</option>
          <option value="WARN">Warnings and errors</option>
          <option value="ERROR">Errors only</option>
        </select>
        <input class="form-control" style="max-width:240px;" placeholder="Search the text" [(ngModel)]="search" (input)="onSearchChange()" />
        <select class="form-select" style="max-width:150px;" [(ngModel)]="lines" (ngModelChange)="load()">
          <option [ngValue]="100">Last 100</option>
          <option [ngValue]="300">Last 300</option>
          <option [ngValue]="1000">Last 1000</option>
          <option [ngValue]="2000">Last 2000</option>
        </select>
        <label class="flex gap-1" style="align-items:center; font-size:0.85rem;">
          <input type="checkbox" [(ngModel)]="auto" (ngModelChange)="onAutoChange()" /> Auto-refresh
        </label>
        <button class="btn btn-sm btn-outline-secondary" (click)="load()">Refresh</button>
        <button class="btn btn-sm btn-primary" (click)="download()" [disabled]="!selected" data-testid="log-download">Download file</button>
      </div>

      <div class="text-muted mb-2" style="font-size:0.85rem;" *ngIf="tail">
        {{ tail.matched | number }} entr{{ tail.matched === 1 ? 'y' : 'ies' }} shown from the end of {{ tail.file }}
        ({{ sizeText(tail.size) }}, last written {{ tail.modified | date: 'yyyy-MM-dd HH:mm:ss' }}).
        <span *ngIf="tail.truncated"> Only the last part of a large file is read; download it for everything.</span>
      </div>

      <div class="card" style="padding:0; overflow:auto; max-height:70vh;" *ngIf="tail">
        <table style="font-family:monospace; font-size:0.78rem;">
          <tbody>
            <tr *ngFor="let e of tail.entries">
              <td style="white-space:nowrap; vertical-align:top;">{{ e.time }}</td>
              <td style="vertical-align:top;"><span class="badge" [ngClass]="badge(e.level)">{{ e.level }}</span></td>
              <td style="white-space:pre-wrap; word-break:break-word;">{{ body(e.text) }}</td>
            </tr>
            <tr *ngIf="tail.entries.length === 0"><td class="text-muted" colspan="3">Nothing matches.</td></tr>
          </tbody>
        </table>
      </div>
    </app-shell>
  `,
})
export class ServerLogsComponent implements OnInit, OnDestroy {
  files: ServerLogFile[] = [];
  filesLoading = true;
  selected = '';
  level = '';
  search = '';
  lines = 300;
  auto = false;
  tail: ServerLogTail | null = null;
  error: string | null = null;

  private timer: ReturnType<typeof setInterval> | null = null;
  private searchDebounce: ReturnType<typeof setTimeout> | null = null;

  constructor(private api: ServerLogApiService) {}

  ngOnInit(): void {
    this.api.files().subscribe({
      next: (f) => {
        this.files = f;
        this.filesLoading = false;
        const preferred = f.find((x) => x.name === 'niwe-erp.log') ?? f[0];
        if (preferred) {
          this.selected = preferred.name;
          this.load();
        }
      },
      error: (err) => {
        this.filesLoading = false;
        this.error = err?.error?.message ?? 'Failed to list the log files';
      },
    });
  }

  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
  }

  load(): void {
    if (!this.selected) return;
    this.error = null;
    this.api.tail(this.selected, this.lines, this.level, this.search).subscribe({
      next: (t) => (this.tail = t),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to read the log file'),
    });
  }

  onFileChange(): void {
    this.tail = null;
    this.load();
  }

  onSearchChange(): void {
    if (this.searchDebounce) clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => this.load(), 400);
  }

  onAutoChange(): void {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (this.auto) this.timer = setInterval(() => this.load(), 5000);
  }

  download(): void {
    if (!this.selected) return;
    const name = this.selected;
    this.api.download(name).subscribe({
      next: (blob) => downloadBlob(blob, name),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to download the log file'),
    });
  }

  badge(level: string | null): string {
    switch (level) {
      case 'ERROR': return 'badge-danger';
      case 'WARN': return 'badge-warning';
      default: return 'badge-secondary';
    }
  }

  /** The time and level have their own columns; show the rest of the first line (and any stack-trace lines after it). */
  body(text: string): string {
    return text.replace(/^\d{4}-\d\d-\d\d \d\d:\d\d:\d\d(\.\d+)?\s+[A-Z]+\s+/, '');
  }

  sizeText(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }
}
