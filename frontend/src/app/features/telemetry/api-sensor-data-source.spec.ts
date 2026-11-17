import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AppConfig } from '../../core/app-config';
import { ApiSensorDataSource } from './api-sensor-data-source';
import { SensorRow } from './sensor.model';

const config: AppConfig = {
  issuer: 'http://localhost:8180/realms/aquaops',
  clientId: 'aquaops-frontend',
  assetsBaseUrl: '/api/assets',
  telemetryBaseUrl: '/api/telemetry',
  sensorDataMode: 'api',
  sensorSilenceThresholdSeconds: 10,
  sensorLookbackSeconds: 120,
};

describe('ApiSensorDataSource', () => {
  let http: HttpTestingController;
  let source: ApiSensorDataSource;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    source = TestBed.inject(ApiSensorDataSource);
  });

  afterEach(() => http.verify());

  it('joins sensor, zone and asset data with recent readings and marks silent sensors', () => {
    let rows: SensorRow[] | undefined;
    source.getSensors(config).subscribe((result) => (rows = result));

    http.expectOne('/api/assets/sensors').flush([
      { id: 1, name: 'Pression Nord', type: 'PRESSURE', zoneId: 4, assetType: 'PIPE', assetId: 9 },
      { id: 2, name: 'Pompe Sud', type: 'PUMP_STATE', zoneId: 4, assetType: 'PUMP', assetId: 7 },
    ]);
    http.expectOne('/api/assets/zones').flush([{ id: 4, name: 'Médina' }]);
    http.expectOne('/api/assets/pipes').flush([{ id: 9, name: 'Conduite A' }]);
    http.expectOne('/api/assets/tanks').flush([]);
    http.expectOne('/api/assets/pumps').flush([{ id: 7, name: 'Pompe B' }]);

    const readings = http.match((request) => request.url === '/api/telemetry/readings');
    expect(readings).toHaveLength(2);
    expect(readings.map((request) => request.request.params.get('size'))).toEqual(['1000', '1000']);
    readings.find((request) => request.request.params.get('sensor') === '1')?.flush({
      content: [
        { value: 1.8, unit: 'bar', pumpState: null, measuredAt: '2026-11-17T10:00:00Z' },
        { value: 2.6, unit: 'bar', pumpState: null, measuredAt: new Date().toISOString() },
      ],
    });
    readings.find((request) => request.request.params.get('sensor') === '2')?.flush({
      content: [
        {
          value: null,
          unit: null,
          pumpState: 'FAULT',
          measuredAt: new Date(Date.now() - 20_000).toISOString(),
        },
      ],
    });

    expect(rows).toEqual([
      expect.objectContaining({
        id: 1,
        zoneName: 'Médina',
        assetName: 'Conduite A',
        latestValue: 2.6,
        state: 'active',
      }),
      expect.objectContaining({
        id: 2,
        assetName: 'Pompe B',
        pumpState: 'FAULT',
        state: 'silent',
      }),
    ]);
  });
});
