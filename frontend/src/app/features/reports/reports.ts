import { Component, HostListener, OnInit, ViewEncapsulation, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { AppShell } from '../../core/layout/app-shell';
import { errorMessage } from '../incidents/incident-api';
import {
  ALL_STATUSES,
  PRIORITIES,
  formatDateTime,
  incidentCode,
  priorityClass,
  priorityLabel,
  statusClass,
  statusLabel,
  typeLabel,
} from '../incidents/incident-rules';
import { Incident, IncidentStatus, Priority, ZoneInfo } from '../incidents/incident.model';
import { Intervention } from '../interventions/intervention.model';
import { CSV_DOWNLOAD } from './csv-download';
import { REPORTS_DATA_SOURCE_CONFIG, ReportsDataSource } from './reports-data-source';
import {
  CHART_HEIGHT,
  CHART_WIDTH,
  PERIOD_PRESETS,
  PeriodPreset,
  computeIndicators,
  csvFileName,
  filterByPeriod,
  filterReport,
  formatDuration,
  incidentsPerDay,
  lineChart,
  periodRange,
  rangeError,
  toCsv,
} from './reports-logic';

/**
 * Rapports & Analyses (FE-10 indicateurs, FE-13 rapport et export).
 * Réservé aux rôles superviseur et administrateur (voir app.routes.ts) ; l'API refuse aussi les autres (403).
 * La période choisie s'applique aux indicateurs et au rapport ; priorité, état et zone ne filtrent que le rapport.
 */
@Component({
  selector: 'app-reports',
  imports: [AppShell],
  templateUrl: './reports.html',
  styleUrl: './reports.css',
  encapsulation: ViewEncapsulation.None,
  host: { class: 'block' },
})
export class Reports implements OnInit {
  private source = inject(ReportsDataSource);
  private download = inject(CSV_DOWNLOAD);

  readonly pageSize = REPORTS_DATA_SOURCE_CONFIG.pageSize;
  readonly presets = PERIOD_PRESETS;
  readonly priorities = PRIORITIES;
  readonly statuses = ALL_STATUSES;
  readonly chartWidth = CHART_WIDTH;
  readonly chartHeight = CHART_HEIGHT;

  // Données chargées.
  readonly incidents = signal<Incident[]>([]);
  readonly interventions = signal<Intervention[] | null>([]);
  readonly zones = signal<ZoneInfo[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Filtres.
  readonly now = signal(new Date());
  readonly preset = signal<PeriodPreset>('30d');
  readonly customFrom = signal('');
  readonly customTo = signal('');
  readonly priority = signal<Priority | ''>('');
  readonly status = signal<IncidentStatus | ''>('');
  readonly zoneId = signal<number | null>(null);
  readonly page = signal(0);
  /** Vrai pendant l'impression : le rapport affiche toutes les lignes, sans pagination. */
  readonly printing = signal(false);

  // Résultats calculés.
  readonly range = computed(() => periodRange(this.preset(), this.customFrom(), this.customTo(), this.now()));
  readonly rangeError = computed(() => rangeError(this.range()));
  readonly periodIncidents = computed(() => filterByPeriod(this.incidents(), this.range()));
  readonly indicators = computed(() => computeIndicators(this.periodIncidents(), this.interventions(), this.range()));
  readonly chart = computed(() => lineChart(incidentsPerDay(this.periodIncidents())));
  readonly report = computed(() =>
    filterReport(this.periodIncidents(), { priority: this.priority(), status: this.status(), zoneId: this.zoneId() }),
  );
  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.report().length / this.pageSize)));
  readonly rows = computed(() =>
    this.printing() ? this.report() : this.report().slice(this.page() * this.pageSize, (this.page() + 1) * this.pageSize),
  );
  /** Zones à proposer dans le filtre : celles de l'API, plus celles vues dans les incidents. */
  readonly zoneChoices = computed(() => {
    const ids = new Set<number>([...this.zones().map((z) => z.id), ...this.incidents().map((i) => i.zoneId)]);
    return [...ids].sort((a, b) => a - b).map((id) => ({ id, name: this.zoneName(id) }));
  });
  readonly periodLabel = computed(() => {
    const range = this.range();
    const day = (d: Date | null) => (d ? d.toISOString().slice(0, 10) : null);
    const from = day(range.from);
    const to = day(range.to);
    if (!from && !to) return 'Toute la période';
    return `Du ${from ?? 'début'} au ${to ?? 'jour même'}`;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.now.set(new Date());
    forkJoin({ incidents: this.source.incidents(), interventions: this.source.interventions(), zones: this.source.zones() }).subscribe({
      next: ({ incidents, interventions, zones }) => {
        this.incidents.set(incidents);
        this.interventions.set(interventions);
        this.zones.set(zones);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.incidents.set([]);
        this.error.set(errorMessage(error, 'Impossible de charger les incidents.'));
        this.loading.set(false);
      },
    });
  }

  // ----- Changement de filtres : on revient toujours à la première page -----
  setPreset(value: string): void {
    this.preset.set(value as PeriodPreset);
    this.page.set(0);
  }
  setCustomFrom(value: string): void {
    this.customFrom.set(value);
    this.page.set(0);
  }
  setCustomTo(value: string): void {
    this.customTo.set(value);
    this.page.set(0);
  }
  setPriority(value: string): void {
    this.priority.set(value as Priority | '');
    this.page.set(0);
  }
  setStatus(value: string): void {
    this.status.set(value as IncidentStatus | '');
    this.page.set(0);
  }
  setZone(value: string): void {
    this.zoneId.set(value === '' ? null : Number(value));
    this.page.set(0);
  }
  resetReportFilters(): void {
    this.priority.set('');
    this.status.set('');
    this.zoneId.set(null);
    this.page.set(0);
  }

  previous(): void {
    if (this.page() > 0) this.page.update((p) => p - 1);
  }
  next(): void {
    if (this.page() + 1 < this.totalPages()) this.page.update((p) => p + 1);
  }

  /** Exporte exactement les lignes filtrées du rapport (toutes les pages, pas seulement celle affichée). */
  exportCsv(): void {
    const lines = this.report();
    if (lines.length === 0) return;
    this.download(
      toCsv(lines, {
        zone: (id) => this.zoneName(id),
        type: typeLabel,
        priority: priorityLabel,
        status: statusLabel,
      }),
      csvFileName(new Date()),
    );
  }

  /** Version imprimable : le menu disparaît (reports.css) et toutes les lignes s'affichent. */
  print(): void {
    this.printing.set(true);
    setTimeout(() => {
      window.print();
      this.printing.set(false);
    }, 50);
  }

  // Impression lancée par le navigateur (Ctrl+P) : même affichage complet.
  @HostListener('window:beforeprint') onBeforePrint(): void {
    this.printing.set(true);
  }
  @HostListener('window:afterprint') onAfterPrint(): void {
    this.printing.set(false);
  }

  // ----- Affichage -----
  zoneName(id: number): string {
    return this.zones().find((z) => z.id === id)?.name ?? `Zone ${id}`;
  }
  readonly duration = formatDuration;
  readonly priorityLabel = priorityLabel;
  readonly priorityClass = priorityClass;
  readonly statusLabel = statusLabel;
  readonly statusClass = statusClass;
  readonly typeLabel = typeLabel;
  readonly code = incidentCode;
  readonly when = formatDateTime;
}
