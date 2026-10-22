import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { API_BASE } from './constants';

/** Attaches the JWT to every request, and bounces to /login if the API ever says "not authenticated anymore". */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  // The client portal has its own token and sign-in (PortalApiService). A staff token must never go to it, and a portal 401 must not log the staff user out.
  if (req.url.startsWith(`${API_BASE}/portal/`)) return next(req);
  const auth = inject(AuthService);
  const router = inject(Router);
  const token = auth.getToken();

  const authedReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authedReq).pipe(
    catchError((error) => {
      if (error?.status === 401) {
        auth.logout();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    })
  );
};
