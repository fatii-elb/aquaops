import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../core/notification-stream';
import { AppShell } from '../../core/layout/app-shell';

function runGuard(auth: { isLoggedIn: () => boolean; hasAnyRole: (r: string[]) => boolean }): string | boolean {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
  const guard = routes.find((r) => r.path === 'thresholds')?.canActivate?.[0] as CanActivateFn;
  const result = TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : (result as boolean);
}

function shell(roles: string[]): HTMLElement {
  TestBed.configureTestingModule({
    imports: [AppShell],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: AuthService, useValue: { username: 'u', hasAnyRole: (w: string[]) => w.some((r) => roles.includes(r)), logout() {} } },
      { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
    ],
  });
  const fixture = TestBed.createComponent(AppShell);
  fixture.detectChanges();
  return fixture.nativeElement;
}

describe('route /thresholds (FR-13)', () => {
  it('est déclarée et protégée par un garde', () => {
    const route = routes.find((r) => r.path === 'thresholds');
    expect(route).toBeDefined();
    expect(route?.canActivate?.length).toBe(1);
  });

  it('laisse passer l’administrateur, refuse les autres rôles et les visiteurs', () => {
    expect(runGuard({ isLoggedIn: () => true, hasAnyRole: (r) => r.includes('administrateur') })).toBe(true);
  });

  it('renvoie un superviseur vers /forbidden', () => {
    expect(runGuard({ isLoggedIn: () => true, hasAnyRole: (r) => r.includes('superviseur') })).toBe('/forbidden');
  });

  it('renvoie un visiteur non connecté vers /login', () => {
    expect(runGuard({ isLoggedIn: () => false, hasAnyRole: () => false })).toBe('/login');
  });
});

describe('menu : « Seuils par zone »', () => {
  it('est un lien vers /thresholds pour l’administrateur', () => {
    expect(shell(['administrateur']).querySelector('nav a[href="/thresholds"]')).not.toBeNull();
  });

  it('est masqué aux autres rôles', () => {
    expect(shell(['superviseur']).querySelector('nav a[href="/thresholds"]')).toBeNull();
  });
});
