import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { EVENT_SOURCE_FACTORY } from '../../core/notification-stream';
import { Incident } from '../incidents/incident.model';
import { AnomaliesPage } from './anomalies-page';
import { Anomaly, AnomalyPage } from './anomaly.model';

const A1: Anomaly = {
  id: 'a1', type: 'LEAK_SUSPECTED', zoneId: 1, sensorId: 12, severity: 'CRITICAL', status: 'OPEN',
  measuredAt: '2026-11-14T10:00:10Z', detectedAt: '2026-11-14T10:00:12Z', resolvedAt: null,
};
const A2: Anomaly = {
  id: 'a2', type: 'PUMP_FAULT', zoneId: 2, sensorId: 5, severity: 'HIGH', status: 'RESOLVED',
  measuredAt: '2026-11-14T09:00:00Z', detectedAt: '2026-11-14T09:00:01Z', resolvedAt: '2026-11-14T09:05:00Z',
};
const INCIDENT = { id: 7, anomalyId: 'a1', zoneId: 1, sensorId: '12', type: 'LEAK_SUSPECTED', status: 'OPEN', priority: 'CRITICAL', description: null, assignee: null, measuredAt: null, createdAt: '', updatedAt: '' } as Incident;
const ZONES = [{ id: 1, name: 'Zone Nord' }, { id: 2, name: 'Zone Sud' }];
const SENSORS = [{ id: 12, name: 'Pression P-12' }, { id: 5, name: 'Pompe P-5' }];

const page = (content: Anomaly[], total = content.length, p = 0): AnomalyPage => ({
  content, page: p, size: 20, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 20)),
});

type Fail = { fail: number };
interface Replies {
  main?: AnomalyPage | Fail;
  open?: number;
  incidents?: Incident[] | Fail;
  zones?: unknown[] | Fail;
  sensors?: unknown[] | Fail;
}

describe('AnomaliesPage', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AnomaliesPage>;
  let el: HTMLElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AnomaliesPage],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'u', hasAnyRole: () => false, logout() {} } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => ({ onopen: null, onerror: null, onmessage: null, close() {} }) },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => vi.useRealTimers());

  function start(): void {
    fixture = TestBed.createComponent(AnomaliesPage);
    el = fixture.nativeElement;
    fixture.detectChanges();
  }

  const flushWith = (req: TestRequest, body: unknown) =>
    body && typeof body === 'object' && 'fail' in body
      ? req.flush({}, { status: (body as Fail).fail, statusText: 'erreur' })
      : req.flush(body as Parameters<TestRequest['flush']>[0]);

  /** Répond à toutes les requêtes en attente (liste, compteur, incidents, zones, capteurs). */
  function respond(r: Replies = {}): void {
    for (const req of http.match(() => true)) {
      const url = req.request.url;
      if (url === '/api/detection/anomalies') {
        const isCount = req.request.params.get('size') === '1';
        flushWith(req, isCount ? page([], r.open ?? 3, 0) : (r.main ?? page([A1, A2], 2)));
      } else if (url === '/api/incidents') {
        flushWith(req, r.incidents ?? [INCIDENT]);
      } else if (url === '/api/assets/zones') {
        flushWith(req, r.zones ?? ZONES);
      } else if (url === '/api/assets/sensors') {
        flushWith(req, r.sensors ?? SENSORS);
      }
    }
    fixture.detectChanges();
  }

  const q = <T extends Element = HTMLElement>(sel: string) => el.querySelector<T>(sel);
  const rows = () => el.querySelectorAll('[data-testid="anomaly-row"]');
  const row = (id: string) => q(`[data-testid="anomaly-row"][data-id="${id}"]`)!;
  const mainRequests = () => http.match((r) => r.url === '/api/detection/anomalies' && r.params.get('size') !== '1');

  /* ---------------- contenu ---------------- */

  it('affiche chaque anomalie avec type, zone, capteur, gravité, statut et incident associé', () => {
    start();
    respond();
    expect(rows().length).toBe(2);
    const first = row('a1').textContent!;
    for (const expected of ['Fuite probable', 'Zone Nord', 'Pression P-12', 'Critique', 'Ouverte', 'Incident n° 7']) {
      expect(first).toContain(expected);
    }
    expect(row('a2').textContent).toContain('Pompe en défaut');
    expect(row('a2').querySelector('[data-testid="incident-link"]')).toBeNull();
  });

  it('affiche le compteur d’anomalies ouvertes, dans la page et dans le menu', () => {
    start();
    respond({ open: 3 });
    expect(q('[data-testid="kpi-open"]')!.textContent!.trim()).toBe('3');
    expect(q('[data-testid="alerts-count"]')!.textContent!.trim()).toBe('3');
    expect(q('[data-testid="kpi-total"]')!.textContent!.trim()).toBe('2');
    expect(q('[data-testid="kpi-linked"]')!.textContent!.trim()).toBe('1');
  });

  it('affiche un squelette de chargement avant la réponse', () => {
    start();
    expect(q('[data-testid="state-loading"]')).not.toBeNull();
    respond();
    expect(q('[data-testid="state-loading"]')).toBeNull();
  });

  it('affiche un état vide quand il n’y a aucune anomalie', () => {
    start();
    respond({ main: page([]), open: 0 });
    expect(q('[data-testid="state-empty"]')!.textContent).toContain('Aucune anomalie détectée');
    expect(q('[data-testid="alerts-count"]')).toBeNull();
  });

  /* ---------------- erreurs ---------------- */

  it('affiche l’erreur serveur et recharge au clic sur Réessayer', () => {
    start();
    respond({ main: { fail: 503 } });
    expect(q('[data-testid="state-error"]')!.textContent).toContain('Le service a rencontré une erreur');
    expect(rows().length).toBe(0);

    q('[data-testid="state-retry"]')!.click();
    respond();
    expect(q('[data-testid="state-error"]')).toBeNull();
    expect(rows().length).toBe(2);
  });

  it('un refus 403 est expliqué, sans bouton Réessayer', () => {
    start();
    respond({ main: { fail: 403 } });
    expect(q('[data-testid="state-error"]')!.textContent).toContain('rôle');
    expect(q('[data-testid="state-retry"]')).toBeNull();
  });

  it('la liste reste affichée quand le service des incidents est en panne', () => {
    start();
    respond({ incidents: { fail: 503 } });
    expect(rows().length).toBe(2);
    expect(q('[data-testid="incident-link"]')).toBeNull();
  });

  it('retombe sur les identifiants quand les noms de zone et de capteur sont indisponibles', () => {
    start();
    respond({ zones: { fail: 500 }, sensors: { fail: 500 } });
    expect(row('a1').textContent).toContain('Zone 1');
    expect(row('a1').textContent).toContain('Capteur 12');
  });

  /* ---------------- filtres, tri, pagination ---------------- */

  it('filtre par statut côté serveur et revient à la première page', () => {
    start();
    respond();
    const select = q<HTMLSelectElement>('[data-testid="filter-status"]')!;
    select.selectedIndex = 1; // Ouvertes
    select.dispatchEvent(new Event('change'));
    const [req] = mainRequests();
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(page([A1]));
    respond();
    expect(rows().length).toBe(1);
  });

  it('filtre par zone, type et période', () => {
    start();
    respond();
    const zone = q<HTMLSelectElement>('[data-testid="filter-zone"]')!;
    zone.selectedIndex = 2; // Zone Sud
    zone.dispatchEvent(new Event('change'));
    expect(mainRequests()[0].request.params.get('zoneId')).toBe('2');
    respond();

    const type = q<HTMLSelectElement>('[data-testid="filter-type"]')!;
    type.selectedIndex = 3; // Pompe en défaut
    type.dispatchEvent(new Event('change'));
    const typed = mainRequests()[0];
    expect(typed.request.params.get('type')).toBe('PUMP_FAULT');
    expect(typed.request.params.get('zoneId')).toBe('2');
    respond();

    const from = q<HTMLInputElement>('[data-testid="filter-from"]')!;
    from.value = '2026-11-14T10:00';
    from.dispatchEvent(new Event('change'));
    expect(mainRequests()[0].request.params.get('from')).toBe(new Date('2026-11-14T10:00').toISOString());
  });

  it('trie côté serveur au clic sur un en-tête', () => {
    start();
    respond();
    q('[data-sort="zoneId"]')!.click();
    expect(mainRequests()[0].request.params.get('sort')).toBe('zoneId,asc');
    respond();
    q('[data-sort="zoneId"]')!.click();
    expect(mainRequests()[0].request.params.get('sort')).toBe('zoneId,desc');
  });

  it('pagine côté serveur', () => {
    start();
    respond({ main: page([A1, A2], 45) });
    expect(q('[data-testid="pagination-info"]')!.textContent).toContain('1–20 sur 45');
    q('[data-testid="pagination-next"]')!.click();
    expect(mainRequests()[0].request.params.get('page')).toBe('1');
  });

  it('la recherche rapide filtre la page affichée sans nouvelle requête', () => {
    start();
    respond();
    const input = q<HTMLInputElement>('[data-testid="search"]')!;
    input.value = 'pompe';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(rows().length).toBe(1);
    http.expectNone(() => true);
    input.value = 'zzz';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(q('[data-testid="no-search-results"]')).not.toBeNull();
  });

  it('réinitialise les filtres', () => {
    start();
    respond();
    const select = q<HTMLSelectElement>('[data-testid="filter-status"]')!;
    select.selectedIndex = 2;
    select.dispatchEvent(new Event('change'));
    respond();
    q('[data-testid="reset-filters"]')!.click();
    expect(mainRequests()[0].request.params.has('status')).toBe(false);
  });

  /* ---------------- rafraîchissement automatique ---------------- */

  it('recharge la liste toutes les 10 secondes, puis s’arrête à la fermeture de la page', () => {
    vi.useFakeTimers();
    start();
    respond();
    expect(mainRequests().length).toBe(0);

    vi.advanceTimersByTime(9_000);
    expect(mainRequests().length).toBe(0);
    vi.advanceTimersByTime(1_000);
    const first = mainRequests();
    expect(first.length).toBe(1);
    first[0].flush(page([A1]));
    respond();
    fixture.detectChanges();
    expect(rows().length).toBe(1);

    vi.advanceTimersByTime(10_000);
    expect(mainRequests().length).toBe(1);
    respond();

    fixture.destroy();
    vi.advanceTimersByTime(30_000);
    http.expectNone(() => true);
  });

  /* ---------------- fiche détaillée ---------------- */

  it('ouvre la fiche d’une anomalie avec ses délais et son incident, puis la ferme avec Échap', () => {
    start();
    respond();
    row('a1').dispatchEvent(new Event('click'));
    fixture.detectChanges();
    const detail = q('[data-testid="anomaly-detail"]')!;
    expect(detail).not.toBeNull();
    expect(q('[data-testid="detail-zone"]')!.textContent).toContain('Zone Nord');
    expect(q('[data-testid="detail-sensor"]')!.textContent).toContain('Pression P-12');
    expect(q('[data-testid="detail-delay"]')!.textContent).toContain('2 s');
    expect(q('[data-testid="detail-incident"]')!.textContent).toContain('Incident n° 7');

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();
    expect(q('[data-testid="anomaly-detail"]')).toBeNull();
  });

  it('indique l’absence d’incident associé', () => {
    start();
    respond();
    row('a2').dispatchEvent(new Event('click'));
    fixture.detectChanges();
    expect(q('[data-testid="detail-no-incident"]')).not.toBeNull();
    expect(q('[data-testid="anomaly-detail"]')!.textContent).toContain('Durée de l\'anomalie');
  });

  it('la fiche ouverte se met à jour au rafraîchissement', () => {
    vi.useFakeTimers();
    start();
    respond();
    row('a1').dispatchEvent(new Event('click'));
    fixture.detectChanges();
    expect(q('[data-testid="anomaly-detail"]')!.textContent).toContain('Ouverte');

    vi.advanceTimersByTime(10_000);
    respond({ main: page([{ ...A1, status: 'RESOLVED', resolvedAt: '2026-11-14T10:05:00Z' }, A2]) });
    expect(q('[data-testid="anomaly-detail"]')!.textContent).toContain('Résolue');
  });
});
