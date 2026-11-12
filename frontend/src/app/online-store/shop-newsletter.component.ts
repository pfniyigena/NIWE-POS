import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OnlineStoreApiService } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * The page behind the links of the newsletter e-mails: /shop/newsletter/confirm/<token> confirms the subscription and /shop/newsletter/unsubscribe/<token>
 * ends it. The token is read from the address and sent to the server; nothing else is needed.
 */
@Component({
  selector: 'app-shop-newsletter',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink],
  template: `
    <div class="nl-page" style="max-width:460px; margin:0 auto; padding:2rem 0.75rem;">
      <div class="card" style="padding:1.25rem; text-align:center;">
        <div *ngIf="!done && !error" class="text-muted">{{ 'Please wait…' | translate }}</div>
        <div class="alert alert-success nl-done" *ngIf="done">{{ done }}</div>
        <div class="alert alert-danger nl-error" *ngIf="error">{{ error }}</div>
        <div class="mt-2" *ngIf="unsubscribeToken && action === 'confirm'">
          <span class="text-muted" style="font-size:0.85rem;">{{ 'Changed your mind?' | translate }}</span>
          <a class="nl-unsub" style="cursor:pointer; margin-left:0.3rem;" (click)="unsubscribe()">{{ 'Unsubscribe' | translate }}</a>
        </div>
        <div class="mt-3"><a class="btn btn-primary" routerLink="/shop">{{ 'Back to the shop' | translate }}</a></div>
      </div>
    </div>
  `,
})
export class ShopNewsletterComponent implements OnInit {
  action = '';
  done: string | null = null;
  error: string | null = null;
  unsubscribeToken: string | null = null;

  constructor(private api: OnlineStoreApiService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.action = this.route.snapshot.paramMap.get('action') ?? '';
    const token = this.route.snapshot.paramMap.get('token') ?? '';
    if (this.action === 'confirm') {
      this.api.confirmNewsletter(token).subscribe({
        next: (r) => {
          this.unsubscribeToken = r.unsubscribeToken;
          this.done = T('Thank you. You are subscribed to our newsletter.');
        },
        error: (e) => (this.error = e?.error?.message ?? T('That did not work')),
      });
    } else if (this.action === 'unsubscribe') {
      this.send(token);
    } else {
      this.error = T('This link is not valid, or it was already used.');
    }
  }

  unsubscribe(): void {
    if (this.unsubscribeToken) this.send(this.unsubscribeToken);
  }

  private send(token: string): void {
    this.api.unsubscribeNewsletter(token).subscribe({
      next: () => {
        this.unsubscribeToken = null;
        this.error = null;
        this.done = T('You are unsubscribed. You will not receive our newsletter.');
        this.action = 'unsubscribe';
      },
      error: (e) => (this.error = e?.error?.message ?? T('That did not work')),
    });
  }
}
