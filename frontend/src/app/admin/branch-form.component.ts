import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { BranchApiService } from '../core/api/branch-api.service';

/** Admin > Branches > New/Edit — see BranchListComponent's own javadoc. */
@Component({
  selector: 'app-branch-form',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? 'Edit branch' : 'New branch' }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="flex gap-2">
          <div class="form-group" style="flex:1;">
            <label class="form-label">Code</label>
            <input class="form-control" name="code" [(ngModel)]="code" placeholder="e.g. KLA" required />
          </div>
          <div class="form-group" style="flex:3;">
            <label class="form-label">Name</label>
            <input class="form-control" name="name" [(ngModel)]="name" placeholder="e.g. Kampala Road" required />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Address (optional)</label>
          <textarea class="form-control" name="address" [(ngModel)]="address" rows="2"></textarea>
        </div>
        <div class="flex gap-2">
          <div class="form-group" style="flex:1;">
            <label class="form-label">Phone (optional)</label>
            <input class="form-control" name="phone" [(ngModel)]="phone" />
          </div>
          <div class="form-group" style="flex:1;">
            <label class="form-label">Email (optional)</label>
            <input class="form-control" type="email" name="email" [(ngModel)]="email" />
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">Receipt header (optional)</label>
          <textarea class="form-control" name="receiptHeader" [(ngModel)]="receiptHeader" rows="2"></textarea>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">Saved now; printing it on this branch's receipts comes in a later step.</div>
        </div>
        <h4 class="mt-3 mb-2" style="font-size:1rem;">Tax &amp; numbering (optional)</h4>
        <div class="flex gap-2">
          <div class="form-group" style="flex:1;">
            <label class="form-label">Tax ID / TIN</label>
            <input class="form-control" name="taxId" [(ngModel)]="taxId" />
          </div>
          <div class="form-group" style="flex:1;">
            <label class="form-label">EFRIS branch code</label>
            <input class="form-control" name="efrisBranchCode" [(ngModel)]="efrisBranchCode" />
          </div>
          <div class="form-group" style="flex:1;">
            <label class="form-label">Document prefix</label>
            <input class="form-control" name="documentPrefix" [(ngModel)]="documentPrefix" placeholder="e.g. KLA-" />
          </div>
        </div>
        <div class="text-muted mb-3" style="font-size:0.8rem;">
          Stored for each branch; applying them to document numbers and fiscal submissions comes in a later step.
        </div>
        <div class="form-group" *ngIf="!headquarters">
          <label class="form-label"><input type="checkbox" name="active" [(ngModel)]="active" /> Active</label>
        </div>
        <div class="form-group" *ngIf="headquarters">
          <label class="form-label"><input type="checkbox" checked disabled /> Active</label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;">This is your head office — it can't be deactivated.</div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">Save</button>
          <a class="btn btn-link" routerLink="/admin/branches">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class BranchFormComponent implements OnInit {
  id: number | null = null;
  code = '';
  name = '';
  address: string | null = null;
  phone: string | null = null;
  email: string | null = null;
  receiptHeader: string | null = null;
  taxId: string | null = null;
  efrisBranchCode: string | null = null;
  documentPrefix: string | null = null;
  active = true;
  /** Read-only — never admin-settable. */
  headquarters = false;
  error: string | null = null;

  constructor(private branchApi: BranchApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.branchApi.listAll().subscribe({
      next: (branches) => {
        const b = branches.find((x) => x.id === this.id);
        if (!b) {
          this.error = 'Branch not found';
          return;
        }
        this.code = b.code;
        this.name = b.name;
        this.address = b.address;
        this.phone = b.phone;
        this.email = b.email;
        this.receiptHeader = b.receiptHeader;
        this.taxId = b.taxId;
        this.efrisBranchCode = b.efrisBranchCode;
        this.documentPrefix = b.documentPrefix;
        this.active = b.active;
        this.headquarters = b.headquarters;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load branch'),
    });
  }

  submit(): void {
    this.error = null;
    if (!this.code.trim() || !this.name.trim()) {
      this.error = 'Code and name are required';
      return;
    }
    const payload = {
      code: this.code.trim(),
      name: this.name.trim(),
      address: this.address || null,
      phone: this.phone || null,
      email: this.email || null,
      receiptHeader: this.receiptHeader || null,
      taxId: this.taxId || null,
      efrisBranchCode: this.efrisBranchCode || null,
      documentPrefix: this.documentPrefix || null,
      active: this.headquarters ? true : this.active,
    };
    const call = this.id ? this.branchApi.update(this.id, payload) : this.branchApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/branches']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save branch'),
    });
  }
}
