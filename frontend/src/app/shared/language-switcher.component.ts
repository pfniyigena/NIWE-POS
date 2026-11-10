import { Component } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { CommonModule } from '@angular/common';
import { LANGUAGES, LanguageService, Lang } from '../core/i18n/language.service';

/** A small English / Français picker for the topbar and the sign-in page. */
@Component({
  selector: 'app-language-switcher',
  standalone: true,
  imports: [TranslatePipe, CommonModule],
  template: `
    <select class="form-select" style="width:auto; display:inline-block; padding:2px 6px; font-size:0.85rem;" [attr.aria-label]="'Language' | translate" [title]="'Language' | translate"
            [value]="lang.current" (change)="pick($any($event.target).value)">
      <option *ngFor="let l of languages" [value]="l.code">{{ l.label }}</option>
    </select>
  `,
})
export class LanguageSwitcherComponent {
  languages = LANGUAGES;
  constructor(public lang: LanguageService) {}
  pick(code: Lang): void {
    this.lang.choose(code);
  }
}
