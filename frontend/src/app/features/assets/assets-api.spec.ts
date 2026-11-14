import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, beforeEach, afterEach } from 'vitest';
import { AssetsApi } from './assets-api';

describe('AssetsApi', () => {
  let api: AssetsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AssetsApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it.each([
    ['zones', '/api/assets/zones'],
    ['pipes', '/api/assets/pipes'],
    ['tanks', '/api/assets/tanks'],
    ['pumps', '/api/assets/pumps'],
    ['sensors', '/api/assets/sensors'],
  ] as const)('liste %s : GET %s', (category, url) => {
    api.list(category).subscribe();
    const req = http.expectOne(url);
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('lit un élément par son identifiant', () => {
    api.get('pipes', 4).subscribe();
    const req = http.expectOne('/api/assets/pipes/4');
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('crée une zone : POST avec le corps tel quel, sans identifiant', () => {
    api.create('zones', { name: 'Zone 3', description: null }).subscribe();
    const req = http.expectOne('/api/assets/zones');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Zone 3', description: null });
    req.flush({ id: 3, name: 'Zone 3', description: null });
  });

  it('crée un capteur avec sa zone et son actif rattaché', () => {
    const body = { name: 'Pression Z1', type: 'PRESSURE', zoneId: 1, assetType: 'PIPE', assetId: 1 } as const;
    api.create('sensors', body).subscribe();
    const req = http.expectOne('/api/assets/sensors');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 8, ...body });
  });

  it('modifie : PUT avec tous les champs (le serveur remplace l’élément entier)', () => {
    const body = { name: 'Conduite Z1', zoneId: 1, lengthM: 500, diameterMm: 200 };
    api.update('pipes', 1, body).subscribe();
    const req = http.expectOne('/api/assets/pipes/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    req.flush({ id: 1, ...body });
  });

  it('supprime : DELETE sur l’élément', () => {
    api.remove('tanks', 2).subscribe();
    const req = http.expectOne('/api/assets/tanks/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('charge les cinq listes ensemble', () => {
    let snapshot: unknown;
    api.loadAll().subscribe((s) => (snapshot = s));
    http.expectOne('/api/assets/zones').flush([{ id: 1, name: 'Z1', description: null }]);
    http.expectOne('/api/assets/pipes').flush([]);
    http.expectOne('/api/assets/tanks').flush([]);
    http.expectOne('/api/assets/pumps').flush([]);
    http.expectOne('/api/assets/sensors').flush([]);
    expect(snapshot).toEqual({ zones: [{ id: 1, name: 'Z1', description: null }], pipes: [], tanks: [], pumps: [], sensors: [] });
  });

  it('propage l’erreur 400 d’un capteur refusé', () => {
    let status = 0;
    api.create('sensors', { name: 'X', type: 'FLOW', zoneId: 999, assetType: 'PIPE', assetId: 1 }).subscribe({
      error: (e: { status: number }) => (status = e.status),
    });
    http.expectOne('/api/assets/sensors').flush({ status: 400 }, { status: 400, statusText: 'Bad Request' });
    expect(status).toBe(400);
  });
});

describe('AssetsApi.save', () => {
  let api: AssetsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(AssetsApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('crée avec POST quand il n’y a pas d’identifiant', () => {
    api.save({ category: 'pumps', body: { name: 'Pompe 9', zoneId: 2 } }, null).subscribe();
    const req = http.expectOne('/api/assets/pumps');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ name: 'Pompe 9', zoneId: 2 });
    req.flush({ id: 9, name: 'Pompe 9', zoneId: 2 });
  });

  it('modifie avec PUT sur l’identifiant, en renvoyant tous les champs', () => {
    api.save({ category: 'tanks', body: { name: 'R1', zoneId: 1, capacityM3: null } }, 5).subscribe();
    const req = http.expectOne('/api/assets/tanks/5');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ name: 'R1', zoneId: 1, capacityM3: null });
    req.flush({ id: 5, name: 'R1', zoneId: 1, capacityM3: null });
  });
});
