import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser } from '../core/models';

/**
 * ADMIN-only (Change 3). The admin opening the shift usually isn't the one who'll ring sales on
 * it, so they pick which active staff member this shift's first cashier session is assigned to
 * (Change: "when open session you need to select the user to be assigned this session").
 */
@Component({
  selector: 'app-session-open',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">Open session</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">Assign to</label>
          <select class="form-select" name="cashierId" [(ngModel)]="cashierId" required>
            <option [ngValue]="null" disabled>Select a staff member…</option>
            <option *ngFor="let u of activeStaff" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">Opening cash float</label>
          <input class="form-control" type="number" step="0.01" name="openingFloat" [(ngModel)]="openingFloat" />
        </div>
        <div class="flex gap-2">
          <button class="btn btn-success" type="submit" [disabled]="!cashierId">Open till</button>
          <a class="btn btn-link" routerLink="/registers">Cancel</a>
        </div>
      </form>
    </app-shell>
  `,
})
export class SessionOpenComponent implements OnInit {
  registerId!: number;
  openingFloat = 0;
  cashierId: number | null = null;
  activeStaff: AppUser[] = [];
  error: string | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private registerApi: RegisterApiService,
    private userApi: UserApiService
  ) {}

  ngOnInit(): void {
    this.registerId = Number(this.route.snapshot.paramMap.get('id'));
    this.userApi.list().subscribe({
      next: (users) => (this.activeStaff = users.filter((u) => u.active)),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load staff list'),
    });
  }

  submit(): void {
    if (!this.cashierId) return;
    this.error = null;
    this.registerApi.openSession(this.registerId, this.openingFloat, this.cashierId).subscribe({
      // Not the terminal — the admin who opened this isn't necessarily who's working it; the
      // assigned staff member reaches it themselves from the register list, or via "Switch
      // cashier" at the terminal with their PIN.
      next: () => this.router.navigate(['/registers']),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to open session'),
    });
  }
}
