import { Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { AuthService } from '../../../core/auth.service';
import { NotificationStream } from '../../../core/notification-stream';
import { InterventionList } from '../../interventions/intervention-list';
import { IncidentApi, errorMessage } from '../incident-api';
import {
  actionLabel,
  buttonLabel,
  delaySeconds,
  formatDateTime,
  formatTime,
  incidentCode,
  isClosed,
  nextStatuses,
  priorityClass,
  priorityLabel,
  statusClass,
  statusLabel,
  typeLabel,
} from '../incident-rules';
import { HistoryEntry, Incident, IncidentStatus } from '../incident.model';
import { ZoneNames } from '../zone-names';

/** Panneau de droite : l'incident choisi, ses actions, sa chronologie et son journal de bord. */
@Component({
  selector: 'app-incident-panel',
  imports: [InterventionList],
  templateUrl: './incident-panel.html',
  host: { class: 'flex flex-col gap-space-lg' },
})
export class IncidentPanel {
  private api = inject(IncidentApi);
  private auth = inject(AuthService);
  private stream = inject(NotificationStream);
  protected zones = inject(ZoneNames);

  readonly incidentId = input.required<number>();
  /** Émis après une action réussie, pour que la liste se mette à jour. */
  readonly changed = output<void>();

  protected readonly incident = signal<Incident | null>(null);
  protected readonly history = signal<HistoryEntry[]>([]);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly assignee = signal('');
  protected readonly commentText = signal('');

  private readonly next = computed(() => {
    const current = this.incident();
    return current ? nextStatuses(current.status) : [];
  });
  /** Étapes suivantes (confirmer, démarrer, résoudre) : boutons principaux. */
  protected readonly forward = computed(() => this.next().filter((s) => s !== 'ASSIGNED' && s !== 'CLOSED'));
  protected readonly canAssign = computed(() => this.next().includes('ASSIGNED'));
  protected readonly canClose = computed(() => this.next().includes('CLOSED'));
  protected readonly finished = computed(() => this.next().length === 0);
  protected readonly canComment = computed(() => {
    const current = this.incident();
    return current !== null && !isClosed(current.status);
  });
  /** Chronologie : tout sauf les commentaires. */
  protected readonly events = computed(() => this.history().filter((e) => e.action !== 'COMMENTED'));
  /** Journal de bord : les commentaires. */
  protected readonly notes = computed(() => this.history().filter((e) => e.action === 'COMMENTED'));
  protected readonly delay = computed(() => {
    const current = this.incident();
    return current ? delaySeconds(current.measuredAt, current.createdAt) : null;
  });

  constructor() {
    this.zones.load();
    // On (re)charge quand on change d'incident et à chaque notification reçue.
    effect(() => {
      const id = this.incidentId();
      this.stream.tick();
      untracked(() => this.load(id));
    });
  }

  private load(id: number): void {
    if (this.incident()?.id !== id) {
      this.incident.set(null);
      this.history.set([]);
      this.notFound.set(false);
      this.error.set(null);
      this.assignee.set('');
      this.commentText.set('');
    }
    this.api.get(id).subscribe({
      next: (incident) => {
        if (id !== this.incidentId()) {
          return;
        }
        this.incident.set(incident);
        this.notFound.set(false);
        if (incident.assignee && !this.assignee()) {
          this.assignee.set(incident.assignee);
        }
      },
      error: (err) => {
        if (id !== this.incidentId()) {
          return;
        }
        this.notFound.set(err?.status === 404);
        this.error.set(errorMessage(err, "Impossible de charger l'incident."));
      },
    });
    this.loadHistory(id);
  }

  private loadHistory(id: number): void {
    this.api.history(id).subscribe({
      next: (entries) => id === this.incidentId() && this.history.set(entries),
      error: () => id === this.incidentId() && this.history.set([]),
    });
  }

  /** Confirmer, affecter, démarrer, résoudre ou clore. Le serveur refuse une transition interdite (message affiché). */
  protected change(status: IncidentStatus): void {
    const current = this.incident();
    if (!current || this.busy()) {
      return;
    }
    let assignee = current.assignee;
    if (status === 'ASSIGNED') {
      assignee = this.assignee().trim();
      if (!assignee) {
        this.error.set('Indique le technicien à affecter.');
        return;
      }
    }
    this.error.set(null);
    this.busy.set(true);
    this.api.update(current.id, { status, assignee, actor: this.auth.username || 'anonyme' }).subscribe({
      next: (updated) => {
        this.incident.set(updated);
        this.busy.set(false);
        this.loadHistory(updated.id);
        this.changed.emit();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err, 'Action impossible.'));
      },
    });
  }

  protected sendComment(): void {
    const current = this.incident();
    const text = this.commentText().trim();
    if (!current || !text || this.busy()) {
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.api.addComment(current.id, text, this.auth.username || 'anonyme').subscribe({
      next: () => {
        this.commentText.set('');
        this.busy.set(false);
        this.loadHistory(current.id);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err, 'Commentaire refusé.'));
      },
    });
  }

  protected buttonClass(status: IncidentStatus): string {
    return status === 'RESOLVED' ? 'bg-tertiary text-on-tertiary hover:bg-tertiary-container' : 'bg-primary text-on-primary hover:brightness-110';
  }

  protected readonly code = incidentCode;
  protected readonly statusLabel = statusLabel;
  protected readonly statusClass = statusClass;
  protected readonly priorityLabel = priorityLabel;
  protected readonly priorityClass = priorityClass;
  protected readonly typeLabel = typeLabel;
  protected readonly actionLabel = actionLabel;
  protected readonly buttonLabel = buttonLabel;
  protected readonly formatDateTime = formatDateTime;
  protected readonly formatTime = formatTime;
}
