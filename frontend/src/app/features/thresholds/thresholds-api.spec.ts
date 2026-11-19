import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ThresholdApi } from './thresholds-api';

describe('ThresholdApi', () => {
  let api: ThresholdApi;
  let http: HttpTestingController;
  const values = { leakMinPressure: 1.5, leakMaxFlow: 55, leakDurationSeconds: 8, silenceThresholdSeconds: 12, tankMinLevel: 3.5 };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ThresholdApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('lit les seuils : GET /api/detection/thresholds', () => {
    api.list().subscribe();
    const req = http.expectOne('/api/detection/thresholds');
    expect(req.request.method).toBe('GET');
    req.flush({ defaults: values, zones: [] });
  });

  it('enregistre : PUT /api/detection/thresholds/{zone} avec les cinq seuils', () => {
    api.save(3, values).subscribe();
    const req = http.expectOne('/api/detection/thresholds/3');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(values);
    req.flush({ zoneId: 3, custom: true, thresholds: values, updatedAt: null, updatedBy: null });
  });

  it('propage le 403 (rôle insuffisant) et le 400 de validation', () => {
    const statuses: number[] = [];
    api.save(3, values).subscribe({ error: (e: { status: number }) => statuses.push(e.status) });
    http.expectOne('/api/detection/thresholds/3').flush({}, { status: 403, statusText: 'Forbidden' });
    api.save(3, values).subscribe({ error: (e: { status: number }) => statuses.push(e.status) });
    http.expectOne('/api/detection/thresholds/3').flush({ errors: [] }, { status: 400, statusText: 'Bad Request' });
    expect(statuses).toEqual([403, 400]);
  });
});
