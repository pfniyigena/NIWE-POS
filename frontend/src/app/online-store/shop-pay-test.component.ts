import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { OnlineStoreApiService, TestPayment } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * The pretend payment page of the test provider (development and demos): the customer presses Pay or Decline instead of paying a real provider.
 * It exists only while the simulated provider is switched on; otherwise the server answers "not found". No real money moves here.
 */
@Component({
  selector: 'app-shop-pay-test',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="pt-page" style="max-width:420px; margin:0 auto; padding:1.5rem 0.75rem 3rem;">
      <div class="alert alert-warning" style="text-align:center;"><strong>{{ 'TEST PAYMENT' | translate }}</strong><br />{{ 'No real money is taken on this page.' | translate }}</div>
      <div class="alert alert-danger pt-error" *ngIf="error">{{ error }}</div>
      <div *ngIf="!p && !error" class="text-muted">{{ 'Loading…' | translate }}</div>
      <div class="card" *ngIf="p" style="padding:1rem; text-align:center;">
        <div class="text-muted">{{ p.method === 'CARD' ? ('Card' | translate) : ('Mobile money' | translate) }}</div>
        <div class="pt-amount" style="font-size:2rem; font-weight:700;">{{ p.amount | number: '1.0-2' }} {{ p.currency }}</div>
        <div class="text-muted" style="font-size:0.8rem;">{{ p.reference }}</div>
        <ng-container *ngIf="p.state === 'PENDING'; else done">
          <div class="flex gap-2 mt-3" style="justify-content:center;">
            <button class="btn btn-primary pt-pay" [disabled]="busy" (click)="decide('approve')">{{ 'Pay' | translate }}</button>
            <button class="btn btn-outline-danger pt-decline" [disabled]="busy" (click)="decide('decline')">{{ 'Decline' | translate }}</button>
          </div>
        </ng-container>
        <ng-template #done>
          <div class="mt-2" [style.color]="p.state === 'PAID' ? '#15803d' : '#b91c1c'" style="font-weight:600;">{{ p.state === 'PAID' ? ('Paid' | translate) : ('Declined' | translate) }}</div>
        </ng-template>
        <a class="btn btn-outline-secondary mt-3" [routerLink]="['/shop/order', code()]">{{ 'Back to my order' | translate }}</a>
      </div>
    </div>
  `,
})
export class ShopPayTestComponent implements OnInit {
  p: TestPayment | null = null;
  error: string | null = null;
  busy = false;
  private reference = '';

  constructor(private api: OnlineStoreApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    this.reference = this.route.snapshot.paramMap.get('reference') ?? '';
    this.api.testPayment(this.reference).subscribe({
      next: (p) => (this.p = p),
      error: (e) => (this.error = e?.error?.message ?? T('This test payment was not found. It may have been lost when the server restarted.')),
    });
  }

  /** The order code is inside the payment reference (OS + code + -attempt). */
  code(): string {
    return this.reference.slice(2, this.reference.lastIndexOf('-'));
  }

  decide(d: 'approve' | 'decline'): void {
    this.busy = true;
    this.api.decideTestPayment(this.reference, d).subscribe({
      next: () => this.router.navigate(['/shop/order', this.code()]),
      error: (e) => {
        this.busy = false;
        this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }
}
