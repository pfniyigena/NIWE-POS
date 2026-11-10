import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { ShellComponent } from '../shared/shell.component';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../core/auth.service';
import { CUSTOM_ID, THEMES, ThemeService, contrastRatio, customVariables, isHex } from '../core/theme.service';

/** Settings > Appearance: every signed-in user picks their own colour theme. */
@Component({
  selector: 'app-appearance',
  standalone: true,
  imports: [TranslatePipe, CommonModule, FormsModule, ShellComponent],
  template: `
    <app-shell>
      <h3 class="mb-2">{{ 'Appearance' | translate }}</h3>
      <p class="text-muted" style="font-size:0.85rem; margin-bottom:1rem;">
        {{ 'Choose the colours you work with. It is saved on the server for your account, so it follows you to any device — other staff keep their own.' | translate }}
      </p>
      <div class="theme-section-title">{{ 'Dark' | translate }}</div>
      <div class="theme-grid">
        <button type="button" class="theme-card" *ngFor="let t of dark" [class.selected]="theme.current() === t.id" (click)="theme.select(t.id)">
          <span class="theme-swatch" [style.background]="t.swatch[0]">
            <span class="theme-swatch-card" [style.background]="t.swatch[1]"><span class="theme-swatch-bar" [style.background]="t.swatch[2]"></span></span>
          </span>
          <span class="theme-name">{{ t.label }}</span>
          <span class="theme-check" *ngIf="theme.current() === t.id">✓</span>
        </button>
      </div>
      <div class="theme-section-title">{{ 'Light' | translate }}</div>
      <div class="theme-grid">
        <button type="button" class="theme-card" *ngFor="let t of light" [class.selected]="theme.current() === t.id" (click)="theme.select(t.id)">
          <span class="theme-swatch" [style.background]="t.swatch[0]">
            <span class="theme-swatch-card" [style.background]="t.swatch[1]"><span class="theme-swatch-bar" [style.background]="t.swatch[2]"></span></span>
          </span>
          <span class="theme-name">{{ t.label }}</span>
          <span class="theme-check" *ngIf="theme.current() === t.id">✓</span>
        </button>
      </div>
      <div class="theme-section-title">{{ 'Your own colours' | translate }}</div>
      <div class="theme-grid">
        <button type="button" class="theme-card" [class.selected]="theme.current() === customId" (click)="chooseCustom()">
          <span class="theme-swatch" [style.background]="preview.bg">
            <span class="theme-swatch-card" [style.background]="preview.surface"><span class="theme-swatch-bar" [style.background]="theme.custom().accent"></span></span>
          </span>
          <span class="theme-name">{{ 'Custom' | translate }}</span>
          <span class="theme-check" *ngIf="theme.current() === customId">✓</span>
        </button>
      </div>
      <div class="card" style="padding:1rem; margin-top:0.75rem; max-width:34rem;">
        <div class="text-muted" style="font-size:0.82rem; margin-bottom:0.75rem;">
          {{ 'Pick a page background and an accent colour (buttons, links, highlights). Cards, borders and text colours are worked out for you, and a light or dark layout is chosen from your background.' | translate }}
        </div>
        <div class="flex gap-2" style="flex-wrap:wrap;">
          <div class="form-group" style="min-width:12rem;">
            <label class="form-label">{{ 'Background' | translate }}</label>
            <div class="flex gap-2 align-center">
              <input type="color" [ngModel]="bgPick" (ngModelChange)="onBg($event)" name="bgPick" style="width:2.6rem; height:2.2rem; padding:0; border:none; background:none;" />
              <input class="form-control" [ngModel]="bgText" (ngModelChange)="onBg($event)" name="bgText" maxlength="7" style="width:7.5rem;" [placeholder]="'#dbeafe' | translate" />
            </div>
          </div>
          <div class="form-group" style="min-width:12rem;">
            <label class="form-label">{{ 'Accent' | translate }}</label>
            <div class="flex gap-2 align-center">
              <input type="color" [ngModel]="accentPick" (ngModelChange)="onAccent($event)" name="accentPick" style="width:2.6rem; height:2.2rem; padding:0; border:none; background:none;" />
              <input class="form-control" [ngModel]="accentText" (ngModelChange)="onAccent($event)" name="accentText" maxlength="7" style="width:7.5rem;" [placeholder]="'#2563eb' | translate" />
            </div>
          </div>
        </div>
        <div class="alert alert-warning" *ngIf="lowContrast">
          {{ 'This accent is hard to read on that background — links and highlights may blend in. Try a darker or lighter accent.' | translate }}
        </div>
        <div class="alert alert-danger" *ngIf="invalid">{{ 'Use a 6-digit hex colour such as #2563eb.' | translate }}</div>
        <button type="button" class="btn btn-sm btn-outline-secondary" (click)="resetCustom()">{{ 'Reset to sky blue' | translate }}</button>
      </div>
      <div class="card" style="padding:1rem; margin-top:1rem; max-width:34rem;" *ngIf="isAdmin">
        <label class="flex gap-2 align-center"><input type="checkbox" class="ap-website" name="applyToWebsite" [ngModel]="theme.applyToWebsite()" (ngModelChange)="theme.applyToWebsite.set($event)" /> {{ 'Also use my choice for the public website and sign-in page' | translate }}</label>
        <div class="text-muted" style="font-size:0.82rem; margin-top:0.35rem;">{{ 'Visitors who are not signed in see the theme you pick here. Untick it to change only your own look.' | translate }}</div>
      </div>
      <p class="text-muted" style="font-size:0.78rem; margin-top:1rem;">
        {{ 'Printed receipts, purchase orders and reports always stay white so they print cleanly.' | translate }}
      </p>
    </app-shell>
  `,
  styles: [
    `
      .theme-section-title { font-size: 0.8rem; font-weight: 700; letter-spacing: 0.05em; text-transform: uppercase; color: var(--muted); margin: 1rem 0 0.5rem; }
      .theme-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 0.75rem; }
      .theme-card { position: relative; display: flex; flex-direction: column; gap: 0.5rem; padding: 0.6rem; border: 2px solid var(--border); border-radius: var(--radius); background: var(--surface); color: var(--text); cursor: pointer; text-align: left; font: inherit; }
      .theme-card:hover { border-color: var(--accent); }
      .theme-card.selected { border-color: var(--accent); box-shadow: 0 0 0 1px var(--accent); }
      .theme-swatch { display: block; height: 64px; border-radius: 6px; padding: 8px; border: 1px solid rgba(128, 128, 128, 0.3); }
      .theme-swatch-card { display: block; height: 100%; border-radius: 4px; padding: 6px; }
      .theme-swatch-bar { display: block; height: 10px; width: 60%; border-radius: 3px; }
      .theme-name { font-size: 0.85rem; font-weight: 600; }
      .theme-check { position: absolute; top: 0.4rem; right: 0.6rem; color: var(--accent); font-weight: 700; }
    `,
  ],
})
export class AppearanceComponent {
  readonly dark = THEMES.filter((t) => t.dark);
  readonly light = THEMES.filter((t) => !t.dark);

  readonly customId = CUSTOM_ID;
  bgText = '';
  accentText = '';
  invalid = false;

  get isAdmin(): boolean {
    return this.auth.currentUser()?.role === 'ADMIN';
  }

  constructor(public theme: ThemeService, private auth: AuthService) {
    this.bgText = theme.custom().bg;
    this.accentText = theme.custom().accent;
  }

  /** The colour picker needs a valid 6-digit hex; fall back to the saved colour while the text box is half-typed. */
  get bgPick(): string {
    return isHex(this.bgText) ? this.bgText : this.theme.custom().bg;
  }
  get accentPick(): string {
    return isHex(this.accentText) ? this.accentText : this.theme.custom().accent;
  }

  /** Colours for the Custom card's preview swatch. */
  get preview(): { bg: string; surface: string } {
    const v = customVariables(this.theme.custom()).vars;
    return { bg: v['--bg'], surface: v['--surface'] };
  }

  get lowContrast(): boolean {
    const c = this.theme.custom();
    return contrastRatio(c.accent, c.bg) < 3;
  }

  chooseCustom(): void {
    this.theme.setCustom(this.theme.custom());
  }

  onBg(value: string): void {
    this.bgText = this.normalise(value);
    this.commit();
  }

  onAccent(value: string): void {
    this.accentText = this.normalise(value);
    this.commit();
  }

  resetCustom(): void {
    this.bgText = '#dbeafe';
    this.accentText = '#2563eb';
    this.commit();
  }

  private normalise(value: string): string {
    const v = (value ?? '').trim();
    return v && !v.startsWith('#') ? '#' + v : v;
  }

  /** Applies live as soon as both boxes hold valid hex codes. */
  private commit(): void {
    this.invalid = !(isHex(this.bgText) && isHex(this.accentText));
    if (!this.invalid) this.theme.setCustom({ bg: this.bgText, accent: this.accentText });
  }
}
