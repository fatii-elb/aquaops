import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { routes } from '../../app.routes';

function runGuard(auth: { isLoggedIn: () => boolean; hasAnyRole: (r: string[]) => boolean }): string | boolean {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
  const route = routes.find((r) => r.path === 'assets');
  const guard = route?.canActivate?.[0] as CanActivateFn;
  const result = TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : (result as boolean);
}

describe('route /assets (FE-3)', () => {
  it('est déclarée et protégée par un garde', () => {
    const route = routes.find((r) => r.path === 'assets');
    expect(route).toBeDefined();
    expect(route?.canActivate?.length).toBe(1);
  });

  it("laisse passer l'administrateur", () => {
    expect(runGuard({ isLoggedIn: () => true, hasAnyRole: (r) => r.includes('administrateur') })).toBe(true);
  });

  it('renvoie un superviseur vers /forbidden', () => {
    expect(runGuard({ isLoggedIn: () => true, hasAnyRole: (r) => r.includes('superviseur') })).toBe('/forbidden');
  });

  it('renvoie un visiteur non connecté vers /login', () => {
    expect(runGuard({ isLoggedIn: () => false, hasAnyRole: () => false })).toBe('/login');
  });
});
