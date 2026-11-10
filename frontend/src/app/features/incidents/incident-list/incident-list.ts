import { Component, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationStream } from '../../../core/notification-stream';
import { NotificationToasts } from '../../notifications/notification-toasts';
import { IncidentApi } from '../incident-api';
import { ALL_STATUSES, formatDateTime, priorityClass, priorityLabel, sortIncidents, statusClass, statusLabel, typeLabel } from '../incident-rules';
import { Incident, IncidentStatus } from '../incident.model';

/** Liste des incidents (état, priorité, zone), rafraîchie toutes les 5 s et à chaque notification reçue. */
@Component({
  selector: 'app-incident-list',
  imports: [RouterLink, NotificationToasts],
  templateUrl: './incident-list.html',
  host: { class: 'block' },
})
export class IncidentList implements OnInit, OnDestroy {
  private api = inject(IncidentApi);
  private stream = inject(NotificationStream);

  protected readonly statuses = ALL_STATUSES;
  protected readonly incidents = signal<Incident[]>([]);
  protected readonly loaded = signal(false);
  protected readonly error = signal(false);
  protected readonly filter = signal<IncidentStatus | ''>('');

  protected readonly rows = computed(() => sortIncidents(this.incidents()));
  protected readonly activeCount = computed(() => this.incidents().filter((i) => i.status !== 'CLOSED' && i.status !== 'RESOLVED').length);
  protected readonly criticalCount = computed(
    () => this.incidents().filter((i) => i.priority === 'CRITICAL' && i.status !== 'CLOSED' && i.status !== 'RESOLVED').length,
  );

  private timer: ReturnType<typeof setInterval> | undefined;
  private lastTick = this.stream.tick();

  constructor() {
    // Une notification vient d'arriver : on recharge tout de suite la liste.
    effect(() => {
      const tick = this.stream.tick();
      if (tick !== this.lastTick) {
        this.lastTick = tick;
        this.load();
      }
    });
  }

  ngOnInit(): void {
    this.load();
    this.timer = setInterval(() => this.load(), 5000);
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }

  protected setFilter(value: string): void {
    this.filter.set(value as IncidentStatus | '');
    this.load();
  }

  protected load(): void {
    const status = this.filter() || undefined;
    this.api.list(status).subscribe({
      next: (list) => {
        this.incidents.set(list);
        this.loaded.set(true);
        this.error.set(false);
      },
      error: () => this.error.set(true),
    });
  }

  protected readonly statusLabel = statusLabel;
  protected readonly statusClass = statusClass;
  protected readonly priorityLabel = priorityLabel;
  protected readonly priorityClass = priorityClass;
  protected readonly typeLabel = typeLabel;
  protected readonly formatDateTime = formatDateTime;
}
