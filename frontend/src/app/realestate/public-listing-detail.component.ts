import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicRealEstateApiService, PublicDetail, PublicInfo } from '../core/api/public-realestate-api.service';
import { PublicSettingsApiService } from '../core/api/public-settings-api.service';
import { PURPOSE_LABEL, typeLabel } from '../core/api/realestate-api.service';
import { NiweFooterComponent } from '../shared/niwe-footer.component';

/** One property on the public website (/listings/:id): photos, details and an enquiry form that becomes a lead for the agent. Needs no login. */
@Component({
  selector: 'app-public-listing-detail',
  standalone: true,
  imports: [TranslatePipe, NiweFooterComponent, CommonModule, FormsModule, RouterLink],
  template: `
    <div class="pd-page">
      <header class="pd-bar">
        <div class="pd-inner flex align-center justify-between" style="gap:1rem; flex-wrap:wrap;">
          <div class="flex align-center gap-2">
            <img *ngIf="logoDataUrl" [src]="logoDataUrl" alt="" style="height:36px; width:36px; object-fit:contain; border-radius:0.35rem; background:#fff;" />
            <a routerLink="/listings" style="color:inherit; text-decoration:none; font-weight:600;">{{ businessName || ('Properties' | translate) }}</a>
          </div>
          <a routerLink="/listings">{{ '← All properties' | translate }}</a>
        </div>
      </header>

      <main class="pd-inner pd-main">
        <div class="alert alert-danger" *ngIf="error">{{ error }} <a routerLink="/listings">{{ 'See all properties' | translate }}</a></div>
        <div *ngIf="!d && !error" class="text-muted">{{ 'Loading…' | translate }}</div>

        <ng-container *ngIf="d">
          <div class="pd-grid">
            <div>
              <div class="pd-photo">
                <img *ngIf="d.photoIds.length" [src]="photo(current ?? d.photoIds[0])" [alt]="d.title" />
                <div *ngIf="!d.photoIds.length" class="pd-nophoto">🏠</div>
              </div>
              <div class="pd-thumbs" *ngIf="d.photoIds.length > 1">
                <img *ngFor="let id of d.photoIds" [src]="photo(id)" alt="" loading="lazy" (click)="current = id" [class.on]="(current ?? d.photoIds[0]) === id" />
              </div>

              <h1 class="mt-3" style="font-size:1.6rem; margin-bottom:0.2rem;">{{ d.title }}</h1>
              <div class="text-muted">{{ typeName(d.type) }}<span *ngIf="d.area"> · {{ d.area }}</span> {{ '· {p0} · Ref {p1}' | translate: { p0: (purposeName(d.purpose)), p1: (d.code) } }}</div>
              <div class="pd-price mt-2">{{ priceText() }}<span class="text-muted" *ngIf="d.negotiable" style="font-size:0.9rem; font-weight:400;"> {{ '· negotiable' | translate }}</span></div>

              <div class="pd-facts mt-2">
                <div *ngIf="d.bedrooms != null"><strong>{{ d.bedrooms }}</strong><span>{{ 'bedrooms' | translate }}</span></div>
                <div *ngIf="d.bathrooms != null"><strong>{{ d.bathrooms }}</strong><span>{{ 'bathrooms' | translate }}</span></div>
                <div *ngIf="d.parkingSpaces != null"><strong>{{ d.parkingSpaces }}</strong><span>{{ 'parking' | translate }}</span></div>
                <div *ngIf="d.sizeSqm != null"><strong>{{ d.sizeSqm | number: '1.0-0' }}</strong><span>{{ 'm² built' | translate }}</span></div>
                <div *ngIf="d.plotSqm != null"><strong>{{ d.plotSqm | number: '1.0-0' }}</strong><span>{{ 'm² plot' | translate }}</span></div>
                <div *ngIf="d.yearBuilt != null"><strong>{{ d.yearBuilt }}</strong><span>{{ 'built' | translate }}</span></div>
                <div *ngIf="d.furnished"><strong>{{ 'Yes' | translate }}</strong><span>{{ 'furnished' | translate }}</span></div>
              </div>

              <div class="mt-3" *ngIf="d.description" style="white-space:pre-wrap;">{{ d.description }}</div>
              <div class="mt-3" *ngIf="d.features.length">
                <h4 class="mb-1">{{ 'Features' | translate }}</h4>
                <div class="flex gap-2" style="flex-wrap:wrap;"><span *ngFor="let f of d.features" class="badge badge-secondary">{{ f }}</span></div>
              </div>
            </div>

            <aside class="card pd-form">
              <ng-container *ngIf="!sent; else thanks">
                <h3 class="mb-1">{{ 'Interested in this property?' | translate }}</h3>
                <p class="text-muted mb-2" style="font-size:0.9rem;">{{ 'Leave your details and {p0} will get back to you.' | translate: { p0: (d.agentName || 'our team') } }}</p>
                <div class="alert alert-danger" *ngIf="formError">{{ formError }}</div>
                <form (ngSubmit)="send()">
                  <div class="form-group mb-2"><label class="form-label">{{ 'Your name *' | translate }}</label><input class="form-control" name="n" [(ngModel)]="f.name" maxlength="200" /></div>
                  <div class="form-group mb-2"><label class="form-label">{{ 'Phone' | translate }}</label><input class="form-control" name="p" [(ngModel)]="f.phone" maxlength="40" /></div>
                  <div class="form-group mb-2"><label class="form-label">{{ 'E-mail' | translate }}</label><input class="form-control" type="email" name="e" [(ngModel)]="f.email" maxlength="200" /></div>
                  <div class="form-group mb-2"><label class="form-label">{{ 'Message' | translate }}</label><textarea class="form-control" rows="4" name="m" [(ngModel)]="f.message" maxlength="1000" placeholder="Ask a question or suggest a time to view."></textarea></div>
                  <!-- A trap for robots: people never see or fill this in. -->
                  <div aria-hidden="true" style="position:absolute; left:-9999px; width:1px; height:1px; overflow:hidden;">
                    <label>{{ 'Leave this empty' | translate }} <input name="website" tabindex="-1" autocomplete="off" [(ngModel)]="f.website" /></label>
                  </div>
                  <div class="text-muted mb-2" style="font-size:0.8rem;">{{ 'A phone number or an e-mail is needed so we can reach you.' | translate }}</div>
                  <button class="btn btn-primary btn-block" type="submit" [disabled]="sending">{{ sending ? ('Sending…' | translate) : ('Send enquiry' | translate) }}</button>
                </form>
                <div class="mt-3 text-muted" style="font-size:0.9rem;" *ngIf="info?.phone || info?.email">{{ 'Or contact us:' | translate }} <span *ngIf="info?.phone">{{ info?.phone }}</span><span *ngIf="info?.phone && info?.email"> · </span><span *ngIf="info?.email">{{ info?.email }}</span></div>
              </ng-container>
              <ng-template #thanks>
                <h3 class="mb-1">{{ 'Thank you' | translate }}</h3>
                <p>{{ thanksText }}</p>
                <a class="btn btn-outline-primary" routerLink="/listings">{{ 'See more properties' | translate }}</a>
              </ng-template>
            </aside>
          </div>
        </ng-container>
      </main>
    </div>
    <app-niwe-footer [fixed]="true"></app-niwe-footer>
  `,
  styles: [`
    .pd-page { min-height: 100vh; display: flex; flex-direction: column; }
    .pd-bar { border-bottom: 1px solid var(--border); background: var(--surface); padding: 0.7rem 1rem; }
    .pd-inner { max-width: 1100px; margin: 0 auto; width: 100%; }
    .pd-main { flex: 1; padding: 1.2rem 1rem 3rem; }
    .pd-grid { display: grid; grid-template-columns: minmax(0, 1fr) 21rem; gap: 1.5rem; align-items: start; }
    @media (max-width: 860px) { .pd-grid { grid-template-columns: 1fr; } }
    .pd-photo { aspect-ratio: 16 / 10; background: rgba(127,127,127,0.15); border-radius: 6px; overflow: hidden; }
    .pd-photo img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .pd-nophoto { height: 100%; display: flex; align-items: center; justify-content: center; font-size: 4rem; }
    .pd-thumbs { display: flex; gap: 0.4rem; margin-top: 0.5rem; flex-wrap: wrap; }
    .pd-thumbs img { width: 5rem; height: 3.6rem; object-fit: cover; border-radius: 4px; cursor: pointer; opacity: 0.75; border: 2px solid transparent; }
    .pd-thumbs img.on { opacity: 1; border-color: var(--accent); }
    .pd-price { font-size: 1.6rem; font-weight: 600; }
    .pd-facts { display: flex; flex-wrap: wrap; gap: 0.6rem; }
    .pd-facts div { border: 1px solid var(--border); border-radius: 6px; padding: 0.45rem 0.8rem; text-align: center; min-width: 5.5rem; }
    .pd-facts strong { display: block; font-size: 1.15rem; }
    .pd-facts span { font-size: 0.78rem; color: var(--muted); }
    .pd-form { padding: 1.2rem; position: sticky; top: 1rem; }
  `],
})
export class PublicListingDetailComponent implements OnInit {
  d: PublicDetail | null = null;
  info: PublicInfo | null = null;
  businessName: string | null = null;
  logoDataUrl: string | null = null;
  current: number | null = null;
  error: string | null = null;
  formError: string | null = null;
  sending = false;
  sent = false;
  thanksText = '';
  f = { name: '', phone: '', email: '', message: '', website: '' };

  constructor(private api: PublicRealEstateApiService, private route: ActivatedRoute, private branding: PublicSettingsApiService) {}

  ngOnInit(): void {
    this.branding.branding().subscribe({ next: (b) => { this.businessName = b.businessName; this.logoDataUrl = b.logoDataUrl; }, error: () => {} });
    this.api.info().subscribe({ next: (i) => (this.info = i), error: () => {} });
    this.api.detail(Number(this.route.snapshot.paramMap.get('id'))).subscribe({
      next: (d) => (this.d = d),
      error: (e) => (this.error = e?.status === 404 ? 'This property is no longer listed.' : e?.error?.message ?? 'Could not load the property.'),
    });
  }

  photo(id: number): string { return this.api.photoUrl(this.d!.id, id); }
  typeName(t: string): string { return typeLabel(t); }
  purposeName(p: string): string { return (PURPOSE_LABEL as Record<string, string>)[p] ?? p; }

  priceText(): string {
    const f = (n: number) => Math.round(n).toLocaleString();
    const parts: string[] = [];
    if (this.d!.salePrice != null) parts.push(f(this.d!.salePrice!));
    if (this.d!.rentPrice != null) parts.push(`${f(this.d!.rentPrice!)} / month`);
    return parts.length ? parts.join(' or ') : 'Price on request';
  }

  send(): void {
    this.formError = null;
    const f = this.f;
    if (!f.name.trim()) { this.formError = 'Please tell us your name.'; return; }
    if (!f.phone.trim() && !f.email.trim()) { this.formError = 'Give a phone number or an e-mail so we can reach you.'; return; }
    this.sending = true;
    this.api.enquire(this.d!.id, { name: f.name.trim(), phone: f.phone.trim() || null, email: f.email.trim() || null, message: f.message.trim() || null, website: f.website }).subscribe({
      next: (r) => { this.sending = false; this.sent = true; this.thanksText = r.message; },
      error: (e) => { this.sending = false; this.formError = e?.error?.message ?? 'Could not send your enquiry. Please try again.'; },
    });
  }
}
