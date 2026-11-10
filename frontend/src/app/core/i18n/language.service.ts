import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { API_BASE } from '../constants';

export type Lang = 'en' | 'fr';
export const LANGUAGES: { code: Lang; label: string }[] = [
  { code: 'en', label: 'English' },
  { code: 'fr', label: 'Français' },
];
const KEY = 'niwe.language';

function isLang(v: unknown): v is Lang {
  return v === 'en' || v === 'fr';
}

/**
 * v1.23.0: which language the screens use. A person's own choice (kept in this browser) wins; without one, the business default from
 * Settings > System settings (which choosing the Chad chart of accounts sets to French) is used. A change reloads the page so that every
 * screen, date and number is drawn again in the new language.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  current: Lang = 'en';

  constructor(private translate: TranslateService) {}

  private personal(): Lang | null {
    try {
      const v = localStorage.getItem(KEY);
      return isLang(v) ? v : null;
    } catch {
      return null;
    }
  }

  /** Runs before the app starts (APP_INITIALIZER): decides the language and loads its texts. Never fails: English is the fallback. */
  async init(): Promise<void> {
    let lang: Lang | null = this.personal();
    if (!lang) {
      try {
        const res = await fetch(`${API_BASE}/public/settings/branding`, { signal: AbortSignal.timeout(4000) });
        const body = await res.json();
        if (isLang(body?.language)) lang = body.language;
      } catch {
        /* the server is not reachable yet: English */
      }
    }
    this.current = lang ?? 'en';
    this.translate.setDefaultLang('en');
    await firstValueFrom(this.translate.use(this.current));
    document.documentElement.lang = this.current;
  }

  /** The person's own choice. */
  choose(lang: Lang): void {
    if (lang === this.current) return;
    try {
      localStorage.setItem(KEY, lang);
    } catch {
      /* private window: the choice lasts until the page is closed only if storage works; the business default applies otherwise */
    }
    location.reload();
  }

  /** Right after the chart of accounts is chosen: the Chad chart sets French on the server, so pick it up (people with their own choice keep it). */
  async syncBusinessDefault(): Promise<void> {
    if (this.personal() !== null) return;
    try {
      const res = await fetch(`${API_BASE}/public/settings/branding`, { signal: AbortSignal.timeout(4000) });
      const body = await res.json();
      if (isLang(body?.language) && body.language !== this.current) location.reload();
    } catch {
      /* stay as is */
    }
  }

  /** After an administrator saves another business language: people who never chose their own get it straight away. */
  businessLanguageChanged(lang: string): void {
    if (this.personal() === null && isLang(lang) && lang !== this.current) location.reload();
  }
}
