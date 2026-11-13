import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OnlineStoreApiService } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/** The page behind "Stop these reminders" in a cart reminder e-mail: /shop/cart/stop/<token>. Opening it is enough; the address is not reminded again. */
@Component({
  selector: 'app-shop-cart-stop',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="cart-stop-page" style="max-width:460px; margin:0 auto; padding:2rem 0.75rem;">
      <div class="card" style="padding:1.25rem; text-align:center;">
        <div *ngIf="!done && !error" class="text-muted">{{ 'Please wait…' | translate }}</div>
        <div class="alert alert-success cart-stop-done" *ngIf="done">{{ done }}</div>
        <div class="alert alert-danger cart-stop-error" *ngIf="error">{{ error }}</div>
        <div class="mt-3"><a class="btn btn-primary" routerLink="/shop">{{ 'Back to the shop' | translate }}</a></div>
      </div>
    </div>
  `,
})
export class ShopCartStopComponent implements OnInit {
  done: string | null = null;
  error: string | null = null;

  constructor(private api: OnlineStoreApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token') ?? '';
    this.api.stopCartReminders(token).subscribe({
      next: () => (this.done = T('You will not get any more reminders about your cart.')),
      error: (e) => (this.error = e?.error?.message ?? T('That did not work')),
    });
  }
}
