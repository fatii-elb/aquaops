import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, catchError, forkJoin, map, merge, of, switchMap, tap } from 'rxjs';
import { AppShell } from '../../core/layout/app-shell';
import { SeverityBadge } from '../../shared/badges/severity-badge';
import { StatusBadge } from '../../shared/badges/status-badge';
import { AnomalyStatus } from '../../shared/badges/badge-labels';
import { HttpErrorInfo, describeHttpError } from '../../shared/http-errors';
import { Pagination } from '../../shared/pagination/pagination';
import { StateBlock } from '../../shared/state-block/state-block';
import { pollEvery } from '../../shared/polling';
import { Incident } from '../incidents/incident.model';
import { AnomalyDataSource } from './anomaly-data-source';
import { ANOMALY_DATA_SOURCE_CONFIG } from './anomaly-data-source.config';
import { AnomalyContext } from './anomaly-context';
import { AnomalyDetail } from './anomaly-detail/anomaly-detail';
import { ApiAnomalyDataSource } from './api-anomaly-data-source';
import { MockAnomalyDataSource } from './mock-anomaly-data-source';
import {
  ANOMALY_TYPES,
  Anomaly,
  AnomalyPage,
  AnomalyQuery,
  DEFAULT_QUERY,
  SortField,
  typeLabel,
} from './anomaly.model';
import { fromLocalInput, incidentsByAnomaly, matchesSearch, nextSort, toLocalInput } from './anomaly-rules';

/** Requête du compteur « anomalies ouvertes » : une seule ligne suffit, on lit totalElements. */
const OPEN_COUNT_QUERY: AnomalyQuery = { ...DEFAULT_QUERY, status: 'OPEN', size: 1 };

/**
 * Page « Alertes & Anomalies » (FE-9) : liste des anomalies détectées, filtres, tri, pagination,
 * fiche détaillée et rafraîchissement automatique toutes les 10 secondes.
 */
@Component({
  selector: 'app-anomalies-page',
  imports: [AppShell, RouterLink, StateBlock, Pagination, SeverityBadge, StatusBadge, AnomalyDetail],
  providers: [
    {
      provide: AnomalyDataSource,
      useClass: ANOMALY_DATA_SOURCE_CONFIG.mode === 'mock' ? MockAnomalyDataSource : ApiAnomalyDataSource,
    },
  ],
  templateUrl: './anomalies-page.html',
})
export class AnomaliesPage implements OnInit {
  private source = inject(AnomalyDataSource);
  private destroyRef = inject(DestroyRef);
  protected ctx = inject(AnomalyContext);

  protected readonly refreshSeconds = ANOMALY_DATA_SOURCE_CONFIG.refreshMs / 1000;
  protected readonly types = ANOMALY_TYPES;
  protected readonly typeLabel = typeLabel;
  protected readonly toLocalInput = toLocalInput;

  protected readonly query = signal<AnomalyQuery>({ ...DEFAULT_QUERY, size: ANOMALY_DATA_SOURCE_CONFIG.defaultPageSize });
  protected readonly search = signal('');
  protected readonly data = signal<AnomalyPage | null>(null);
  protected readonly error = signal<HttpErrorInfo | null>(null);
  protected readonly refreshing = signal(false);
  protected readonly lastUpdated = signal<Date | null>(null);
  protected readonly openCount = signal<number | null>(null);
  protected readonly incidents = signal<Map<string, Incident>>(new Map());
  protected readonly selected = signal<Anomaly | null>(null);

  private readonly refresh$ = new Subject<void>();

  /** Lignes affichées : la page reçue, restreinte par la recherche rapide. */
  protected readonly rows = computed(() => {
    const page = this.data();
    return page ? page.content.filter((a) => matchesSearch(a, this.search(), this.ctx)) : [];
  });

  /** La fiche suit les mises à jour : on prend la version la plus récente de l'anomalie quand elle est encore listée. */
  protected readonly detail = computed(() => {
    const current = this.selected();
    return current ? (this.data()?.content.find((a) => a.id === current.id) ?? current) : null;
  });

  /** Nombre d'anomalies de la page qui ont déjà donné lieu à un incident. */
  protected readonly linkedCount = computed(() => {
    const byAnomaly = this.incidents();
    return this.data()?.content.filter((a) => byAnomaly.has(a.id)).length ?? 0;
  });

  protected readonly filtersActive = computed(() => {
    const q = this.query();
    return q.zoneId !== null || !!q.type || !!q.status || !!q.from || !!q.to || this.search().trim() !== '';
  });

  ngOnInit(): void {
    this.ctx.loadNames();
    merge(pollEvery(ANOMALY_DATA_SOURCE_CONFIG.refreshMs), this.refresh$)
      .pipe(
        tap(() => this.refreshing.set(true)),
        // switchMap annule la requête en cours : jamais deux chargements qui se chevauchent.
        switchMap(() => this.fetch()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private fetch() {
    return forkJoin({
      page: this.source.list(this.query()).pipe(
        map((page) => ({ page, error: null as HttpErrorInfo | null })),
        catchError((err: unknown) => of({ page: null as AnomalyPage | null, error: describeHttpError(err, 'les anomalies') })),
      ),
      open: this.source.list(OPEN_COUNT_QUERY).pipe(
        map((p) => p.totalElements as number | null),
        catchError(() => of(null as number | null)),
      ),
      incidents: this.ctx.incidents$(),
    }).pipe(
      tap(({ page, open, incidents }) => {
        this.refreshing.set(false);
        this.error.set(page.error);
        if (page.page) {
          this.data.set(page.page);
          this.lastUpdated.set(new Date());
        }
        if (open !== null) {
          this.openCount.set(open);
        }
        this.incidents.set(incidentsByAnomaly(incidents));
      }),
    );
  }

  /* ---- actions ---- */

  protected reload(): void {
    this.refresh$.next();
  }

  private patch(patch: Partial<AnomalyQuery>): void {
    this.query.update((q) => ({ ...q, page: 0, ...patch }));
    this.reload();
  }

  protected setZone(value: string): void {
    this.patch({ zoneId: value === '' ? null : Number(value) });
  }

  protected setType(value: string): void {
    this.patch({ type: value === '' ? null : value });
  }

  protected setStatus(value: string): void {
    this.patch({ status: value === '' ? null : (value as AnomalyStatus) });
  }

  protected setFrom(value: string): void {
    this.patch({ from: fromLocalInput(value) });
  }

  protected setTo(value: string): void {
    this.patch({ to: fromLocalInput(value) });
  }

  protected setSearch(value: string): void {
    this.search.set(value);
  }

  protected resetFilters(): void {
    this.search.set('');
    this.patch({ zoneId: null, type: null, status: null, from: null, to: null });
  }

  protected toggleSort(field: SortField): void {
    this.patch({ sort: nextSort(this.query().sort, field) });
  }

  protected setPage(page: number): void {
    this.query.update((q) => ({ ...q, page }));
    this.reload();
  }

  protected setSize(size: number): void {
    this.patch({ size });
  }

  protected open(anomaly: Anomaly): void {
    this.selected.set(anomaly);
  }

  protected close(): void {
    this.selected.set(null);
  }

  protected incidentOf(anomaly: Anomaly): Incident | null {
    return this.incidents().get(anomaly.id) ?? null;
  }

  protected date(iso: string): string {
    return new Date(iso).toLocaleString('fr-FR');
  }

  protected time(date: Date): string {
    return date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  protected ariaSort(field: SortField): 'ascending' | 'descending' | 'none' {
    const s = this.query().sort;
    return s.field !== field ? 'none' : s.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected sortIcon(field: SortField): string {
    const s = this.query().sort;
    return s.field !== field ? 'unfold_more' : s.direction === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }
}
