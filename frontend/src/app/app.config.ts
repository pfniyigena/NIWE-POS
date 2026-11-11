import { ApplicationConfig, APP_INITIALIZER, LOCALE_ID, inject } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { MissingTranslationHandler, provideTranslateService, TranslateLoader, TranslateParser } from '@ngx-translate/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { maintenanceInterceptor } from './core/maintenance.interceptor';
import { LanguageService } from './core/i18n/language.service';
import { InterpolatingMissingHandler, SingleBraceParser, StaticTranslateLoader } from './core/i18n/translate-setup';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, maintenanceInterceptor])),
    provideTranslateService({ missingTranslationHandler: { provide: MissingTranslationHandler, useClass: InterpolatingMissingHandler }, loader: { provide: TranslateLoader, useClass: StaticTranslateLoader }, parser: { provide: TranslateParser, useClass: SingleBraceParser } }),
    { provide: APP_INITIALIZER, multi: true, useFactory: () => { const l = inject(LanguageService); return () => l.init(); } },
    { provide: LOCALE_ID, useFactory: () => inject(LanguageService).current },
  ],
};
