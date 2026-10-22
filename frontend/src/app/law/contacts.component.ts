import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ShellComponent } from '../shared/shell.component';
import { PagerComponent } from '../shared/pager.component';
import { LawExportComponent } from '../admin/law/law-export.component';
import { PAGE_SIZE } from '../core/api/paged';
import { Contact, ContactPayload, ContactType, LawApiService } from '../core/api/law-api.service';

/** Law Firm > Contacts: the opposing parties, their lawyers, witnesses, experts and officials who appear on matters. Our clients are customers. */
@Component({
  selector: 'app-law-contacts',
  standalone: true,
  imports: [CommonModule, FormsModule, ShellComponent, PagerComponent, LawExportComponent],
  template: `
    <app-shell [wide]="true">
      <h3 class="mb-3">Contacts</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>

      <form class="card mb-3" style="padding:1rem;" (ngSubmit)="save()">
        <strong>{{ editing ? 'Edit contact' : 'Add a contact' }}</strong>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap; align-items:end;">
          <div class="form-group"><label class="form-label">Name</label><input class="form-control" name="n" [(ngModel)]="f.name" required /></div>
          <div class="form-group"><label class="form-label">Kind</label>
            <select class="form-select" name="t" [(ngModel)]="f.type"><option value="PERSON">Person</option><option value="COMPANY">Company</option><option value="GOVERNMENT">Government / court</option></select></div>
          <div class="form-group"><label class="form-label">Phone</label><input class="form-control" name="p" [(ngModel)]="phone" /></div>
          <div class="form-group"><label class="form-label">E-mail</label><input class="form-control" type="email" name="e" [(ngModel)]="email" /></div>
          <div class="form-group"><label class="form-label">ID / registration no.</label><input class="form-control" name="i" [(ngModel)]="idNumber" /></div>
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">Address</label><input class="form-control" name="a" [(ngModel)]="address" /></div>
          <div class="form-group" style="flex:1; min-width:12rem;"><label class="form-label">Notes</label><input class="form-control" name="no" [(ngModel)]="notes" /></div>
          <button class="btn btn-primary" type="submit" [disabled]="saving">{{ editing ? 'Save' : 'Add' }}</button>
          <button class="btn btn-outline-secondary" type="button" *ngIf="editing" (click)="reset()">Cancel</button>
        </div>
      </form>

      <div class="flex gap-2 mb-3">
        <input class="form-control" style="width:20rem;" placeholder="Search name, phone, e-mail, ID" name="q" [(ngModel)]="q" (keyup.enter)="load()" />
        <button class="btn btn-outline-secondary" type="button" (click)="load()">Search</button>
        <app-law-export kind="contacts" [filters]="{ q: q.trim() }"></app-law-export>
      </div>
      <table class="stack table-sm">
        <thead><tr><th>Name</th><th>Kind</th><th>Phone</th><th>E-mail</th><th>ID</th><th>Notes</th><th class="text-end">Matters</th><th></th></tr></thead>
        <tbody>
          <tr *ngFor="let c of list">
            <td data-label="Name">{{ c.name }}</td><td data-label="Kind">{{ kind(c.type) }}</td><td data-label="Phone">{{ c.phone || '—' }}</td><td data-label="E-mail">{{ c.email || '—' }}</td><td data-label="ID">{{ c.idNumber || '—' }}</td><td data-label="Notes">{{ c.notes || '' }}</td>
            <td class="text-end" data-label="Matters">{{ c.matters }}</td>
            <td><button class="btn btn-sm btn-outline-secondary" (click)="edit(c)">Edit</button>
              <button class="btn btn-sm btn-outline-danger" style="margin-left:0.3rem;" *ngIf="!c.matters" (click)="remove(c)">Delete</button></td>
          </tr>
        </tbody>
      </table>
      <app-pager [page]="page" [size]="size" [total]="total" (pageChange)="goPage($event)"></app-pager>
      <p class="text-muted" *ngIf="!list.length">No contacts yet.</p>
    </app-shell>
  `,
})
export class LawContactsComponent implements OnInit {
  list: Contact[] = [];
  page = 0;
  total = 0;
  readonly size = PAGE_SIZE;
  q = '';
  editing: number | null = null;
  f: { name: string; type: ContactType } = { name: '', type: 'PERSON' };
  phone = '';
  email = '';
  idNumber = '';
  address = '';
  notes = '';
  saving = false;
  error: string | null = null;

  constructor(private api: LawApiService) {}

  ngOnInit(): void { this.load(); }

  goPage(p: number): void { this.page = p; this.load(false); }

  load(resetPage = true): void {
    if (resetPage) this.page = 0;
    this.api.contactsPage(this.q.trim(), { page: this.page, size: this.size }).subscribe({ next: (r) => { this.list = r.items; this.total = r.total; }, error: (e) => (this.error = e?.error?.message ?? 'Could not load the contacts') });
  }

  kind(t: string): string { return t === 'PERSON' ? 'Person' : t === 'COMPANY' ? 'Company' : 'Government / court'; }

  edit(c: Contact): void {
    this.editing = c.id;
    this.f = { name: c.name, type: c.type };
    this.phone = c.phone ?? ''; this.email = c.email ?? ''; this.idNumber = c.idNumber ?? ''; this.address = c.address ?? ''; this.notes = c.notes ?? '';
  }

  reset(): void {
    this.editing = null;
    this.f = { name: '', type: 'PERSON' };
    this.phone = this.email = this.idNumber = this.address = this.notes = '';
  }

  save(): void {
    this.saving = true;
    this.error = null;
    const p: ContactPayload = { name: this.f.name.trim(), type: this.f.type, phone: this.phone.trim() || null, email: this.email.trim() || null,
      idNumber: this.idNumber.trim() || null, address: this.address.trim() || null, notes: this.notes.trim() || null };
    const call = this.editing ? this.api.updateContact(this.editing, p) : this.api.createContact(p);
    call.subscribe({
      next: () => { this.saving = false; this.reset(); this.load(false); },
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? 'Could not save the contact'; },
    });
  }

  remove(c: Contact): void {
    if (!confirm(`Delete ${c.name}?`)) return;
    this.api.deleteContact(c.id).subscribe({ next: () => this.load(false), error: (e) => (this.error = e?.error?.message ?? 'Could not delete this contact') });
  }
}
