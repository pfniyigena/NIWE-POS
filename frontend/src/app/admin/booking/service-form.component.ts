import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ShellComponent } from '../../shared/shell.component';
import { BookingApiService, Option, PaymentMode, ServicePayload } from '../../core/api/booking-api.service';

import { T } from '../../core/i18n/t';
/** Booking > Services > one service (new or edit). */
@Component({
  selector: 'app-booking-service-form',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="flex justify-between align-center mb-3">
        <h3>{{ id ? ('Edit service' | translate) : ('New service' | translate) }}</h3>
        <a class="btn btn-outline-secondary" routerLink="/admin/booking/services">{{ 'Back' | translate }}</a>
      </div>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <form class="card" style="padding:1rem; max-width:44rem;" (ngSubmit)="save()">
        <div class="form-group mb-2"><label class="form-label">{{ 'Name' | translate }}</label><input class="form-control" name="n" [(ngModel)]="f.name" required /></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Description' | translate }} <span class="text-muted">{{ '(shown on the public page)' | translate }}</span></label>
          <textarea class="form-control" rows="2" name="d" [(ngModel)]="description"></textarea></div>
        <div class="flex gap-2">
          <div class="form-group mb-2" style="flex:1;"><label class="form-label">{{ 'Length (minutes)' | translate }}</label><input class="form-control" type="number" min="5" name="dm" [(ngModel)]="f.durationMinutes" required /></div>
          <div class="form-group mb-2" style="flex:1;"><label class="form-label">{{ 'Buffer after (minutes)' | translate }}</label><input class="form-control" type="number" min="0" name="bm" [(ngModel)]="f.bufferMinutes" /></div>
          <div class="form-group mb-2" style="flex:1;"><label class="form-label">{{ 'Price' | translate }}</label><input class="form-control" type="number" min="0" step="0.01" name="p" [(ngModel)]="f.price" /></div>
        </div>
        <div class="flex gap-2">
          <div class="form-group mb-2" style="flex:1;"><label class="form-label">{{ 'Payment needed to confirm' | translate }}</label>
            <select class="form-select" name="pm" [(ngModel)]="f.paymentMode">
              <option value="NONE">{{ 'None — pay at the visit' | translate }}</option><option value="DEPOSIT">{{ 'A deposit' | translate }}</option><option value="FULL">{{ 'The full price' | translate }}</option>
            </select></div>
          <div class="form-group mb-2" style="flex:1;" *ngIf="f.paymentMode === 'DEPOSIT'"><label class="form-label">{{ 'Deposit (% of price)' | translate }}</label>
            <input class="form-control" type="number" min="1" max="100" name="dp" [(ngModel)]="f.depositPercent" /></div>
        </div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Needs a room or piece of equipment of this kind' | translate }} <span class="text-muted">{{ '(optional)' | translate }}</span></label>
          <input class="form-control" name="rk" [(ngModel)]="resourceKind" [placeholder]="'e.g. Room, Chair, Court' | translate" /></div>
        <div class="form-group mb-2"><label class="form-label">{{ 'Colour on the calendar' | translate }}</label><input type="color" name="c" [(ngModel)]="color" /></div>
        <div class="form-group mb-2">
          <label class="form-label">{{ 'Who can do this' | translate }} <span class="text-muted">{{ '(leave empty for anyone)' | translate }}</span></label>
          <div class="flex gap-2" style="flex-wrap:wrap;">
            <label *ngFor="let p of staff" style="display:flex; gap:0.3rem; align-items:center;">
              <input type="checkbox" [checked]="f.staffIds.includes(p.id)" (change)="toggle(p.id)" [name]="'s' + p.id" /> {{ p.name }}
            </label>
          </div>
        </div>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-1"><input type="checkbox" name="a" [(ngModel)]="f.active" /> {{ 'Can be booked' | translate }}</label>
        <label style="display:flex; gap:0.5rem; align-items:center;" class="mb-3"><input type="checkbox" name="v" [(ngModel)]="f.publicVisible" /> {{ 'Show on the public booking page' | translate }}</label>
        <div><button class="btn btn-primary" type="submit" [disabled]="saving">{{ saving ? ('Saving…' | translate) : ('Save' | translate) }}</button></div>
      </form>
    </app-shell>
  `,
})
export class BookingServiceFormComponent implements OnInit {
  id: number | null = null;
  staff: Option[] = [];
  description = '';
  resourceKind = '';
  color = '#2f6fb3';
  f: ServicePayload = { name: '', durationMinutes: 30, bufferMinutes: 0, price: 0, paymentMode: 'NONE' as PaymentMode, depositPercent: 30, active: true, publicVisible: true, staffIds: [] };
  saving = false;
  error: string | null = null;

  constructor(private api: BookingApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.api.staff().subscribe({ next: (l) => (this.staff = l), error: () => {} });
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.id = Number(id);
      this.api.service(this.id).subscribe({
        next: (s) => {
          this.f = { name: s.name, durationMinutes: s.durationMinutes, bufferMinutes: s.bufferMinutes, price: s.price, paymentMode: s.paymentMode, depositPercent: s.depositPercent,
                     active: s.active, publicVisible: s.publicVisible, staffIds: s.staff.map((p) => p.id) };
          this.description = s.description ?? '';
          this.resourceKind = s.resourceKind ?? '';
          this.color = s.color || '#2f6fb3';
        },
        error: (e) => (this.error = e?.error?.message ?? T('Could not load the service')),
      });
    }
  }

  toggle(id: number): void {
    this.f.staffIds = this.f.staffIds.includes(id) ? this.f.staffIds.filter((x) => x !== id) : [...this.f.staffIds, id];
  }

  save(): void {
    this.saving = true;
    this.error = null;
    const p: ServicePayload = { ...this.f, name: this.f.name.trim(), description: this.description.trim() || null, resourceKind: this.resourceKind.trim() || null, color: this.color };
    const call = this.id ? this.api.updateService(this.id, p) : this.api.createService(p);
    call.subscribe({
      next: () => this.router.navigate(['/admin/booking/services']),
      error: (e) => { this.saving = false; this.error = e?.error?.message ?? T('Could not save the service'); },
    });
  }
}
