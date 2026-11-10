import { Component, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { AppShell } from '../../../core/layout/app-shell';
import { NotificationStream } from '../../../core/notification-stream';
import { IncidentApi } from '../incident-api';
import { IncidentCreate } from '../incident-create/incident-create';
import { IncidentPanel } from '../incident-panel/incident-panel';
import { PRIORITIES, STAGES, incidentCode, priorityClass, priorityLabel, sortIncidents, statusClass, statusLabel, timeAgo, typeLabel } from '../incident-rules';
import { Incident, IncidentStatus, Priority } from '../incident.model';
import { ZoneNames } from '../zone-names';

/**
 * Centre de gestion des incidents : barre de pipeline, liste filtrable à gauche, incident choisi à droite.
 * La liste se rafraîchit toutes les 5 s et dès qu'une notification arrive.
 */
@Component({
  selector: 'app-incident-center',
  imports: [AppShell, IncidentPanel, IncidentCreate],
  templateUrl: './incident-center.html',
})
export class IncidentCenter implements OnInit, OnDestroy {
  private api = inject(IncidentApi);
  private stream = inject(NotificationStream);
  private route = inject(ActivatedRoute);
  protected zones = inject(ZoneNames);

  protected readonly stages = STAGES;
  protected readonly priorities = PRIORITIES;

  protected readonly incidents = signal<Incident[]>([]);
  protected readonly loaded = signal(false);
  protected readonly error = signal(false);
  protected readonly statusFilter = signal<IncidentStatus | ''>('');
  protected readonly priorityFilter = signal<Priority | ''>('');
  protected readonly query = signal('');
  protected readonly showCreate = signal(false);
  protected readonly now = signal(Date.now());

  /** Incident choisi à la main ou par l'adresse /incidents/:id ; sinon le premier de la liste. */
  private readonly picked = signal<number | null>(null);
  private readonly routeId = toSignal(this.route.paramMap, { initialValue: this.route.snapshot?.paramMap ?? null });

  protected readonly rows = computed(() => {
    const status = this.statusFilter();
    const priority = this.priorityFilter();
    const q = this.query().trim().toLowerCase();
    const filtered = this.incidents().filter((i) => {
      if (status && i.status !== status) {
        return false;
      }
      if (priority && i.priority !== priority) {
        return false;
      }
      if (!q) {
        return true;
      }
      const text = [incidentCode(i), typeLabel(i.type), i.type, i.description ?? '', i.assignee ?? '', this.zones.name(i.zoneId), i.sensorId ?? '']
        .join(' ')
        .toLowerCase();
      return text.includes(q);
    });
    return sortIncidents(filtered);
  });

  protected readonly selected = computed<number | null>(() => {
    const rows = this.rows();
    const wanted = this.picked();
    if (wanted !== null && this.incidents().some((i) => i.id === wanted)) {
      return wanted;
    }
    return rows[0]?.id ?? null;
  });

  protected readonly total = computed(() => this.incidents().length);
  protected readonly activeCount = computed(() => this.incidents().filter((i) => i.status !== 'CLOSED' && i.status !== 'RESOLVED').length);
  protected readonly stageCounts = computed(() => {
    const counts: Partial<Record<string, number>> = {};
    this.incidents().forEach((i) => (counts[i.status] = (counts[i.status] ?? 0) + 1));
    return counts;
  });
  protected readonly priorityCounts = computed(() => {
    const counts: Partial<Record<string, number>> = {};
    this.incidents().forEach((i) => (counts[i.priority] = (counts[i.priority] ?? 0) + 1));
    return counts;
  });

  private timer: ReturnType<typeof setInterval> | undefined;
  private lastTick = this.stream.tick();

  constructor() {
    // Adresse /incidents/:id : on choisit cet incident.
    effect(() => {
      const id = Number(this.routeId()?.get('id'));
      if (Number.isInteger(id) && id > 0) {
        this.picked.set(id);
      }
    });
    // Une notification arrive : on recharge la liste tout de suite.
    effect(() => {
      const tick = this.stream.tick();
      if (tick !== this.lastTick) {
        this.lastTick = tick;
        this.load();
      }
    });
  }

  ngOnInit(): void {
    this.zones.load();
    this.load();
    this.timer = setInterval(() => this.load(), 5000);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  protected load(): void {
    this.api.list().subscribe({
      next: (list) => {
        this.incidents.set(list);
        this.loaded.set(true);
        this.error.set(false);
        this.now.set(Date.now());
      },
      error: () => this.error.set(true),
    });
  }

  protected select(id: number): void {
    this.picked.set(id);
  }

  /** Cliquer deux fois sur la même étape retire le filtre. */
  protected toggleStatus(status: IncidentStatus): void {
    this.statusFilter.set(this.statusFilter() === status ? '' : status);
  }

  protected setPriority(priority: Priority | ''): void {
    this.priorityFilter.set(priority);
  }

  protected onCreated(incident: Incident): void {
    this.showCreate.set(false);
    this.picked.set(incident.id);
    this.load();
  }

  protected percent(count: number | undefined): number {
    const total = this.total();
    return total === 0 || !count ? 0 : Math.round((count / total) * 100);
  }

  protected age(iso: string): string {
    return timeAgo(iso, this.now());
  }

  protected readonly code = incidentCode;
  protected readonly statusLabel = statusLabel;
  protected readonly statusClass = statusClass;
  protected readonly priorityLabel = priorityLabel;
  protected readonly priorityClass = priorityClass;
  protected readonly typeLabel = typeLabel;
}
