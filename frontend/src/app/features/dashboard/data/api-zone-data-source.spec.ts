import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiZoneDataSource } from './api-zone-data-source';
import { ZoneSummary } from './zone.model';

const zones = [
  { id: 1, name: 'Zone 1' },
  { id: 2, name: 'Zone 2' },
];

const sensors = [
  { id: 10, name: 'P1', type: 'PRESSURE', zoneId: 1 },
  { id: 11, name: 'F1', type: 'FLOW', zoneId: 1 },
  { id: 12, name: 'L1', type: 'LEVEL', zoneId: 1 },
  { id: 13, name: 'PU1', type: 'PUMP_STATE', zoneId: 1 },
  { id: 20, name: 'P2', type: 'PRESSURE', zoneId: 2 },
];

const reading = (sensorId: number, extra: object, at = '2026-11-03T10:00:00Z') => ({
  sensorId,
  value: null,
  pumpState: null,
  measuredAt: at,
  ...extra,
});

describe('ApiZoneDataSource', () => {
  let source: ApiZoneDataSource;
  let http: HttpTestingController;
  let result: ZoneSummary[] | undefined;
  let error: unknown;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), ApiZoneDataSource],
    });
    source = TestBed.inject(ApiZoneDataSource);
    http = TestBed.inject(HttpTestingController);
    result = undefined;
    error = undefined;
  });

  afterEach(() => http.verify({ ignoreCancelled: true }));

  function start() {
    source.getZones().subscribe({ next: (r) => (result = r), error: (e) => (error = e) });
  }

  function answerLists(zoneList: object = zones, sensorList: object = sensors) {
    http.expectOne('/api/assets/zones').flush(zoneList);
    http.expectOne('/api/assets/sensors').flush(sensorList);
  }

  /** Répond à chaque requête de mesures selon le capteur ; `fail` liste les capteurs en panne. */
  function answerReadings(bySensor: Record<number, object[]>, fail: number[] = []) {
    const requests = http.match((r) => r.url === '/api/telemetry/readings');
    for (const req of requests) {
      const id = Number(req.request.params.get('sensor'));
      if (fail.includes(id)) {
        req.flush('erreur', { status: 500, statusText: 'Server Error' });
      } else {
        req.flush({ content: bySensor[id] ?? [], page: 0, size: 1000, totalElements: 0, totalPages: 1 });
      }
    }
    return requests;
  }

  it('assemble les dernières mesures de chaque capteur par zone et calcule l\'état', () => {
    start();
    answerLists();
    answerReadings({
      10: [reading(10, { value: 2.6 })],
      11: [reading(11, { value: 30 })],
      12: [reading(12, { value: 3.5 })],
      13: [reading(13, { pumpState: 'NORMAL' })],
      20: [reading(20, { value: 1.1 }, '2026-11-03T10:00:05Z')],
    });

    expect(result).toHaveLength(2);
    expect(result![0]).toMatchObject({ id: 1, name: 'Zone 1', pressureBar: 2.6, flowM3h: 30, levelM: 3.5, pumpState: 'NORMAL', state: 'normal' });
    expect(result![1]).toMatchObject({ id: 2, pressureBar: 1.1, flowM3h: null, state: 'critique', updatedAt: '2026-11-03T10:00:05Z' });
  });

  it('prend la mesure la plus récente (la dernière de la liste triée par date)', () => {
    start();
    answerLists();
    answerReadings({ 10: [reading(10, { value: 2.9 }), reading(10, { value: 1.1 })] });
    expect(result![0].pressureBar).toBe(1.1);
  });

  it('passe en critique quand une pompe est en défaut', () => {
    start();
    answerLists();
    answerReadings({ 13: [reading(13, { pumpState: 'FAULT' })] });
    expect(result![0].pumpState).toBe('FAULT');
    expect(result![0].state).toBe('critique');
  });

  it('une panne sur un capteur ne casse pas le tableau de bord', () => {
    start();
    answerLists();
    answerReadings({ 10: [reading(10, { value: 2.6 })] }, [20]);
    expect(error).toBeUndefined();
    expect(result![0].pressureBar).toBe(2.6);
    expect(result![1].pressureBar).toBeNull();
  });

  it('interroge les mesures avec le capteur, une fenêtre de temps et une taille', () => {
    start();
    answerLists();
    const requests = answerReadings({});
    expect(requests).toHaveLength(5);
    const params = requests[0].request.params;
    expect(params.get('sensor')).toBe('10');
    expect(params.get('size')).toBe('1000');
    expect(new Date(params.get('from')!).getTime()).toBeLessThan(new Date(params.get('to')!).getTime());
  });

  it('sans capteur, ne demande aucune mesure et renvoie les zones sans valeurs', () => {
    start();
    answerLists(zones, []);
    expect(result).toHaveLength(2);
    expect(result![0]).toMatchObject({ pressureBar: null, flowM3h: null, levelM: null, pumpState: null });
  });

  it('accepte aussi des listes sous forme de page { content }', () => {
    start();
    answerLists({ content: zones }, { content: sensors });
    answerReadings({});
    expect(result).toHaveLength(2);
  });

  it('transmet l\'erreur quand la liste des zones est inaccessible', () => {
    start();
    http.expectOne('/api/assets/zones').flush('erreur', { status: 503, statusText: 'Unavailable' });
    // La requête des capteurs est annulée automatiquement : il n'y a rien à lui répondre.
    expect(error).toBeDefined();
    expect(result).toBeUndefined();
  });
});
