import { environment } from '../../environments/environment';

/**
 * Base URL of the Spring Boot REST API. Comes from src/environments/ — environment.ts for
 * `ng serve` (dev), environment.prod.ts for a production build, swapped automatically by
 * angular.json's fileReplacements. Never hardcode this here directly; edit the environment files
 * instead, so a production build can't accidentally ship a dev URL (or vice versa).
 */
export const API_BASE = environment.apiBase;
