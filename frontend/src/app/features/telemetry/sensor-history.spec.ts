import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { NotificationStream } from '../../core/notification-stream';
import { CSV_DOWNLOAD } from '../reports/csv-download';
import { AnomalyDataSource } from '../anomalies/anomaly-data-source';
import { Anomaly, AnomalyPage, AnomalyQuery } from '../anomalies/anomaly.model';
import { SensorDataSource } from './sensor-data-source';
import { SensorReading, SensorRow, SensorType } from './sensor.model';
import { SensorHistory } from './sensor-history';

class StubSensorDataSource extends SensorDataSource {
  historyCalls: { sensorId: number; from: Date; to: Date }[] = [];

  constructor(
    private readonly rows: SensorRow[],
    private readonly history: SensorReading[],
    private readonly fail = false,
  ) {
    super();
  }

  getSensors(): Observable<SensorRow[]> {
    return of(this.rows);
  }

  getHistory(sensorId: number, from: Date, to: Date): Observable<SensorReading[]> {
    this.historyCalls.push({ sensorId, from, to });
    return this.fail ? throwError(() => new Error('API indisponible')) : of(this.history);
  }
}

class StubAnomalyDataSource extends AnomalyDataSource {
  readonly queries: AnomalyQuery[] = [];

  constructor(
    private readonly pages: AnomalyPage[] = [],
    private readonly fail = false,
  ) {
    super();
  }

  list(query: AnomalyQuery): Observable<AnomalyPage> {
    this.queries.push(query);
    if (this.fail) return throwError(() => new Error('API anomalies indisponible'));
    return of(
      this.pages.find((page) => page.page === query.page) ?? {
        content: [],
        page: query.page,
        size: query.size,
        totalElements: 0,
        totalPages: 1,
      },
    );
  }
}

function sensor(type: SensorType = 'PRESSURE'): SensorRow {
  return {
    id: 1,
    name: 'Pression principale',
    type,
    zoneId: 1,
    zoneName: 'Centre-ville',
    assetName: 'Conduite principale',
    latestValue: type === 'PUMP_STATE' ? null : 2.4,
    unit: type === 'PUMP_STATE' ? null : 'bar',
    pumpState: type === 'PUMP_STATE' ? 'NORMAL' : null,
    measuredAt: new Date().toISOString(),
    state: 'active',
  };
}

function reading(
  value: number | null,
  measuredAt: string,
  pumpState: string | null = null,
): SensorReading {
  return { value, unit: value === null ? null : 'bar', pumpState, measuredAt };
}

function anomaly(sensorId: number, measuredAt: string, id = `a-${sensorId}`): Anomaly {
  return {
    id,
    type: 'LEAK_SUSPECTED',
    zoneId: 1,
    sensorId,
    severity: 'CRITICAL',
    status: 'OPEN',
    measuredAt,
    detectedAt: measuredAt,
    resolvedAt: null,
  };
}

async function render(source: StubSensorDataSource, anomalies = new StubAnomalyDataSource()) {
  const downloads: { content: string; fileName: string }[] = [];
  const routeParams = convertToParamMap({ sensorId: '1' });
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { paramMap: of(routeParams), snapshot: { paramMap: routeParams } },
      },
      { provide: SensorDataSource, useValue: source },
      { provide: AnomalyDataSource, useValue: anomalies },
      {
        provide: CSV_DOWNLOAD,
        useValue: (content: string, fileName: string) => downloads.push({ content, fileName }),
      },
      {
        provide: AuthService,
        useValue: { username: 'test', logout: () => undefined, hasAnyRole: () => false },
      },
      {
        provide: NotificationStream,
        useValue: {
          connected: signal(false),
          items: signal([]),
          toasts: signal([]),
          tick: signal(0),
          start: () => undefined,
          dismiss: () => undefined,
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(SensorHistory);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, root: fixture.nativeElement as HTMLElement, downloads };
}

describe('SensorHistory', () => {
  it('loads history, thresholds, and paginated anomaly markers for the selected sensor', async () => {
    const now = Date.now();
    const firstAt = new Date(now - 2 * 60 * 60 * 1000).toISOString();
    const secondAt = new Date(now - 60 * 60 * 1000).toISOString();
    const source = new StubSensorDataSource(
      [sensor()],
      [reading(2.1, firstAt), reading(2.5, secondAt)],
    );
    const anomalies = new StubAnomalyDataSource([
      { content: [anomaly(99, secondAt)], page: 0, size: 200, totalElements: 2, totalPages: 2 },
      {
        content: [anomaly(1, firstAt, 'a-1')],
        page: 1,
        size: 200,
        totalElements: 2,
        totalPages: 2,
      },
    ]);
    const { fixture, root } = await render(source, anomalies);

    expect(root.textContent).toContain('#1 · Pression principale');
    expect(root.querySelector('[data-testid="history-chart"]')).not.toBeNull();
    expect(root.querySelector('[data-testid="threshold-legend"]')?.textContent).toContain(
      'Seuils indicatifs',
    );
    expect(root.querySelectorAll('[data-testid="threshold-line"]')).toHaveLength(2);
    expect(root.querySelector('[data-testid="anomaly-marker-a-1"]')).not.toBeNull();
    expect(anomalies.queries.map((query) => query.page)).toEqual([0, 1]);
    expect(anomalies.queries[0].size).toBe(200);
    expect(anomalies.queries[0].from).not.toBeNull();
    expect(anomalies.queries[0].to).not.toBeNull();
    expect(root.querySelectorAll('[data-testid="history-row"]')).toHaveLength(2);

    root.querySelector<HTMLButtonElement>('[data-testid="period-7d"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(
      source.historyCalls.at(-1)!.to.getTime() - source.historyCalls.at(-1)!.from.getTime(),
    ).toBe(7 * 24 * 60 * 60 * 1000);
    root.querySelector<HTMLButtonElement>('[data-testid="period-1h"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(
      source.historyCalls.at(-1)!.to.getTime() - source.historyCalls.at(-1)!.from.getTime(),
    ).toBe(60 * 60 * 1000);
    TestBed.resetTestingModule();
  });

  it('applies custom periods and validates their boundaries', async () => {
    const now = new Date();
    const source = new StubSensorDataSource([sensor()], [reading(2, now.toISOString())]);
    const { fixture, root } = await render(source);

    root.querySelector<HTMLButtonElement>('[data-testid="period-custom"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    const from = root.querySelector<HTMLInputElement>('[data-testid="custom-from"]')!;
    const to = root.querySelector<HTMLInputElement>('[data-testid="custom-to"]')!;
    const localInput = (date: Date) =>
      new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
    from.value = localInput(new Date(now.getTime() - 3 * 60 * 60 * 1000));
    from.dispatchEvent(new Event('input'));
    to.value = localInput(new Date(now.getTime() - 2 * 60 * 60 * 1000));
    to.dispatchEvent(new Event('input'));
    root.querySelector<HTMLButtonElement>('[data-testid="apply-custom-range"]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();

    const applied = source.historyCalls.at(-1)!;
    expect(applied.to.getTime() - applied.from.getTime()).toBe(60 * 60 * 1000);
    from.value = localInput(new Date(now.getTime() - 60 * 60 * 1000));
    from.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelector('[data-testid="custom-range-error"]')?.textContent).toContain(
      'doit précéder',
    );
    expect(
      root.querySelector<HTMLButtonElement>('[data-testid="apply-custom-range"]')?.disabled,
    ).toBe(true);
    TestBed.resetTestingModule();
  });

  it('exports all readings and paginates the table independently', async () => {
    const now = Date.now();
    const readings = Array.from({ length: 25 }, (_, index) =>
      reading(index, new Date(now - (25 - index) * 60_000).toISOString()),
    );
    const { fixture, root, downloads } = await render(
      new StubSensorDataSource([sensor()], readings),
    );

    expect(root.querySelectorAll('[data-testid="history-row"]')).toHaveLength(20);
    root.querySelector<HTMLButtonElement>('[data-testid="pagination-next"]')!.click();
    fixture.detectChanges();
    expect(root.querySelectorAll('[data-testid="history-row"]')).toHaveLength(5);

    root.querySelector<HTMLButtonElement>('[data-testid="export-csv"]')!.click();
    expect(downloads).toHaveLength(1);
    expect(downloads[0].content.split('\r\n')).toHaveLength(26);
    expect(downloads[0].fileName).toContain('capteur-1-historique-');
    TestBed.resetTestingModule();
  });

  it('shows pump states and keeps history available when the optional anomaly API fails', async () => {
    const now = new Date();
    const pumpReadings = [
      reading(null, new Date(now.getTime() - 60_000).toISOString(), 'NORMAL'),
      reading(null, now.toISOString(), 'FAULT'),
    ];
    const pump = await render(new StubSensorDataSource([sensor('PUMP_STATE')], pumpReadings));
    expect(pump.root.textContent).toContain('Historique des états de la pompe');
    expect(pump.root.textContent).toContain('Défaut');
    TestBed.resetTestingModule();

    const readings = [reading(2.1, new Date(now.getTime() - 60_000).toISOString())];
    const failedAnomalies = await render(
      new StubSensorDataSource([sensor()], readings),
      new StubAnomalyDataSource([], true),
    );
    expect(failedAnomalies.root.querySelector('[data-testid="anomaly-warning"]')).not.toBeNull();
    expect(failedAnomalies.root.querySelector('[data-testid="history-chart"]')).not.toBeNull();
    TestBed.resetTestingModule();

    const failedHistory = await render(new StubSensorDataSource([sensor()], [], true));
    expect(
      failedHistory.root.querySelector('[data-testid="history-error"]')?.textContent,
    ).toContain('API indisponible');
    TestBed.resetTestingModule();
  });
});
