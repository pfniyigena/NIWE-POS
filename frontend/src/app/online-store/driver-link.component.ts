import { Component, OnDestroy, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { DriverBoard, DriverJob, DriverReturnJob, OnlineStoreApiService } from '../core/api/online-store-api.service';
import { T } from '../core/i18n/t';

/**
 * A delivery driver's own page (v1.59.0), opened from the private link the shop gave him: no staff login, the secret in the address plus a 4-digit PIN.
 * He sees only the orders assigned to him that are out for delivery, and hands each over with the customer's delivery code (the customer signs only on his own page).
 * A customer who refuses or takes only part of the goods is for the shop to record: the driver is pointed to the shop's phone.
 */
@Component({
  selector: 'app-driver-link',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule],
  template: `
    <div class="dl-page" style="max-width:520px; margin:0 auto; padding:1rem 0.75rem 3rem;">
      <div class="alert alert-danger dl-error" *ngIf="error">{{ error }}</div>

      <div *ngIf="state === 'loading'" class="text-muted">{{ 'Loading…' | translate }}</div>

      <div *ngIf="state === 'invalid'" class="card dl-invalid" style="padding:1rem;">
        <strong>{{ 'This link is not valid any more. Ask the shop for a new one.' | translate }}</strong>
      </div>

      <div *ngIf="state === 'pin'" class="card dl-pin-box" style="padding:1.2rem; text-align:center;">
        <div class="text-muted" *ngIf="shopName">{{ shopName }}</div>
        <h2 style="margin:0.2rem 0 0.6rem;">🛵 {{ 'Delivery driver' | translate }}</h2>
        <label class="form-label" for="dlpin">{{ 'Type your PIN' | translate }}</label>
        <input id="dlpin" class="form-control dl-pin" style="max-width:160px; margin:0 auto; text-align:center; letter-spacing:0.5em; font-size:1.6rem; font-weight:700;"
               type="password" inputmode="numeric" autocomplete="off" maxlength="4" [(ngModel)]="pin" name="dlpin" (keyup.enter)="login()" />
        <button class="btn btn-primary mt-3 dl-login" style="min-width:160px;" [disabled]="busy || pin.trim().length !== 4" (click)="login()">{{ 'Open my deliveries' | translate }}</button>
      </div>

      <ng-container *ngIf="state === 'board' && board">
        <div class="flex justify-between align-center mb-2" style="gap:0.5rem;">
          <div>
            <h2 style="margin:0;" class="dl-hello">🛵 {{ board.driverName }}</h2>
            <div class="text-muted" *ngIf="board.shopName" style="font-size:0.85rem;">{{ board.shopName }}</div>
          </div>
          <div class="flex gap-2">
            <button class="btn btn-outline-secondary btn-sm" (click)="load()">↻</button>
            <button class="btn btn-outline-secondary btn-sm dl-leave" (click)="leave()">{{ 'Close' | translate }}</button>
          </div>
        </div>
        <div class="alert alert-success dl-done" *ngIf="message">{{ message }}</div>
        <div class="card dl-cash mb-3" style="padding:0.9rem;">
          <div class="flex justify-between align-center" style="gap:0.5rem;">
            <strong>💵 {{ 'Cash I hold' | translate }}</strong>
            <strong class="dl-holding" style="font-size:1.25rem;" [style.color]="board.cash.holding > 0 ? '#b45309' : '#15803d'">{{ board.cash.holding | number: '1.0-2' }}</strong>
          </div>
          <div class="text-muted" style="font-size:0.85rem;" *ngIf="board.cash.waiting.length === 0">{{ 'Nothing to hand in to the shop.' | translate }}</div>
          <ng-container *ngIf="board.cash.waiting.length">
            <div class="text-muted" style="font-size:0.85rem;">{{ 'Hand this cash in to the shop. It stays here until the shop records it.' | translate }}</div>
            <div class="flex justify-between dl-cash-row" style="font-size:0.9rem; margin-top:0.3rem;" *ngFor="let c of board.cash.waiting">
              <span>{{ c.code }} · {{ c.customerName }}<span class="text-muted" *ngIf="c.at"> · {{ c.at | date: 'dd/MM HH:mm' }}</span></span>
              <strong>{{ c.collected | number: '1.0-2' }}</strong>
            </div>
          </ng-container>
          <details *ngIf="board.cash.settled.length" class="dl-cash-done" style="margin-top:0.5rem;">
            <summary class="text-muted" style="font-size:0.85rem; cursor:pointer;">{{ 'Handed in lately' | translate }}</summary>
            <div class="flex justify-between" style="font-size:0.85rem; margin-top:0.25rem;" *ngFor="let c of board.cash.settled">
              <span>{{ c.code }}<span class="text-muted" *ngIf="c.at"> · {{ c.at | date: 'dd/MM HH:mm' }}</span></span>
              <span>{{ c.handedIn | number: '1.0-2' }}<span *ngIf="(c.difference ?? 0) !== 0" [style.color]="(c.difference ?? 0) < 0 ? '#b91c1c' : '#15803d'"> ({{ c.difference | number: '1.0-2' }})</span></span>
            </div>
          </details>
        </div>

        <p class="text-muted dl-none" *ngIf="board.jobs.length === 0">{{ 'Nothing to deliver right now.' | translate }}</p>

        <div class="card dl-order mb-3" *ngFor="let o of board.jobs" style="padding:0.9rem;">
          <div class="flex justify-between" style="gap:0.5rem; flex-wrap:wrap;">
            <strong class="dl-code" style="font-size:1.15rem;">{{ o.code }}</strong>
            <strong class="dl-collect" [style.color]="o.paid ? '#15803d' : '#b45309'">{{ o.paid ? ('Already paid' | translate) : (('To collect' | translate) + ': ' + (o.toCollect | number: '1.0-2')) }}</strong>
          </div>
          <div class="dl-slot" *ngIf="o.slotDate && o.slotStart" style="margin-top:0.25rem; font-weight:600;">⏰ {{ o.slotDate | date: 'dd/MM' }} · {{ o.slotStart }} - {{ o.slotEnd }}</div>
          <div style="margin-top:0.35rem;">
            <div><strong>{{ o.customerName }}</strong> · <a class="dl-call" [href]="'tel:' + o.customerPhone">📞 {{ o.customerPhone }}</a></div>
            <div *ngIf="o.zoneName || o.customerAddress">📍 {{ o.zoneName }}<ng-container *ngIf="o.zoneName && o.customerAddress"> — </ng-container>{{ o.customerAddress }}
              <a *ngIf="o.customerAddress" class="dl-map" style="margin-left:0.4rem;" target="_blank" rel="noopener" [href]="mapLink(o)">{{ 'Map' | translate }}</a></div>
            <div class="text-muted" *ngIf="o.notes" style="font-size:0.9rem;">“{{ o.notes }}”</div>
            <div class="text-muted" *ngIf="o.deliveryAttempts > 0" style="font-size:0.85rem;">{{ 'Tries so far' | translate }}: {{ o.deliveryAttempts }}</div>
            <ul style="margin:0.4rem 0 0; padding-left:1.1rem;"><li *ngFor="let l of o.lines">{{ l.quantity }} × {{ l.name }}</li></ul>
          </div>

          <button class="btn btn-primary mt-2 dl-handover-open" *ngIf="open !== o.id" (click)="start(o)">{{ 'Hand over' | translate }}</button>

          <div *ngIf="open === o.id" class="dl-handover mt-2" style="border-top:1px dashed #9ca3af; padding-top:0.6rem;">
            <div class="form-group">
              <label class="form-label">{{ "Customer's delivery code" | translate }}</label>
              <div class="flex gap-2">
                <input class="form-control dl-code-input" style="width:140px; letter-spacing:0.3em; font-weight:700; font-size:1.2rem;" maxlength="4" inputmode="numeric" autocomplete="off" [(ngModel)]="code" name="dlcode" placeholder="••••" />
                <button class="btn btn-primary dl-confirm-code" [disabled]="busy || !code.trim()" (click)="send(o)">{{ 'Confirm with the code' | translate }}</button>
              </div>
            </div>
            <button class="btn btn-sm btn-outline-secondary mt-2" (click)="open = null">{{ 'Cancel' | translate }}</button>
          </div>
        </div>

        <ng-container *ngIf="board.returnJobs.length">
          <h3 class="dl-returns-title" style="margin:1rem 0 0.5rem;">↩ {{ 'Returns to collect' | translate }}</h3>
          <div class="card dl-return mb-3" *ngFor="let r of board.returnJobs" style="padding:0.9rem; border-left:4px solid #1d4ed8;">
            <div class="flex justify-between" style="gap:0.5rem; flex-wrap:wrap;">
              <strong class="dl-rcode" style="font-size:1.1rem;">{{ r.code }}</strong>
              <span class="text-muted">{{ 'Order {p0}' | translate: { p0: r.orderCode } }}</span>
            </div>
            <div style="margin-top:0.35rem;">
              <div><strong>{{ r.customerName }}</strong> · <a [href]="'tel:' + r.customerPhone">📞 {{ r.customerPhone }}</a></div>
              <div *ngIf="r.zoneName || r.customerAddress">📍 {{ r.zoneName }}<ng-container *ngIf="r.zoneName && r.customerAddress"> — </ng-container>{{ r.customerAddress }}
                <a *ngIf="r.customerAddress" class="dl-rmap" style="margin-left:0.4rem;" target="_blank" rel="noopener" [href]="mapLinkOf(r.customerAddress, r.zoneName)">{{ 'Map' | translate }}</a></div>
              <div class="text-muted" style="font-size:0.9rem;">“{{ r.reason }}”</div>
              <ul style="margin:0.4rem 0 0; padding-left:1.1rem;"><li *ngFor="let l of r.lines">{{ l.quantity }} × {{ l.name }}</li></ul>
            </div>
            <button class="btn btn-primary mt-2 dl-collect-open" *ngIf="openReturn !== r.id" (click)="startReturn(r)">{{ 'Collect the goods' | translate }}</button>
            <div *ngIf="openReturn === r.id" class="dl-collect-box mt-2" style="border-top:1px dashed #9ca3af; padding-top:0.6rem;">
              <div class="form-group">
                <label class="form-label">{{ "Customer's return code" | translate }}</label>
                <div class="flex gap-2">
                  <input class="form-control dl-rcode-input" style="width:140px; letter-spacing:0.3em; font-weight:700; font-size:1.2rem;" maxlength="4" inputmode="numeric" autocomplete="off" [(ngModel)]="code" name="dlrcode" placeholder="••••" />
                  <button class="btn btn-primary dl-collect-confirm" [disabled]="busy || !code.trim()" (click)="collect(r)">{{ 'Confirm with the code' | translate }}</button>
                </div>
              </div>
              <button class="btn btn-sm btn-outline-secondary mt-2" (click)="openReturn = null">{{ 'Cancel' | translate }}</button>
            </div>
          </div>
        </ng-container>

        <p class="text-muted dl-help" style="font-size:0.85rem;">
          {{ 'The customer is not there, refuses, or keeps only part of the goods? Do not hand over: call the shop, who records it.' | translate }}
          <a *ngIf="board.shopPhone" [href]="'tel:' + board.shopPhone">📞 {{ board.shopPhone }}</a>
        </p>
      </ng-container>
    </div>
  `,
})
export class DriverLinkComponent implements OnInit, OnDestroy {
  token = '';
  state: 'loading' | 'invalid' | 'pin' | 'board' = 'loading';
  shopName: string | null = null;
  board: DriverBoard | null = null;
  pin = '';
  session = '';
  busy = false;
  error: string | null = null;
  message: string | null = null;
  open: number | null = null;
  openReturn: number | null = null;
  code = '';
  private poll: ReturnType<typeof setInterval> | null = null;

  constructor(private route: ActivatedRoute, private api: OnlineStoreApiService) {}

  private key(): string {
    return 'niwe.driver.session.' + this.token.slice(0, 12);
  }

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';
    this.api.driverLinkInfo(this.token).subscribe({
      next: (i) => {
        this.shopName = i.shopName;
        let saved = '';
        try { saved = localStorage.getItem(this.key()) ?? ''; } catch { saved = ''; }
        if (saved) { this.session = saved; this.load(); } else this.state = 'pin';
      },
      error: () => { this.state = 'invalid'; },
    });
    this.poll = setInterval(() => { if (this.state === 'board' && !this.busy && this.open === null && this.openReturn === null) this.load(); }, 20000);
  }

  ngOnDestroy(): void {
    if (this.poll) clearInterval(this.poll);
  }

  login(): void {
    this.busy = true;
    this.error = null;
    this.api.driverLogin(this.token, this.pin.trim()).subscribe({
      next: (s) => {
        this.busy = false;
        this.session = s.session;
        this.pin = '';
        try { localStorage.setItem(this.key(), s.session); } catch { /* he types the PIN again next time */ }
        this.load();
      },
      error: (e) => { this.busy = false; this.pin = ''; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }

  /** The session ended (or the shop reset the PIN): back to the PIN screen. */
  private signedOut(message: string | null): void {
    this.session = '';
    this.board = null;
    this.open = null;
    this.openReturn = null;
    try { localStorage.removeItem(this.key()); } catch { /* nothing kept */ }
    this.state = 'pin';
    this.error = message;
  }

  load(): void {
    this.api.driverBoard(this.token, this.session).subscribe({
      next: (b) => { this.board = b; this.state = 'board'; this.error = null; },
      error: (e) => {
        if (e?.status === 403) this.signedOut(e?.error?.message ?? T('Type your PIN to continue.'));
        else if (e?.status === 404) this.state = 'invalid';
        else if (this.state === 'loading') this.signedOut(null);
        else this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }

  leave(): void {
    this.signedOut(null);
  }

  mapLink(o: DriverJob): string {
    return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent([o.customerAddress, o.zoneName].filter(Boolean).join(', '));
  }

  mapLinkOf(address: string | null, zone: string | null): string {
    return 'https://www.google.com/maps/search/?api=1&query=' + encodeURIComponent([address, zone].filter(Boolean).join(', '));
  }

  startReturn(r: DriverReturnJob): void {
    this.openReturn = r.id;
    this.open = null;
    this.code = '';
    this.message = null;
    this.error = null;
  }

  collect(r: DriverReturnJob): void {
    this.busy = true;
    this.error = null;
    this.api.driverCollect(this.token, this.session, r.id, { code: this.code.trim() }).subscribe({
      next: (b) => {
        this.busy = false;
        this.openReturn = null;
        this.board = b;
        this.message = T('Return {p0} collected. Bring the goods to the shop.', { p0: r.code });
      },
      error: (e) => {
        this.busy = false;
        if (e?.status === 403) this.signedOut(e?.error?.message ?? T('Type your PIN to continue.'));
        else this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }

  start(o: DriverJob): void {
    this.open = o.id;
    this.openReturn = null;
    this.code = '';
    this.message = null;
    this.error = null;
  }

  send(o: DriverJob): void {
    this.busy = true;
    this.error = null;
    this.api.driverReceive(this.token, this.session, o.id, { code: this.code.trim() }).subscribe({
      next: (b) => {
        this.busy = false;
        this.open = null;
        this.board = b;
        this.message = T('Order {p0} delivered.', { p0: o.code });
      },
      error: (e) => {
        this.busy = false;
        if (e?.status === 403) this.signedOut(e?.error?.message ?? T('Type your PIN to continue.'));
        else this.error = e?.error?.message ?? T('That did not work');
      },
    });
  }
}
