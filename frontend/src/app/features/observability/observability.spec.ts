import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../core/notification-stream';
import { FakeEventSource } from '../incidents/test-helpers';
import { Observability } from './observability';
import { ServiceStatus, SERVICE_LABELS } from './service-status.model';
import { ServiceStatusDataSource } from './service-status-data-source';

class StubSource extends ServiceStatusDataSource {
  constructor(public fail = false) {
    super();
  }

  getStatuses(): Observable<ServiceStatus[]> {
    if (this.fail) {
      return throwError(() => ({ status: 0 }));
    }
    const now = new Date().toISOString();
    return of(
      (Object.keys(SERVICE_LABELS) as Array<keyof typeof SERVICE_LABELS>).map((key): ServiceStatus => {
        const state: ServiceStatus['state'] =
          key === 'incidents' ? 'DOWN' : key === 'gateway' ? 'UNKNOWN' : 'UP';
        return { key, label: SERVICE_LABELS[key], state, checkedAt: now };
      }),
    );
  }
}

async function render(source: ServiceStatusDataSource) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: ServiceStatusDataSource, useValue: source },
      { provide: AuthService, useValue: { username: 'sup', hasAnyRole: () => true, logout: () => undefined } },
      { provide: EVENT_SOURCE_FACTORY, useValue: () => new FakeEventSource() },
    ],
  });
  const fixture = TestBed.createComponent(Observability);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const q = (testId: string) => root.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null;
  const text = (testId: string) => q(testId)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  return { fixture, root, q, text };
}

describe('Observability (FE-11)', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('affiche les 6 services avec leur état (UP, DOWN, UNKNOWN)', async () => {
    const { text } = await render(new StubSource());
    expect(text('services')).toContain('Télémétrie');
    expect(text('services')).toContain('Notifications');
    expect(text('state-telemetry')).toBe('UP');
    expect(text('state-incidents')).toBe('DOWN');
    expect(text('state-gateway')).toBe('INCONNU');
  });

  it('affiche « Supervision indisponible » quand la source échoue', async () => {
    const { text } = await render(new StubSource(true));
    expect(text('error')).toContain('Supervision indisponible');
    expect(text('services')).toBe('');
  });

  it('« Réessayer » recharge les services', async () => {
    const source = new StubSource(true);
    const { fixture, q, text } = await render(source);
    expect(text('error')).toContain('Supervision indisponible');
    source.fail = false;
    (q('retry') as HTMLButtonElement).click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(text('error')).toBe('');
    expect(text('services')).toContain('Télémétrie');
  });
});

function runGuard(auth: { isLoggedIn: () => boolean; hasAnyRole: (r: string[]) => boolean }): string | boolean {
  TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: AuthService, useValue: auth }] });
  const route = routes.find((r) => r.path === 'observability');
  const guard = route?.canActivate?.[0] as CanActivateFn;
  const result = TestBed.runInInjectionContext(() => guard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot));
  return result instanceof UrlTree ? TestBed.inject(Router).serializeUrl(result) : (result as boolean);
}

describe('route /observability (FE-11)', () => {
  it('est déclarée avec Observability et un garde', () => {
    const route = routes.find((r) => r.path === 'observability');
    expect(route).toBeDefined();
    expect(route?.canActivate?.length).toBe(1);
    expect(route?.providers?.length).toBe(1);
  });

  it('laisse passer le superviseur', () => {
    expect(runGuard({ isLoggedIn: () => true, hasAnyRole: (r) => r.includes('superviseur') })).toBe(true);
  });

  it("laisse passer l'administrateur", () => {
    expect(runGuard({ isLoggedIn: () => true, hasAnyRole: (r) => r.includes('administrateur') })).toBe(true);
  });

  it('renvoie un opérateur vers /forbidden (403)', () => {
    expect(runGuard({ isLoggedIn: () => true, hasAnyRole: (r) => r.includes('operateur') })).toBe('/forbidden');
  });

  it('renvoie un visiteur non connecté vers /login', () => {
    expect(runGuard({ isLoggedIn: () => false, hasAnyRole: () => false })).toBe('/login');
  });
});