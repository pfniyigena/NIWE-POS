import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { TranslateDefaultParser, TranslateLoader } from '@ngx-translate/core';
import { FR } from './fr';

/**
 * v1.23.0 languages. The English text written in a template IS the key ({{ 'Sign in' | translate }}), so English needs no file and a
 * missing French text simply shows the English one. French lives in fr.ts (generated from fr.json; see tools/i18n/README.md).
 */
@Injectable()
export class StaticTranslateLoader implements TranslateLoader {
  getTranslation(lang: string): Observable<Record<string, string>> {
    return of(lang === 'fr' ? FR : {});
  }
}

/** Values in the files use single braces for parameters, "Total: {p0} items", so text with {{ }} never clashes with Angular's own syntax. */
@Injectable()
export class SingleBraceParser extends TranslateDefaultParser {
  override templateMatcher = /\{\s?([^{}\s]*)\s?\}/g;
}
