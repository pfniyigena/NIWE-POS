import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { UserApiService } from '../core/api/user-api.service';
import { AppUser, DeviceStatus } from '../core/models';

/**
 * ADMIN-only (Change 3). The admin opening the shift usually isn't the one who'll ring sales on
 * it, so they pick which active staff member this shift's first cashier session is assigned to
 * (Change: "when open session you need to select the user to be assigned this session").
 */
@Component({
  selector: 'app-session-open',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell [narrow]="true">
      <h3 class="mb-3">{{ 'Open session' | translate }}</h3>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-danger so-device" *ngIf="device && !device.allowed">{{ device.message }}</div>
      <div class="text-muted so-register" *ngIf="registerName" style="margin-bottom:0.75rem;">{{ registerName }}</div>
      <form (ngSubmit)="submit()">
        <div class="form-group">
          <label class="form-label">{{ 'Assign to' | translate }}</label>
          <select class="form-select" name="cashierId" [(ngModel)]="cashierId" required>
            <option [ngValue]="null" disabled>{{ 'Select a staff member…' | translate }}</option>
            <option *ngFor="let u of eligibleStaff()" [ngValue]="u.id">{{ u.fullName }}</option>
          </select>
        </div>
        <div class="form-group">
          <label class="form-label">{{ 'Opening cash float' | translate }}</label>
          <input class="form-control so-float" type="number" step="0.01" name="openingFloat" [(ngModel)]="openingFloat" />
          <div class="text-muted so-float-hint" style="font-size:0.8rem; margin-top:0.25rem;" *ngIf="maxFloat !== null">{{ 'The most this till may open with is {p0}.' | translate: { p0: (maxFloat | number: '1.0-2') } }}</div>
        </div>
        <div class="flex gap-2">
          <button class="btn btn-success" type="submit" [disabled]="!cashierId || (device && !device.allowed)">{{ 'Open till' | translate }}</button>
          <a class="btn btn-link" routerLink="/registers">{{ 'Cancel' | translate }}</a>
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
  registerName = '';
  maxFloat: number | null = null;
  allowedIds: number[] = [];
  error: string | null = null;
  /** v1.13.0: a till tied to another device cannot be opened here. */
  device: DeviceStatus | null = null;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private registerApi: RegisterApiService,
    private userApi: UserApiService
  ) {}

  ngOnInit(): void {
    this.registerId = Number(this.route.snapshot.paramMap.get('id'));
    this.registerApi.deviceStatus(this.registerId).subscribe({ next: (d) => (this.device = d), error: () => {} });
    this.registerApi.listRegisters().subscribe({
      next: (rs) => {
        const r = rs.find((x) => x.id === this.registerId);
        if (!r) return;
        this.registerName = r.name;
        this.maxFloat = r.maxFloat;
        this.allowedIds = r.allowedCashierIds ?? [];
        if (r.defaultFloat !== null && r.defaultFloat !== undefined) this.openingFloat = r.defaultFloat;
        if (!r.active) this.error = 'This register is switched off, so a shift cannot be opened on it.';
      },
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load the register'),
    });
    this.userApi.list().subscribe({
      next: (users) => (this.activeStaff = users.filter((u) => u.active)),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load staff list'),
    });
  }

  /** A till with a list of allowed cashiers only offers those (a manager can still be picked by the backend's own rule). */
  eligibleStaff(): AppUser[] {
    if (!this.allowedIds.length) return this.activeStaff;
    return this.activeStaff.filter((u) => this.allowedIds.includes(u.id) || u.role === 'ADMIN');
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
