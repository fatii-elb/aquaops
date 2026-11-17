import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../core/auth.service';
import { NotificationStream } from '../../core/notification-stream';
import { SensorDataSource } from './sensor-data-source';
import { SensorRow } from './sensor.model';
import { SensorList } from './sensor-list';

const rows: SensorRow[] = [
  {
    id: 1,
    name: 'Pression principale',
    type: 'PRESSURE',
    zoneId: 1,
    zoneName: 'Centre-ville',
    assetName: 'Conduite principale',
    latestValue: 2.4,
    unit: 'bar',
    pumpState: null,
    measuredAt: '2026-11-17T10:00:00Z',
    state: 'active',
  },
  {
    id: 7,
    name: 'Débit secondaire',
    type: 'FLOW',
    zoneId: 2,
    zoneName: 'Médina',
    assetName: 'Conduite secondaire',
    latestValue: 18.6,
    unit: 'm³/h',
    pumpState: null,
    measuredAt: '2026-11-17T09:00:00Z',
    state: 'silent',
  },
];

class StubSensorDataSource extends SensorDataSource {
  calls = 0;
  fail = false;

  getSensors(): Observable<SensorRow[]> {
    this.calls++;
    return this.fail ? throwError(() => new Error('API indisponible')) : of(rows);
  }
}

describe('SensorList', () => {
  let source: StubSensorDataSource;

  beforeEach(() => {
    vi.useFakeTimers();
    source = new StubSensorDataSource();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: SensorDataSource, useValue: source },
        { provide: AuthService, useValue: { username: 'test', logout: vi.fn(), hasAnyRole: () => false } },
        {
          provide: NotificationStream,
          useValue: {
            connected: signal(false),
            items: signal([]),
            toasts: signal([]),
            tick: signal(0),
            start: vi.fn(),
            dismiss: vi.fn(),
          },
        },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  function create() {
    const fixture = TestBed.createComponent(SensorList);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    fixture.detectChanges();
    return fixture;
  }

  it('affiche les capteurs actifs et silencieux et leurs compteurs', () => {
    const el: HTMLElement = create().nativeElement;

    expect(el.querySelectorAll('[data-testid="sensor-row"]')).toHaveLength(2);
    expect(el.querySelector('[data-testid="active-count"]')?.textContent).toContain('1');
    expect(el.querySelector('[data-testid="silent-count"]')?.textContent).toContain('1');
    expect(el.textContent).toContain('Conduite principale');
    expect(el.textContent).toContain('Silencieux');
    expect(el.querySelector('a[href="/telemetry/1"]')).not.toBeNull();
  });

  it('combine le filtre de zone et la recherche par identifiant', () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    const zone = el.querySelector<HTMLSelectElement>('[data-testid="filter-zone"]')!;
    zone.value = '2';
    zone.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el.querySelectorAll('[data-testid="sensor-row"]')).toHaveLength(1);
    expect(el.textContent).toContain('#7');

    const search = el.querySelector<HTMLInputElement>('[data-testid="filter-id"]')!;
    search.value = '1';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el.querySelectorAll('[data-testid="sensor-row"]')).toHaveLength(0);
    expect(el.querySelector('[data-testid="empty-sensors"]')).not.toBeNull();
  });

  it('filtre par type et par état', () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    const type = el.querySelector<HTMLSelectElement>('[data-testid="filter-type"]')!;
    type.value = 'FLOW';
    type.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el.querySelectorAll('[data-testid="sensor-row"]')).toHaveLength(1);
    expect(el.textContent).toContain('#7');

    const state = el.querySelector<HTMLSelectElement>('[data-testid="filter-state"]')!;
    state.value = 'active';
    state.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(el.querySelectorAll('[data-testid="sensor-row"]')).toHaveLength(0);
  });

  it('rafraîchit toutes les cinq secondes et expose les erreurs', () => {
    source.fail = true;
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="sensor-error"]')?.textContent).toContain('API indisponible');
    source.fail = false;
    vi.advanceTimersByTime(5000);
    fixture.detectChanges();
    expect(source.calls).toBe(2);
    expect(el.querySelector('[data-testid="sensor-error"]')).toBeNull();
    expect(el.querySelectorAll('[data-testid="sensor-row"]')).toHaveLength(2);
  });
});
