import { TranslateService } from '@ngx-translate/core';

let service: TranslateService | null = null;

/** Set once by LanguageService, so code that is not a component (and has no TranslateService) can still translate. */
export function setTranslateService(s: TranslateService): void {
  service = s;
}

/**
 * Translates a text written in TypeScript: T('Save failed'), T('Merged {p0} records', { p0: n }). The English text is the key, as in
 * templates. Before the language is loaded (or if it never is) the English text is returned with its values filled in.
 */
export function T(text: string, params?: Record<string, unknown>): string {
  if (service) return service.instant(text, params);
  return params ? text.replace(/\{\s?([^{}\s]*)\s?\}/g, (m, k) => (k in params ? String(params[k]) : m)) : text;
}
