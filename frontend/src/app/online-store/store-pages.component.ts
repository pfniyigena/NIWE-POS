import { Component, OnInit } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ShellComponent } from '../shared/shell.component';
import { InfoPageAdmin, OnlineStoreApiService } from '../core/api/online-store-api.service';
import { LanguageService } from '../core/i18n/language.service';
import { T } from '../core/i18n/t';

/**
 * Online Store > Shop pages: the Terms, Privacy, Returns and Delivery pages of the shop. Each starts with a general text (in the language of this screen)
 * that staff must read and adapt; a page staff saved is shown as written to every visitor. A page can be switched off and the starter text restored.
 */
@Component({
  selector: 'app-store-pages',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, RouterLink, ShellComponent],
  template: `
    <app-shell>
      <div class="alert alert-danger" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="message">{{ message }}</div>
      <div class="flex justify-between align-center mb-3" style="flex-wrap:wrap; gap:0.5rem;">
        <h3>{{ 'Shop pages' | translate }}</h3>
        <div class="flex gap-2">
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/messages">{{ 'Messages' | translate }}</a>
          <a class="btn btn-outline-secondary" routerLink="/admin/online-store/setup">{{ 'Shop settings' | translate }}</a>
        </div>
      </div>
      <p class="text-muted">{{ 'These pages are linked in the footer of the shop. The texts below are general starter texts: read them, adapt them to your shop and, where the law asks for it, have them checked. They are not legal advice.' | translate }}</p>
      <p class="text-muted" style="font-size:0.85rem;">{{ 'Write paragraphs separated by an empty line. A line starting with ## is a heading and a line starting with - is a bullet. {shop}, {phone}, {email} and {address} are replaced by the details of your shop.' | translate }}</p>

      <div class="card pg-card mb-3" *ngFor="let p of pages" style="padding:1rem;">
        <div class="flex justify-between align-center" style="flex-wrap:wrap; gap:0.5rem;">
          <strong class="pg-slug">{{ labels[p.slug] | translate }}</strong>
          <span class="text-muted pg-state" style="font-size:0.85rem;">{{ (p.edited ? 'Your own text' : 'Starter text') | translate }}</span>
        </div>
        <div class="form-group mt-2"><label class="form-label">{{ 'Title' | translate }}</label><input class="form-control pg-title" [name]="'t' + p.slug" maxlength="120" [(ngModel)]="p.title" /></div>
        <div class="form-group"><label class="form-label">{{ 'Text' | translate }}</label><textarea class="form-control pg-body" [name]="'b' + p.slug" rows="14" maxlength="12000" [(ngModel)]="p.body"></textarea></div>
        <label class="flex gap-1" style="align-items:center;"><input type="checkbox" class="pg-published" [name]="'p' + p.slug" [(ngModel)]="p.published" /> {{ 'Shown on the shop' | translate }}</label>
        <div class="flex gap-2 mt-2" style="flex-wrap:wrap;">
          <button class="btn btn-primary pg-save" [disabled]="busy" (click)="save(p)">{{ 'Save' | translate }}</button>
          <button class="btn btn-outline-secondary pg-restore" *ngIf="p.edited" [disabled]="busy" (click)="restore(p)">{{ 'Back to the starter text' | translate }}</button>
          <a class="btn btn-outline-secondary pg-view" [routerLink]="['/shop/info', p.slug]" target="_blank">{{ 'See it on the shop' | translate }}</a>
        </div>
      </div>
    </app-shell>
  `,
})
export class StorePagesComponent implements OnInit {
  pages: InfoPageAdmin[] = [];
  labels: Record<string, string> = { terms: 'Terms & Conditions', privacy: 'Privacy Policy', returns: 'Returns & Refunds', delivery: 'Delivery & Shipping' };
  error: string | null = null;
  message: string | null = null;
  busy = false;

  constructor(private api: OnlineStoreApiService, private lang: LanguageService) {}

  private get l(): string {
    return this.lang.current === 'fr' ? 'fr' : 'en';
  }

  ngOnInit(): void {
    this.api.adminInfoPages(this.l).subscribe({ next: (p) => (this.pages = p), error: (e) => (this.error = e?.error?.message ?? T('Failed to load the pages')) });
  }

  private replace(updated: InfoPageAdmin): void {
    this.pages = this.pages.map((p) => (p.slug === updated.slug ? updated : p));
  }

  save(p: InfoPageAdmin): void {
    this.busy = true;
    this.error = null;
    this.message = null;
    this.api.saveInfoPage(p.slug, { title: p.title, body: p.body, published: p.published }, this.l).subscribe({
      next: (u) => { this.busy = false; this.replace(u); this.message = T('Saved'); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }

  restore(p: InfoPageAdmin): void {
    if (!confirm(T('Replace your text with the starter text?'))) return;
    this.busy = true;
    this.error = null;
    this.message = null;
    this.api.restoreInfoPage(p.slug, this.l).subscribe({
      next: (u) => { this.busy = false; this.replace(u); this.message = T('The starter text is back.'); },
      error: (e) => { this.busy = false; this.error = e?.error?.message ?? T('That did not work'); },
    });
  }
}
