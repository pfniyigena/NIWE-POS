/**
 * Production defaults — this file replaces environment.ts at build time for the "production"
 * configuration (see angular.json fileReplacements), so `npm run build` always ships this
 * version, never the dev one, with no manual editing step to remember or forget.
 *
 * Relative on purpose: nginx serves this Angular build and proxies /api/* to the backend on the
 * same origin, so this works whether the site is reached by IP, domain, http, or https, with no
 * rebuild needed if any of those change. Only hardcode an absolute URL here if the frontend and
 * backend are deliberately served from different origins.
 */
export const environment = {
  production: true,
  apiBase: '/api',
};
