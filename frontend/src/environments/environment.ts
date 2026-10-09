/**
 * Development defaults — used by `ng serve` / `npm start`. Swapped out for environment.prod.ts
 * automatically whenever a production build runs (see angular.json's fileReplacements under the
 * "production" configuration), so this file is never the one that ends up on a server.
 */
export const environment = {
  production: false,
  /** The Spring Boot API running locally via `mvn spring-boot:run` (or the `demo` profile). */
  apiBase: 'http://localhost:8080/api',
};
