import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/** Page réservée aux utilisateurs connectés. Sinon : retour à /login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.isLoggedIn() ? true : router.createUrlTree(['/login']);
};

/** Page réservée à certains rôles. Non connecté : /login. Mauvais rôle : /forbidden. */
export const roleGuard = (...roles: string[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.isLoggedIn()) {
    return router.createUrlTree(['/login']);
  }
  return auth.hasAnyRole(roles) ? true : router.createUrlTree(['/forbidden']);
};
