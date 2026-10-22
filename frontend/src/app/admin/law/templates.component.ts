import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../../shared/shell.component';
import { AuthService } from '../../core/auth.service';
import { DOC_CATEGORY_LABEL, DocCategory, DocPlaceholder, DocTemplate, LawApiService, TemplateKind } from '../../core/api/law-api.service';
import { downloadBlob } from '../../core/download-file';

/**
 * Law Firm > Templates: the letters and contracts a matter's documents can be made from. A template is either typed text or a Word (.docx)
 * file; {{placeholders}} such as {{client.name}} or {{matter.number}} are replaced with the matter's details when a document is made.
 * Everyone in Operations can see them; an admin or an Operations manager changes them (the server enforces it).
 */
@Component({
  selector: 'app-law-templates',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell [wide]="true">
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>Document templates</h3>
        <button class="btn btn-primary" *ngIf="canManage" (click)="startNew()">New template</button>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="info">{{ info }}</div>

      <form class="card mb-3" style="padding:1rem;" *ngIf="f" (ngSubmit)="save()">
        <h4 class="mb-2">{{ f.id ? 'Change the template' : 'New template' }}</h4>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group" style="flex:1; min-width:14rem;"><label class="form-label">Name</label><input class="form-control" name="n" [(ngModel)]="f.name" /></div>
          <div class="form-group" style="min-width:12rem;"><label class="form-label">Kind of document</label>
            <select class="form-select" name="c" [(ngModel)]="f.category"><option *ngFor="let c of categories" [value]="c.key">{{ c.label }}</option></select></div>
          <div class="form-group" style="min-width:12rem;"><label class="form-label">Made of</label>
            <select class="form-select" name="k" [(ngModel)]="f.kind" [disabled]="!!f.id"><option value="TEXT">Typed text</option><option value="DOCX">A Word file</option></select></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">Description</label><input class="form-control" name="d" [(ngModel)]="f.description" /></div>
        <div class="form-group mb-2" *ngIf="f.kind === 'TEXT'"><label class="form-label">Text <span class="text-muted">(use the placeholders listed below)</span></label>
          <textarea class="form-control" rows="12" name="b" [(ngModel)]="f.body"></textarea></div>
        <div class="mb-2" *ngIf="f.kind === 'DOCX'">
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="!f.id">Save the template first, then upload its Word file.</div>
          <div *ngIf="f.id">
            <label class="form-label">Word file (.docx) <span class="text-muted" *ngIf="f.fileName">· current: {{ f.fileName }}</span></label>
            <input class="form-control" type="file" accept=".docx" (change)="onFile($event)" />
            <div class="text-muted mt-1" style="font-size:0.8rem;">Type the placeholders in the Word file as normal text, for example <span ngNonBindable>{{client.name}}</span>. They work in the body, tables, headers and footers.</div>
          </div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-2"><input type="checkbox" name="a" [(ngModel)]="f.active" /> In use (people can choose it when making a document)</label>
        <div class="flex gap-2"><button class="btn btn-primary" type="submit" [disabled]="busy">Save</button><button class="btn btn-outline-secondary" type="button" (click)="f = null">Close</button></div>
      </form>

      <div class="card mb-3" style="padding:1rem;">
        <table class="stack table-sm" *ngIf="rows.length">
          <thead><tr><th>Template</th><th>Kind</th><th>Made of</th><th>Used</th><th>Status</th><th></th></tr></thead>
          <tbody>
            <tr *ngFor="let t of rows">
              <td data-label="Template"><strong>{{ t.name }}</strong><div class="text-muted" style="font-size:0.8rem;">{{ t.description }}<span *ngIf="t.kind === 'DOCX' && !t.fileName"> · no Word file uploaded yet</span></div></td>
              <td data-label="Kind">{{ catLabel(t.category) }}</td>
              <td data-label="Made of">{{ t.kind === 'DOCX' ? 'Word file' : 'Typed text' }}</td>
              <td data-label="Used">{{ t.uses }}</td>
              <td data-label="Status">{{ t.active ? 'In use' : 'Switched off' }}</td>
              <td style="white-space:nowrap;">
                <button class="btn btn-sm btn-outline-secondary" *ngIf="t.kind === 'DOCX' && t.fileName" (click)="downloadFile(t)">Download file</button>
                <button class="btn btn-sm btn-outline-secondary" *ngIf="canManage" (click)="startEdit(t)">Change</button>
                <button class="btn btn-sm btn-outline-danger" *ngIf="canManage && !t.uses" (click)="remove(t)">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="text-muted" *ngIf="!rows.length && loaded">No templates yet.<span *ngIf="canManage"> Use "New template" to add the first one.</span></div>
        <div class="text-muted mt-2" style="font-size:0.8rem;" *ngIf="!canManage">Only an admin or an Operations manager can change templates.</div>
      </div>

      <div class="card" style="padding:1rem;">
        <h4 class="mb-2">Placeholders you can use</h4>
        <div class="text-muted mb-2" style="font-size:0.85rem;">Write them with double curly brackets. Anything not on this list is left as written and reported when the document is made.</div>
        <table class="table-sm"><tbody>
          <tr *ngFor="let p of placeholders"><td><code>{{ ph(p.key) }}</code></td><td>{{ p.description }}</td></tr>
        </tbody></table>
      </div>
    </app-shell>
  `,
})
export class LawTemplatesComponent implements OnInit {
  rows: DocTemplate[] = [];
  placeholders: DocPlaceholder[] = [];
  categories = Object.entries(DOC_CATEGORY_LABEL).map(([key, label]) => ({ key, label }));
  f: { id: number | null; name: string; category: DocCategory; description: string; kind: TemplateKind; body: string; active: boolean; fileName: string | null } | null = null;
  error: string | null = null;
  info: string | null = null;
  busy = false;
  loaded = false;
  canManage = false;

  constructor(private api: LawApiService, private auth: AuthService) {}

  ngOnInit(): void {
    this.canManage = this.auth.canManage('OPERATIONS');
    this.api.docPlaceholders().subscribe((p) => (this.placeholders = p));
    this.load();
  }

  load(): void {
    this.api.docTemplates(false).subscribe({
      next: (r) => { this.rows = r; this.loaded = true; },
      error: (e) => (this.error = e?.error?.message ?? 'Could not load the templates'),
    });
  }

  catLabel(c: DocCategory): string { return DOC_CATEGORY_LABEL[c] ?? c; }

  ph(key: string): string { return '{' + '{' + key + '}' + '}'; }

  startNew(): void { this.f = { id: null, name: '', category: 'CORRESPONDENCE', description: '', kind: 'TEXT', body: '', active: true, fileName: null }; this.info = null; this.error = null; }

  startEdit(t: DocTemplate): void {
    this.f = { id: t.id, name: t.name, category: t.category, description: t.description ?? '', kind: t.kind, body: t.body ?? '', active: t.active, fileName: t.fileName };
    this.info = null;
    this.error = null;
  }

  save(): void {
    if (!this.f) return;
    const f = this.f;
    this.busy = true;
    this.error = null;
    this.info = null;
    this.api.saveDocTemplate(f.id, { name: f.name, category: f.category, description: f.description.trim() || null, kind: f.kind, body: f.kind === 'TEXT' ? f.body : null, active: f.active }).subscribe({
      next: (t) => {
        this.busy = false;
        this.info = f.kind === 'DOCX' && !f.id ? 'Saved. Now upload its Word file.' : 'Saved.';
        this.f = f.kind === 'DOCX' ? { ...f, id: t.id, fileName: t.fileName } : null;
        this.load();
      },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; },
    });
  }

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.f?.id) return;
    const fd = new FormData();
    fd.append('file', file);
    this.busy = true;
    this.error = null;
    this.api.uploadTemplateFile(this.f.id, fd).subscribe({
      next: (t) => { this.busy = false; this.info = 'Word file uploaded.'; if (this.f) this.f.fileName = t.fileName; input.value = ''; this.load(); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? 'That did not work'; input.value = ''; },
    });
  }

  downloadFile(t: DocTemplate): void {
    this.api.downloadTemplateFile(t.id).subscribe({ next: (b) => downloadBlob(b, t.fileName ?? 'template.docx'), error: () => (this.error = 'Could not download the file') });
  }

  remove(t: DocTemplate): void {
    if (!confirm(`Delete the template "${t.name}"?`)) return;
    this.api.deleteDocTemplate(t.id).subscribe({ next: () => { this.info = 'Deleted.'; this.load(); }, error: (e) => (this.error = e?.error?.message ?? 'That did not work') });
  }
}
