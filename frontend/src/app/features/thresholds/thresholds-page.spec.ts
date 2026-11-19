import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../core/notification-stream';
import { ThresholdValues, ThresholdsResponse } from './thresholds.model';
import { ThresholdsPage } from './thresholds-page';

const DEFAULTS: ThresholdValues = { leakMinPressure: 2, leakMaxFlow: 40, leakDurationSeconds: 10, silenceThresholdSeconds: 10, tankMinLevel: 2 };
const ZONES = [{ id: 1, name: 'Zone Nord' }, { id: 2, name: 'Zone Sud' }];
const RESPONSE: ThresholdsResponse = {
  defaults: DEFAULTS,
  zones: [{ zoneId: 2, custom: true, thresholds: { ...DEFAULTS, leakMaxFlow: 55 }, updatedAt: '2026-12-10T10:00:00Z', updatedBy: 'admin' }],
};

type Fail = { fail: number; body?: unknown };

describe('ThresholdsPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<ThresholdsPage>;
  let el: HTMLElement;

  function setup(admin = true): void {
    TestBed.configureTestingModule({
      imports: [ThresholdsPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'u', hasAnyRole: () => admin, logout() {} } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ThresholdsPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  const flushWith = (req: TestRequest, body: unknown) =>
    body && typeof body === 'object' && 'fail' in body
      ? req.flush((body as Fail).body ?? {}, { status: (body as Fail).fail, statusText: 'erreur' })
      : req.flush(body as Parameters<TestRequest['flush']>[0]);

  /** Répond aux lectures en attente : seuils et zones. */
  function respond(thresholds: ThresholdsResponse | Fail = RESPONSE, zones: unknown[] | Fail = ZONES): void {
    for (const req of http.match((r) => r.method === 'GET')) {
      if (req.request.url === '/api/detection/thresholds') {
        flushWith(req, thresholds);
      } else if (req.request.url === '/api/assets/zones') {
        flushWith(req, zones);
      }
    }
    fixture.detectChanges();
  }

  const q = <T extends Element = HTMLElement>(sel: string) => el.querySelector<T>(sel);
  const type = (key: string, value: string) => {
    const i = q<HTMLInputElement>(`[data-field="${key}"]`)!;
    i.value = value;
    i.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };
  const submit = () => {
    q('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();
  };
  const rowOf = (zone: number) => q(`tr[data-zone="${zone}"]`)!;
  const putRequest = (zone: number) => http.expectOne({ method: 'PUT', url: `/api/detection/thresholds/${zone}` });

  beforeEach(() => TestBed.resetTestingModule());

  /* ---------------- consultation ---------------- */

  it('liste les zones avec leurs seuils, personnalisés ou par défaut', () => {
    setup();
    respond();
    expect(q('[data-testid="defaults"]')!.textContent).toContain('Débit maximal');
    expect(rowOf(1).textContent).toContain('Zone Nord');
    expect(rowOf(1).querySelector('[data-testid="status-default"]')).not.toBeNull();
    expect(rowOf(2).querySelector('[data-testid="status-custom"]')).not.toBeNull();
    expect(rowOf(2).querySelector('[data-col="leakMaxFlow"]')!.textContent).toContain('55');
    expect(rowOf(1).querySelector('[data-col="leakMaxFlow"]')!.textContent).toContain('40');
  });

  it('affiche un squelette de chargement avant la réponse', () => {
    setup();
    expect(q('[data-testid="state-loading"]')).not.toBeNull();
    respond();
    expect(q('[data-testid="state-loading"]')).toBeNull();
  });

  it('affiche l’erreur serveur et recharge au clic sur Réessayer', () => {
    setup();
    respond({ fail: 503 });
    expect(q('[data-testid="state-error"]')!.textContent).toContain('erreur');
    q('[data-testid="state-retry"]')!.click();
    respond();
    expect(q('[data-testid="state-error"]')).toBeNull();
    expect(rowOf(1)).not.toBeNull();
  });

  it('un refus 403 à la lecture est expliqué sans bouton Réessayer', () => {
    setup();
    respond({ fail: 403 });
    expect(q('[data-testid="state-error"]')!.textContent).toContain('rôle');
    expect(q('[data-testid="state-retry"]')).toBeNull();
  });

  it('affiche un état vide quand aucune zone n’existe', () => {
    setup();
    respond({ defaults: DEFAULTS, zones: [] }, []);
    expect(q('[data-testid="state-empty"]')!.textContent).toContain('Aucune zone');
  });

  /* ---------------- modification ---------------- */

  it('modifie les seuils d’une zone : formulaire prérempli, PUT, confirmation, rechargement', () => {
    setup();
    respond();
    q('tr[data-zone="1"] [data-action="edit"]')!.click();
    fixture.detectChanges();
    expect(q<HTMLInputElement>('[data-field="tankMinLevel"]')!.value).toBe('2');

    type('tankMinLevel', '3.5');
    type('leakMaxFlow', '55');
    submit();
    submit(); // double soumission : une seule requête
    const req = putRequest(1);
    expect(req.request.body).toEqual({ ...DEFAULTS, tankMinLevel: 3.5, leakMaxFlow: 55 });
    req.flush({ zoneId: 1, custom: true, thresholds: { ...DEFAULTS, tankMinLevel: 3.5, leakMaxFlow: 55 }, updatedAt: null, updatedBy: 'admin' });
    fixture.detectChanges();

    expect(q('[data-testid="toast"]')!.textContent).toContain('enregistrés');
    expect(q('[data-testid="threshold-form"]')).toBeNull();

    // Rechargement : la zone devient « personnalisée » avec les nouvelles valeurs
    respond({ defaults: DEFAULTS, zones: [...RESPONSE.zones, { zoneId: 1, custom: true, thresholds: { ...DEFAULTS, tankMinLevel: 3.5 }, updatedAt: null, updatedBy: 'admin' }] });
    expect(rowOf(1).querySelector('[data-testid="status-custom"]')).not.toBeNull();
    expect(rowOf(1).querySelector('[data-col="tankMinLevel"]')!.textContent).toContain('3,5');
  });

  it('refuse un seuil invalide sans appeler l’API', () => {
    setup();
    respond();
    q('tr[data-zone="1"] [data-action="edit"]')!.click();
    fixture.detectChanges();
    type('leakMaxFlow', '-5');
    submit();
    expect(q('[data-error="leakMaxFlow"]')).not.toBeNull();
    http.expectNone({ method: 'PUT', url: '/api/detection/thresholds/1' });
  });

  it('affiche les erreurs de validation du serveur (400) sans fermer le formulaire', () => {
    setup();
    respond();
    q('tr[data-zone="1"] [data-action="edit"]')!.click();
    fixture.detectChanges();
    submit();
    putRequest(1).flush({ errors: [{ field: 'leakMaxFlow', message: 'Ce seuil doit etre strictement positif.' }] }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(q('[data-error="leakMaxFlow"]')!.textContent).toContain('strictement positif');
    expect(q('[data-testid="form-error"]')!.textContent).toContain('refusées');
    expect(q('[data-testid="threshold-form"]')).not.toBeNull();
  });

  it('un refus 403 à l’enregistrement est expliqué dans le formulaire', () => {
    setup();
    respond();
    q('tr[data-zone="1"] [data-action="edit"]')!.click();
    fixture.detectChanges();
    submit();
    putRequest(1).flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(q('[data-testid="form-error"]')!.textContent).toContain('administrateur');
    expect(q('[data-testid="threshold-form"]')).not.toBeNull();
  });

  it('une panne du service est signalée, le formulaire reste ouvert pour réessayer', () => {
    setup();
    respond();
    q('tr[data-zone="1"] [data-action="edit"]')!.click();
    fixture.detectChanges();
    submit();
    putRequest(1).flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(q('[data-testid="form-error"]')!.textContent).toContain('pas été enregistrés');
    expect(q('[data-testid="toast"]')).not.toBeNull();
    expect(q('[data-testid="threshold-form"]')).not.toBeNull();
  });

  /* ---------------- permissions ---------------- */

  it('sans le rôle administrateur : consultation seulement, aucun bouton de modification', () => {
    setup(false);
    respond();
    expect(rowOf(1)).not.toBeNull();
    expect(q('[data-action="edit"]')).toBeNull();
  });
});
