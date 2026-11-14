import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../../core/notification-stream';
import { FakeEventSource } from '../../incidents/test-helpers';
import { AssetsPage } from './assets-page';

describe('AssetsPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AssetsPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'admin', hasAnyRole: () => true, logout() {} } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => new FakeEventSource() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function flushLists(lists: Partial<Record<'zones' | 'pipes' | 'tanks' | 'pumps' | 'sensors', unknown[]>>): void {
    for (const key of ['zones', 'pipes', 'tanks', 'pumps', 'sensors'] as const) {
      http.expectOne(`/api/assets/${key}`).flush(lists[key] ?? []);
    }
  }

  const kpi = (el: HTMLElement, key: string) => el.querySelector(`[data-testid="kpi-${key}"]`)?.textContent?.trim();

  it('charge les cinq listes et affiche les cartes KPI calculées depuis les données', () => {
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();
    flushLists({
      zones: [{ id: 1, name: 'Zone 1', description: null }, { id: 2, name: 'Zone 2', description: null }],
      pipes: [{ id: 1, name: 'C1', zoneId: 1, lengthM: 10, diameterMm: 100 }],
      tanks: [],
      pumps: [{ id: 1, name: 'P1', zoneId: 1 }, { id: 2, name: 'P2', zoneId: 2 }, { id: 3, name: 'P3', zoneId: 2 }],
      sensors: [{ id: 1, name: 'S1', type: 'FLOW', zoneId: 1, assetType: 'PIPE', assetId: 1 }],
    });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(kpi(el, 'zones')).toBe('2');
    expect(kpi(el, 'pipes')).toBe('1');
    expect(kpi(el, 'tanks')).toBe('0');
    expect(kpi(el, 'pumps')).toBe('3');
    expect(kpi(el, 'sensors')).toBe('1');
    expect(el.querySelector('[data-testid="load-error"]')).toBeNull();
  });

  it('affiche un squelette tant que les listes ne sont pas arrivées', () => {
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="kpi-zones"]')).toBeNull();
    expect(el.querySelectorAll('[data-kpi]').length).toBe(5);
    flushLists({});
  });

  it("affiche l'erreur d'API puis recharge au clic sur Réessayer", () => {
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();
    const failing: TestRequest[] = ['zones', 'pipes', 'tanks', 'pumps', 'sensors'].map((k) => http.expectOne(`/api/assets/${k}`));
    // Dès qu'une liste échoue, forkJoin annule les autres requêtes : on n'a rien d'autre à répondre.
    failing[0].flush({}, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="load-error"]')?.textContent).toContain("Le service d'actifs a rencontré une erreur");

    el.querySelector<HTMLButtonElement>('[data-testid="load-error"] button')!.click();
    fixture.detectChanges();
    flushLists({ zones: [{ id: 1, name: 'Zone 1', description: null }] });
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="load-error"]')).toBeNull();
    expect(kpi(el, 'zones')).toBe('1');
  });

  it('le bouton Actualiser recharge les données', () => {
    const fixture = TestBed.createComponent(AssetsPage);
    fixture.detectChanges();
    flushLists({});
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="refresh"]').click();
    flushLists({ zones: [{ id: 9, name: 'Nouvelle', description: null }] });
    fixture.detectChanges();
    expect(kpi(fixture.nativeElement, 'zones')).toBe('1');
  });
});
