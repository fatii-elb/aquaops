import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { authGuard, roleGuard } from './auth.guard';

function setup(loggedIn: boolean, roles: string[]): Router {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: AuthService,
        useValue: {
          isLoggedIn: () => loggedIn,
          hasAnyRole: (wanted: string[]) => wanted.some((role) => roles.includes(role)),
        },
      },
    ],
  });
  return TestBed.inject(Router);
}

function run(guard: any): any {
  return TestBed.runInInjectionContext(() => guard({} as any, {} as any));
}

describe('guards', () => {
  it('authGuard renvoie vers /login si non connecté', () => {
    const router = setup(false, []);
    expect(router.serializeUrl(run(authGuard))).toBe('/login');
  });

  it('authGuard laisse passer un utilisateur connecté', () => {
    setup(true, ['technicien']);
    expect(run(authGuard)).toBe(true);
  });

  it('roleGuard renvoie vers /login si non connecté', () => {
    const router = setup(false, []);
    expect(router.serializeUrl(run(roleGuard('administrateur')))).toBe('/login');
  });

  it('roleGuard renvoie vers /forbidden si le rôle manque', () => {
    const router = setup(true, ['technicien']);
    expect(router.serializeUrl(run(roleGuard('administrateur')))).toBe('/forbidden');
  });

  it('roleGuard laisse passer le bon rôle', () => {
    setup(true, ['administrateur']);
    expect(run(roleGuard('administrateur'))).toBe(true);
  });
});
