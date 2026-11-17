import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AppShell } from '../../core/layout/app-shell';
import { SensorDataSource } from './sensor-data-source';
import { SensorRow, SensorState, SensorType } from './sensor.model';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';

const REFRESH_MS = 5000;

@Component({
  selector: 'app-sensor-list',
  imports: [AppShell, RouterLink],
  templateUrl: './sensor-list.html',
})
export class SensorList {
  private readonly source = inject(SensorDataSource);
  private readonly route = inject(ActivatedRoute);

  protected readonly sensors = signal<SensorRow[]>([]);
  protected readonly loading = signal(true);
  protected readonly loaded = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly zoneFilter = signal('');
  protected readonly typeFilter = signal('');
  protected readonly stateFilter = signal('');
  protected readonly search = signal('');
  protected readonly routeParams = toSignal(this.route.paramMap, { initialValue: this.route.snapshot.paramMap });

  protected readonly selectedSensorId = computed(() => {
    const id = Number(this.routeParams()?.get('sensorId'));
    return Number.isInteger(id) && id > 0 ? id : null;
  });

  protected readonly zones = computed(() => {
    const unique = new Map<number, string>();
    this.sensors().forEach((sensor) => unique.set(sensor.zoneId, sensor.zoneName));
    return [...unique].sort(([a], [b]) => a - b).map(([id, name]) => ({ id, name }));
  });

  protected readonly filteredSensors = computed(() => {
    const zone = this.zoneFilter();
    const type = this.typeFilter();
    const state = this.stateFilter();
    const search = this.search().trim();
    return this.sensors().filter((sensor) =>
      (!zone || sensor.zoneId === Number(zone))
      && (!type || sensor.type === type)
      && (!state || sensor.state === state)
      && (!search || String(sensor.id).includes(search)),
    );
  });

  protected readonly activeCount = computed(() => this.sensors().filter((sensor) => sensor.state === 'active').length);
  protected readonly silentCount = computed(() => this.sensors().filter((sensor) => sensor.state === 'silent').length);

  constructor() {
    timer(0, REFRESH_MS)
      .pipe(
        switchMap(() => {
          this.loading.set(!this.loaded());
          return this.source.getSensors().pipe(
            catchError((error: unknown) => {
              this.error.set(this.errorMessage(error));
              this.loaded.set(true);
              this.loading.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((sensors) => {
        this.sensors.set(sensors);
        this.error.set(null);
        this.loading.set(false);
        this.loaded.set(true);
      });
  }

  protected setZone(event: Event): void {
    this.zoneFilter.set((event.target as HTMLSelectElement).value);
  }

  protected setType(event: Event): void {
    this.typeFilter.set((event.target as HTMLSelectElement).value);
  }

  protected setState(event: Event): void {
    this.stateFilter.set((event.target as HTMLSelectElement).value);
  }

  protected setSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
  }

  protected resetFilters(): void {
    this.zoneFilter.set('');
    this.typeFilter.set('');
    this.stateFilter.set('');
    this.search.set('');
  }

  protected typeLabel(type: SensorType): string {
    return { PRESSURE: 'Pression', FLOW: 'Débit', LEVEL: 'Niveau', PUMP_STATE: 'État pompe' }[type];
  }

  protected stateLabel(state: SensorState): string {
    return state === 'active' ? 'Actif' : 'Silencieux';
  }

  protected stateClass(state: SensorState): string {
    return state === 'active'
      ? 'bg-tertiary-fixed/30 text-tertiary'
      : 'bg-amber-100 text-amber-800';
  }

  protected latestValue(sensor: SensorRow): string {
    if (sensor.type === 'PUMP_STATE') {
      return sensor.pumpState ?? '—';
    }
    return sensor.latestValue === null
      ? '—'
      : `${sensor.latestValue} ${sensor.unit ?? ''}`.trim();
  }

  protected measuredAt(value: string | null): string {
    if (!value) {
      return 'Aucune mesure';
    }
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? 'Date inconnue'
      : date.toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
  }

  private errorMessage(error: unknown): string {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = Number(error.status);
      return status > 0
        ? `Erreur HTTP ${status} lors du chargement des capteurs.`
        : 'Service des capteurs injoignable. Vérifiez la connexion puis réessayez.';
    }
    return error instanceof Error ? error.message : 'Erreur inconnue lors du chargement des capteurs.';
  }
}
