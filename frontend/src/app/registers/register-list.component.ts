import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { RegisterApiService } from '../core/api/register-api.service';
import { AuthService } from '../core/auth.service';
import { DeviceStatus, RegisterSummary } from '../core/models';
import { FormsModule } from '@angular/forms';

import { T } from '../core/i18n/t';
@Component({
  selector: 'app-register-list',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ 'Registers' | translate }}</h3>
        <a *ngIf="auth.canManage('SALES')" class="btn btn-primary" routerLink="/registers/new">{{ '+ New register' | translate }}</a>
      </div>
      <table>
        <thead>
          <tr>
            <th>{{ 'Name' | translate }}</th>
            <th>{{ 'Location' | translate }}</th>
            <th>{{ 'Warehouse' | translate }}</th>
            <th>{{ 'Location' | translate }}</th>
            <th>{{ 'Float' | translate }}</th>
            <th>{{ 'Cashiers' | translate }}</th>
            <th>{{ 'Device' | translate }}</th>
            <th>{{ 'Status' | translate }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let r of registers">
            <td>{{ r.name }}</td>
            <td>{{ r.location }}</td>
            <td>{{ r.warehouseName || '— (primary)' }}</td>
            <td>{{ r.defaultLocationName || '— (default)' }}</td>
            <td class="rl-float">{{ r.defaultFloat !== null ? (r.defaultFloat | number: '1.0-2') : '—' }}<span class="text-muted" *ngIf="r.maxFloat !== null"> {{ '(max {p0})' | translate: { p0: (r.maxFloat | number: '1.0-2') } }}</span></td>
            <td class="rl-cashiers">{{ r.allowedCashierNames.length ? r.allowedCashierNames.join(', ') : 'Anyone' }}</td>
            <td class="rl-device">
              <ng-container *ngIf="r.deviceBound; else notTied">
                <span class="badge" [ngClass]="isHere(r) ? 'badge-success' : 'badge-warning'">{{ isHere(r) ? ('This device' | translate) : ('Another device' | translate) }}</span>
                <div>{{ r.deviceName }}</div>
                <div class="text-muted" style="font-size:0.78rem;">{{ r.deviceIp ? r.deviceIp : 'any address' }}<span *ngIf="r.deviceBoundBy"> {{ '· bound by {p0}' | translate: { p0: (r.deviceBoundBy) } }}</span></div>
              </ng-container>
              <ng-template #notTied><span class="text-muted">{{ 'Any device' | translate }}</span></ng-template>
              <div *ngIf="auth.canManage('SALES')" class="flex gap-2" style="margin-top:0.25rem;">
                <button class="btn btn-sm btn-outline-primary rl-bind" *ngIf="!r.deviceBound || isHere(r)" (click)="startBind(r)">{{ r.deviceBound ? ('Update' | translate) : ('Bind to this device' | translate) }}</button>
                <button class="btn btn-sm btn-outline-danger rl-unbind" *ngIf="r.deviceBound" (click)="unbind(r)">{{ 'Unbind' | translate }}</button>
              </div>
              <div class="card rl-bind-panel" *ngIf="binding?.id === r.id" style="padding:0.6rem; margin-top:0.4rem; min-width:230px;">
                <label class="form-label">{{ 'Name of this device' | translate }}</label>
                <input class="form-control rl-bind-name" name="devname{{r.id}}" [(ngModel)]="bindName" maxlength="120" [placeholder]="'Front counter PC' | translate" />
                <label style="display:block; margin-top:0.4rem; font-size:0.85rem;">
                  <input type="checkbox" class="rl-bind-ip" [(ngModel)]="bindIp" name="devip{{r.id}}" />
                  {{ 'Also require this address' | translate }}<span *ngIf="bindYourIp"> ({{ bindYourIp }})</span>
                </label>
                <div class="flex gap-2" style="margin-top:0.5rem;">
                  <button class="btn btn-sm btn-primary rl-bind-ok" (click)="confirmBind(r)" [disabled]="busy">{{ 'Bind' | translate }}</button>
                  <button class="btn btn-sm btn-link rl-bind-cancel" (click)="binding = null">{{ 'Cancel' | translate }}</button>
                </div>
              </div>
            </td>
            <td>
              <span class="badge badge-warning rl-off" *ngIf="!r.active">{{ 'Switched off' | translate }}</span>
              <span class="badge badge-success" *ngIf="r.active && r.openSessionId">{{ 'Open session' | translate }}</span>
              <span class="badge badge-secondary" *ngIf="r.active && !r.openSessionId">{{ 'Closed' | translate }}</span>
            </td>
            <td class="flex gap-2">
              <span *ngIf="r.deviceBound && !isHere(r)" class="text-muted rl-elsewhere" style="font-size:0.8rem;">{{ 'Works on {p0} only' | translate: { p0: (r.deviceName) } }}</span>
              <a *ngIf="r.openSessionId && (!r.deviceBound || isHere(r))" class="btn btn-sm btn-success" [routerLink]="['/pos', r.openSessionId]">{{ 'Go to terminal' | translate }}</a>
              <a *ngIf="r.active && !r.openSessionId && auth.canManage('SALES') && (!r.deviceBound || isHere(r))" class="btn btn-sm btn-outline-primary" [routerLink]="['/registers', r.id, 'open-session']">{{ 'Open session' | translate }}</a>
              <a *ngIf="auth.canManage('SALES')" [routerLink]="['/registers', r.id, 'edit']">{{ 'Edit' | translate }}</a>
            </td>
          </tr>
          <tr *ngIf="registers.length === 0">
            <td colspan="9" class="text-muted">{{ 'No registers yet.' | translate }}</td>
          </tr>
        </tbody>
      </table>
      <p style="margin-top:1rem;">
        <a routerLink="/sessions">View sessions &rarr;</a>
      </p>
    </app-shell>
  `,
})
export class RegisterListComponent implements OnInit {
  registers: RegisterSummary[] = [];
  error: string | null = null;
  /** v1.13.0: for tills tied to a device, whether the device on this screen is that one. */
  status: Record<number, DeviceStatus> = {};
  binding: RegisterSummary | null = null;
  bindName = '';
  bindIp = true;
  bindYourIp: string | null = null;
  busy = false;

  constructor(private registerApi: RegisterApiService, public auth: AuthService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.registerApi.listRegisters().subscribe({
      next: (r) => {
        this.registers = r;
        for (const x of r) if (x.deviceBound) this.registerApi.deviceStatus(x.id).subscribe({ next: (st) => (this.status[x.id] = st), error: () => {} });
      },
      error: (err) => (this.error = err?.error?.message ?? T('Failed to load registers')),
    });
  }

  /** True when the till is tied to the device this screen is running on. */
  isHere(r: RegisterSummary): boolean {
    return !!this.status[r.id]?.allowed;
  }

  startBind(r: RegisterSummary): void {
    this.binding = r;
    this.bindName = r.deviceName ?? '';
    this.bindIp = r.deviceBound ? r.deviceIp !== null : true;
    this.bindYourIp = null;
    this.registerApi.deviceStatus(r.id).subscribe({ next: (st) => (this.bindYourIp = st.yourIp), error: () => {} });
  }

  confirmBind(r: RegisterSummary): void {
    this.busy = true;
    this.error = null;
    this.registerApi.bindDevice(r.id, this.bindName.trim(), this.bindIp).subscribe({
      next: () => { this.busy = false; this.binding = null; this.status = {}; this.load(); },
      error: (err) => { this.busy = false; this.error = err?.error?.message ?? T('Could not bind the register'); },
    });
  }

  unbind(r: RegisterSummary): void {
    this.error = null;
    this.registerApi.unbindDevice(r.id).subscribe({
      next: () => { this.binding = null; this.status = {}; this.load(); },
      error: (err) => (this.error = err?.error?.message ?? T('Could not unbind the register')),
    });
  }
}
