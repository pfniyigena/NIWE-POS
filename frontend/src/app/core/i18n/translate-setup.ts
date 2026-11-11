import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { MissingTranslationHandler, MissingTranslationHandlerParams, TranslateDefaultParser, TranslateLoader } from '@ngx-translate/core';
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

/**
 * A text that has no entry (all English text, and any phrase without French yet) is returned as the key. ngx-translate does not fill in
 * the values {p0}, {p1} for such a key, so this does it: "{p0} days left" with {p0: 3} gives "3 days left".
 */
@Injectable()
export class InterpolatingMissingHandler implements MissingTranslationHandler {
  private parser = new SingleBraceParser();
  handle(params: MissingTranslationHandlerParams): string {
    const p = params.interpolateParams;
    return p ? (this.parser.interpolate(params.key, p) ?? params.key) : params.key;
  }
}
