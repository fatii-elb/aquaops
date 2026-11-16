import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../core/notification-stream';
import { ComingSoon } from './coming-soon';
import { routes } from '../../app.routes';

function render(data: Record<string, unknown>): HTMLElement {
  TestBed.configureTestingModule({
    imports: [ComingSoon],
    providers: [
      provideRouter([]),
      provideHttpClient(),
      provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { snapshot: { data } } },
      { provide: AuthService, useValue: { username: 'u', hasAnyRole: () => true, logout() {} } },
      { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
    ],
  });
  const fixture = TestBed.createComponent(ComingSoon);
  fixture.detectChanges();
  return fixture.nativeElement;
}

describe('ComingSoon', () => {
  it('affiche le titre et la description fournis par la route', () => {
    const el = render({ title: 'Rapports & Analyses', active: 'reports', icon: 'analytics', description: 'Bientôt des rapports.' });
    const card = el.querySelector('[data-testid="coming-soon"]')!;
    expect(card.textContent).toContain('Rapports & Analyses');
    expect(card.textContent).toContain('Bientôt des rapports.');
    expect(card.textContent).toContain('Bientôt disponible');
  });

  it('a des valeurs par défaut sans données de route', () => {
    expect(render({}).querySelector('[data-testid="coming-soon"]')!.textContent).toContain('Bientôt disponible');
  });
});

describe('routes des pages d\'attente (FE-6)', () => {
  it.each(['telemetry', 'reports', 'observability'])('/%s existe et exige une connexion', (path) => {
    const route = routes.find((r) => r.path === path);
    expect(route).toBeDefined();
    expect(route?.canActivate?.length).toBe(1);
    expect(route?.data?.['title']).toBeTruthy();
    expect(route?.data?.['active']).toBe(path);
  });

  it('les routes existantes sont conservées', () => {
    for (const path of ['login', 'dashboard', 'incidents', 'incidents/:id', 'assets', 'administration', 'audit', 'forbidden']) {
      expect(routes.some((r) => r.path === path)).toBe(true);
    }
  });
});
