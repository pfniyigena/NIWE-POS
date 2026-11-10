# Translating screens (v1.23.0)

The English text written in an Angular template is the translation key: `{{ 'Save' | translate }}`. English needs no file; a text with no French shows English.

1. `cd frontend` and run `node ../tools/i18n/transform.mjs src/app/<module>/*.component.ts` (add `--dry` to look first).
   It turns template text, `placeholder`/`title`/`alt`/`aria-label`/`label` attributes and `cond ? 'A' : 'B'` into translate calls, adds `TranslatePipe` to the component, and lists the new phrases in `tools/i18n/keys.json`. It prints what it could not convert (`leftovers`): fix those by hand with `| translate`.
2. Add each new English phrase and its French to `frontend/src/app/core/i18n/fr.json`. A `{p0}` in a phrase is a value filled in by the screen; keep it in the French text.
3. `node ../tools/i18n/build-fr.mjs` writes `fr.ts` (commit both).
4. Text written in TypeScript (messages, `confirm()` texts, labels held in arrays) is not touched by the script: use `TranslateService.instant('English text')` or `| translate` where it is shown.
