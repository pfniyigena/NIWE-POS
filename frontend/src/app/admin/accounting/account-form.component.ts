import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { AccountApiService } from '../../core/api/account-api.service';
import { AccountType } from '../../core/models';

/** Admin > Accounting > Chart of Accounts > New/Edit. Mirrors OverheadCostFormComponent's simple CRUD form pattern. */
@Component({
  selector: 'app-account-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ id ? ('Edit account' | translate) : ('New account' | translate) }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">{{ 'Code' | translate }}</label>
          <input class="form-control" name="code" [(ngModel)]="code" [disabled]="!!id" [placeholder]="'e.g. 5300' | translate" required />
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="id">
            {{ 'The code can\\'t be changed once created.' | translate }}
          </div>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Name' | translate }}</label>
          <input class="form-control" name="name" [(ngModel)]="name" [placeholder]="'e.g. Rent Expense' | translate" required />
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Type' | translate }}</label>
          <select class="form-select" name="type" [(ngModel)]="type" [disabled]="!!id && systemAccount" required>
            <option value="ASSET">{{ 'Asset' | translate }}</option>
            <option value="LIABILITY">{{ 'Liability' | translate }}</option>
            <option value="EQUITY">{{ 'Equity' | translate }}</option>
            <option value="REVENUE">{{ 'Revenue' | translate }}</option>
            <option value="EXPENSE">{{ 'Expense' | translate }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Description (optional)' | translate }}</label>
          <input class="form-control" name="description" [(ngModel)]="description" />
        </div>
        <div class="form-group" *ngIf="id">
          <label class="form-label">
            <input type="checkbox" name="active" [(ngModel)]="active" [disabled]="systemAccount" /> {{ 'Active' | translate }}
          </label>
          <div class="text-muted" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="systemAccount">
            {{ 'This account is posted to automatically by the accounting module and cannot be deactivated.' | translate }}
          </div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-primary" type="submit">{{ 'Save' | translate }}</button>
          <a class="btn btn-link" routerLink="/admin/accounting/accounts">{{ 'Cancel' | translate }}</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class AccountFormComponent implements OnInit {
  id: number | null = null;
  code = '';
  name = '';
  type: AccountType = 'EXPENSE';
  description: string | null = null;
  active = true;
  systemAccount = false;
  error: string | null = null;

  constructor(
    private accountApi: AccountApiService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) return;
    this.id = Number(idParam);
    this.accountApi.getById(this.id).subscribe({
      next: (a) => {
        this.code = a.code;
        this.name = a.name;
        this.type = a.type;
        this.description = a.description;
        this.active = a.active;
        this.systemAccount = a.systemAccount;
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load account'),
    });
  }

  submit(): void {
    this.error = null;
    const payload = { code: this.code, name: this.name, type: this.type, description: this.description || null, active: this.active };
    const call = this.id ? this.accountApi.update(this.id, payload) : this.accountApi.create(payload);
    call.subscribe({
      next: () => this.router.navigate(['/admin/accounting/accounts']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to save account'),
    });
  }
}
