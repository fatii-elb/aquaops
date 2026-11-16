import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { AnomalyContext } from './anomaly-context';

describe('AnomalyContext', () => {
  let ctx: AnomalyContext;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    ctx = TestBed.inject(AnomalyContext);
    http = TestBed.inject(HttpTestingController);
  });

  it('charge les noms une seule fois et les met à disposition', () => {
    ctx.loadNames();
    ctx.loadNames();
    http.expectOne('/api/assets/zones').flush([{ id: 1, name: 'Zone Nord' }]);
    http.expectOne('/api/assets/sensors').flush([{ id: 4, name: 'Pression P-4' }]);
    expect(ctx.zoneName(1)).toBe('Zone Nord');
    expect(ctx.sensorName(4)).toBe('Pression P-4');
  });

  it('retombe sur les identifiants quand les noms sont inconnus ou absents', () => {
    ctx.loadNames();
    http.expectOne('/api/assets/zones').error(new ProgressEvent('error'));
    http.expectOne('/api/assets/sensors').flush({}, { status: 500, statusText: 'Server Error' });
    expect(ctx.zoneName(3)).toBe('Zone 3');
    expect(ctx.sensorName(9)).toBe('Capteur 9');
    expect(ctx.sensorName(null)).toBe('—');
  });

  it('une panne du service des incidents ne bloque rien : liste vide', () => {
    let result: unknown[] | undefined;
    ctx.incidents$().subscribe((i) => (result = i));
    http.expectOne('/api/incidents').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(result).toEqual([]);
  });
});
