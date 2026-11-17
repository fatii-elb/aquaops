import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  EMPTY,
  catchError,
  combineLatest,
  expand,
  forkJoin,
  map,
  of,
  reduce,
  switchMap,
} from 'rxjs';
import { AppShell } from '../../core/layout/app-shell';
import { Pagination } from '../../shared/pagination/pagination';
import { AnomalyDataSource } from '../anomalies/anomaly-data-source';
import {
  ANOMALY_TYPE_LABELS,
  Anomaly,
  AnomalyQuery,
  DEFAULT_QUERY,
} from '../anomalies/anomaly.model';
import { DEFAULT_THRESHOLDS } from '../dashboard/data/zone-thresholds';
import { CSV_DOWNLOAD } from '../reports/csv-download';
import { csvCell } from '../reports/reports-logic';
import { SensorDataSource } from './sensor-data-source';
import { SensorReading, SensorRow, SensorType } from './sensor.model';
import {
  SENSOR_HISTORY_CHART_HEIGHT,
  SENSOR_HISTORY_CHART_WIDTH,
  sensorChartX,
  sensorChartY,
  sensorHistoryChart,
} from './sensor-history-chart';

const PERIODS = [
  { value: '1h', label: '1 heure', durationMs: 60 * 60 * 1000 },
  { value: '24h', label: '24 heures', durationMs: 24 * 60 * 60 * 1000 },
  { value: '7d', label: '7 jours', durationMs: 7 * 24 * 60 * 60 * 1000 },
  { value: 'custom', label: 'Personnalisée', durationMs: null },
] as const;

type HistoryPeriod = (typeof PERIODS)[number]['value'];
type ThresholdKind = 'alert' | 'critical';

interface SensorThreshold {
  label: string;
  value: number;
  kind: ThresholdKind;
}

interface CustomRange {
  from: Date;
  to: Date;
}

interface HistoryLoad {
  sensor: SensorRow | null;
  readings: SensorReading[];
  anomalies: Anomaly[];
  anomalyError: string | null;
  error: string | null;
}

function dateTimeLocal(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

@Component({
  selector: 'app-sensor-history',
  imports: [AppShell, Pagination, RouterLink],
  templateUrl: './sensor-history.html',
})
export class SensorHistory {
  private readonly source = inject(SensorDataSource);
  private readonly anomalySource = inject(AnomalyDataSource);
  private readonly download = inject(CSV_DOWNLOAD);
  private readonly route = inject(ActivatedRoute);

  protected readonly periods = PERIODS;
  protected readonly period = signal<HistoryPeriod>('24h');
  protected readonly reloadToken = signal(0);
  protected readonly customFrom = signal('');
  protected readonly customTo = signal('');
  private readonly appliedCustomRange = signal<CustomRange | null>(null);
  protected readonly sensor = signal<SensorRow | null>(null);
  protected readonly readings = signal<SensorReading[]>([]);
  protected readonly anomalies = signal<Anomaly[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly anomalyError = signal<string | null>(null);
  protected readonly tablePage = signal(0);
  protected readonly tableSize = signal(20);
  protected readonly chartWidth = SENSOR_HISTORY_CHART_WIDTH;
  protected readonly chartHeight = SENSOR_HISTORY_CHART_HEIGHT;
  protected readonly thresholds = computed(() => this.sensorThresholds());
  protected readonly chart = computed(() =>
    sensorHistoryChart(
      this.readings(),
      this.thresholds().map((threshold) => threshold.value),
    ),
  );
  protected readonly thresholdLines = computed(() => {
    const chart = this.chart();
    return this.thresholds()
      .map((threshold) => ({
        ...threshold,
        y: sensorChartY(threshold.value, chart.scaleMin, chart.scaleMax),
      }))
      .filter((threshold): threshold is SensorThreshold & { y: number } => threshold.y !== null);
  });
  protected readonly anomalyMarkers = computed(() => {
    const chart = this.chart();
    return this.anomalies()
      .map((anomaly) => {
        const date = anomaly.measuredAt ?? anomaly.detectedAt;
        const time = Date.parse(date);
        const start = chart.timeStart;
        const end = chart.timeEnd;
        if (
          start === null ||
          end === null ||
          !Number.isFinite(time) ||
          time < start ||
          time > end
        ) {
          return null;
        }
        const x = sensorChartX(time, start, end);
        return x === null
          ? null
          : {
              id: anomaly.id,
              x,
              label: ANOMALY_TYPE_LABELS[anomaly.type] ?? anomaly.type,
              severity: anomaly.severity,
              date,
            };
      })
      .filter((marker): marker is NonNullable<typeof marker> => marker !== null);
  });
  protected readonly customRangeError = computed(() => this.validateCustomRange());
  protected readonly periodLabel = computed(() => {
    if (this.period() !== 'custom') {
      return this.periods.find((item) => item.value === this.period())?.label ?? '';
    }
    return 'Période personnalisée';
  });
  protected readonly latestReadings = computed(() => this.readings().slice(-20).reverse());
  protected readonly tableRows = computed(() => {
    const readings = this.readings();
    const end = Math.max(0, readings.length - this.tablePage() * this.tableSize());
    return readings.slice(Math.max(0, end - this.tableSize()), end).reverse();
  });

  constructor() {
    const now = new Date();
    this.customFrom.set(dateTimeLocal(new Date(now.getTime() - 24 * 60 * 60 * 1000)));
    this.customTo.set(dateTimeLocal(now));
    this.appliedCustomRange.set({ from: new Date(now.getTime() - 24 * 60 * 60 * 1000), to: now });

    combineLatest([
      this.route.paramMap,
      toObservable(this.period),
      toObservable(this.reloadToken),
      toObservable(this.appliedCustomRange),
    ])
      .pipe(
        switchMap(([params, period, , customRange]) => {
          const id = Number(params.get('sensorId'));
          this.loading.set(true);
          this.error.set(null);
          this.anomalyError.set(null);

          if (!Number.isSafeInteger(id) || id < 1) {
            return of<HistoryLoad>({
              sensor: null,
              readings: [],
              anomalies: [],
              anomalyError: null,
              error: 'Identifiant de capteur invalide.',
            });
          }

          const now = new Date();
          const preset = PERIODS.find((item) => item.value === period);
          const range =
            preset?.durationMs !== null && preset?.durationMs !== undefined
              ? { from: new Date(now.getTime() - preset.durationMs), to: now }
              : customRange;
          if (!range) {
            return of<HistoryLoad>({
              sensor: null,
              readings: [],
              anomalies: [],
              anomalyError: null,
              error: 'Période personnalisée invalide.',
            });
          }
          return forkJoin({
            sensor: this.source
              .getSensors()
              .pipe(map((sensors) => sensors.find((item) => item.id === id) ?? null)),
            readings: this.source.getHistory(id, range.from, range.to),
            anomalies: this.loadAnomalies(id, range.from, range.to).pipe(
              map((anomalies) => ({ anomalies, error: null as string | null })),
              catchError((error: unknown) =>
                of({
                  anomalies: [] as Anomaly[],
                  error: this.errorMessage(error, 'des anomalies'),
                }),
              ),
            ),
          }).pipe(
            map(({ sensor, readings, anomalies }): HistoryLoad => ({
              sensor,
              readings,
              anomalies: anomalies.anomalies,
              anomalyError: anomalies.error,
              error: sensor ? null : `Capteur #${id} introuvable.`,
            })),
            catchError((error: unknown) => {
              this.sensor.set(null);
              this.readings.set([]);
              this.anomalies.set([]);
              this.error.set(this.errorMessage(error));
              this.loading.set(false);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ sensor, readings, anomalies, anomalyError, error }) => {
        this.sensor.set(sensor);
        this.readings.set(readings);
        this.anomalies.set(anomalies);
        this.anomalyError.set(anomalyError);
        this.error.set(error);
        this.tablePage.set(0);
        this.loading.set(false);
      });
  }

  protected selectPeriod(period: HistoryPeriod): void {
    this.period.set(period);
  }

  protected setCustomFrom(event: Event): void {
    this.customFrom.set((event.target as HTMLInputElement).value);
  }

  protected setCustomTo(event: Event): void {
    this.customTo.set((event.target as HTMLInputElement).value);
  }

  protected applyCustomRange(): void {
    if (this.customRangeError()) return;
    this.appliedCustomRange.set({
      from: new Date(this.customFrom()),
      to: new Date(this.customTo()),
    });
  }

  protected reload(): void {
    this.reloadToken.update((value) => value + 1);
  }

  protected setTablePage(page: number): void {
    this.tablePage.set(page);
  }

  protected setTableSize(size: number): void {
    this.tablePage.set(0);
    this.tableSize.set(size);
  }

  protected exportCsv(): void {
    const sensor = this.sensor();
    if (!sensor) return;
    const header = [
      'Identifiant du capteur',
      'Capteur',
      'Date de mesure (UTC)',
      'Valeur',
      'Unité',
      'État de pompe',
    ];
    const rows = this.readings().map((reading) => [
      sensor.id,
      sensor.name,
      reading.measuredAt,
      reading.value ?? '',
      reading.unit ?? '',
      reading.pumpState ?? '',
    ]);
    const content = [header, ...rows].map((row) => row.map(csvCell).join(';')).join('\r\n');
    this.download(
      content,
      `capteur-${sensor.id}-historique-${new Date().toISOString().slice(0, 10)}.csv`,
    );
  }

  protected typeLabel(type: SensorType): string {
    return { PRESSURE: 'Pression', FLOW: 'Débit', LEVEL: 'Niveau', PUMP_STATE: 'État pompe' }[type];
  }

  protected valueLabel(reading: SensorReading): string {
    if (reading.pumpState) {
      return this.pumpStateLabel(reading.pumpState);
    }
    if (reading.value === null) {
      return '—';
    }
    return `${reading.value.toLocaleString('fr-FR', { maximumFractionDigits: 2 })} ${reading.unit ?? this.sensor()?.unit ?? ''}`.trim();
  }

  protected pumpStateLabel(state: string): string {
    return { NORMAL: 'Normal', FAULT: 'Défaut' }[state] ?? state;
  }

  protected measuredAt(value: string): string {
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? 'Date inconnue'
      : date.toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' });
  }

  protected chartValue(value: number | null): string {
    return value === null
      ? '—'
      : `${value.toLocaleString('fr-FR', { maximumFractionDigits: 2 })} ${this.unit()}`.trim();
  }

  protected chartAriaLabel(): string {
    const sensor = this.sensor();
    return `Historique ${sensor ? this.typeLabel(sensor.type).toLowerCase() : 'des mesures'} sur ${this.periodLabel()}`;
  }

  protected thresholdStrokeClass(kind: ThresholdKind): string {
    return kind === 'critical' ? 'stroke-error' : 'stroke-amber-600';
  }

  protected thresholdTextClass(kind: ThresholdKind): string {
    return kind === 'critical' ? 'text-error' : 'text-amber-700';
  }

  protected anomalyStrokeClass(severity: Anomaly['severity']): string {
    return severity === 'CRITICAL' || severity === 'HIGH' ? 'stroke-error' : 'stroke-amber-600';
  }

  protected anomalyFillClass(severity: Anomaly['severity']): string {
    return severity === 'CRITICAL' || severity === 'HIGH' ? 'fill-error' : 'fill-amber-600';
  }

  protected pumpStateClass(state: string | null): string {
    return state === 'FAULT'
      ? 'bg-error-container text-on-error-container'
      : 'bg-tertiary-fixed/30 text-tertiary';
  }

  private sensorThresholds(): SensorThreshold[] {
    const type = this.sensor()?.type;
    if (type === 'PRESSURE') {
      return [
        { label: 'Alerte minimale', value: DEFAULT_THRESHOLDS.pressureMinBar, kind: 'alert' },
        {
          label: 'Critique minimale',
          value: DEFAULT_THRESHOLDS.pressureCriticalBar,
          kind: 'critical',
        },
      ];
    }
    if (type === 'FLOW') {
      return [
        { label: 'Alerte maximale', value: DEFAULT_THRESHOLDS.flowMaxM3h, kind: 'alert' },
        { label: 'Critique maximale', value: DEFAULT_THRESHOLDS.flowCriticalM3h, kind: 'critical' },
      ];
    }
    if (type === 'LEVEL') {
      return [
        { label: 'Alerte minimale', value: DEFAULT_THRESHOLDS.levelMinM, kind: 'alert' },
        { label: 'Critique minimale', value: DEFAULT_THRESHOLDS.levelCriticalM, kind: 'critical' },
      ];
    }
    return [];
  }

  private validateCustomRange(): string | null {
    const from = this.customFrom();
    const to = this.customTo();
    if (!from || !to) return 'Choisissez une date de début et une date de fin.';
    const start = Date.parse(from);
    const end = Date.parse(to);
    if (!Number.isFinite(start) || !Number.isFinite(end))
      return 'La période personnalisée est invalide.';
    if (start > end) return 'La date de début doit précéder la date de fin.';
    if (end > Date.now()) return 'La date de fin ne peut pas être dans le futur.';
    return null;
  }

  private loadAnomalies(sensorId: number, from: Date, to: Date) {
    const query: AnomalyQuery = {
      ...DEFAULT_QUERY,
      from: from.toISOString(),
      to: to.toISOString(),
      page: 0,
      size: 200,
      sort: { field: 'detectedAt', direction: 'asc' },
    };
    return this.anomalySource.list(query).pipe(
      expand((page) =>
        page.page + 1 < page.totalPages
          ? this.anomalySource.list({ ...query, page: page.page + 1 })
          : EMPTY,
      ),
      reduce((anomalies, page) => anomalies.concat(page.content), [] as Anomaly[]),
      map((anomalies) => anomalies.filter((anomaly) => anomaly.sensorId === sensorId)),
    );
  }

  private errorMessage(error: unknown, subject = 'l’historique'): string {
    if (typeof error === 'object' && error !== null && 'status' in error) {
      const status = Number(error.status);
      return status > 0
        ? `Erreur HTTP ${status} lors du chargement de ${subject}.`
        : `Service indisponible pour charger ${subject}. Vérifiez la connexion puis réessayez.`;
    }
    return error instanceof Error
      ? error.message
      : `Erreur inconnue lors du chargement de ${subject}.`;
  }

  private unit(): string {
    return this.sensor()?.unit ?? this.readings().find((reading) => reading.unit)?.unit ?? '';
  }
}
