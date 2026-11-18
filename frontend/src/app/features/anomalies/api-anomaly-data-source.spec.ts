import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiAnomalyDataSource } from './api-anomaly-data-source';
import { MOCK_ANOMALIES, MockAnomalyDataSource } from './mock-anomaly-data-source';
import { AnomalyPage, DEFAULT_QUERY } from './anomaly.model';

describe('ApiAnomalyDataSource', () => {
  let http: HttpTestingController;
  let source: ApiAnomalyDataSource;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ApiAnomalyDataSource, provideHttpClient(), provideHttpClientTesting()] });
    source = TestBed.inject(ApiAnomalyDataSource);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('appelle GET /api/detection/anomalies avec les paramètres de la requête', () => {
    let result: AnomalyPage | undefined;
    source
      .list({ ...DEFAULT_QUERY, zoneId: 2, status: 'OPEN', page: 1, size: 10, sort: { field: 'zoneId', direction: 'asc' } })
      .subscribe((p) => (result = p));
    const req = http.expectOne((r) => r.url === '/api/detection/anomalies');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('zoneId')).toBe('2');
    expect(req.request.params.get('status')).toBe('OPEN');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    expect(req.request.params.get('sort')).toBe('zoneId,asc');
    expect(req.request.params.has('type')).toBe(false);
    req.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 1 });
    expect(result?.totalElements).toBe(0);
  });

  it('propage les erreurs HTTP', () => {
    let status = 0;
    source.list(DEFAULT_QUERY).subscribe({ error: (e: { status: number }) => (status = e.status) });
    http.expectOne((r) => r.url === '/api/detection/anomalies').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });
});

describe('MockAnomalyDataSource', () => {
  it('applique les filtres et la pagination du serveur sur les données de démonstration', () => {
    const source = new MockAnomalyDataSource();
    let page: AnomalyPage | undefined;
    source.list({ ...DEFAULT_QUERY, status: 'OPEN' }).subscribe((p) => (page = p));
    expect(page?.totalElements).toBe(MOCK_ANOMALIES.filter((a) => a.status === 'OPEN').length);
    expect(page?.content.every((a) => a.status === 'OPEN')).toBe(true);
  });
});
