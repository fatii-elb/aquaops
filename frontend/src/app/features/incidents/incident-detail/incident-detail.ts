import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { NotificationStream } from '../../../core/notification-stream';
import { NotificationToasts } from '../../notifications/notification-toasts';
import { IncidentApi, errorMessage } from '../incident-api';
import {
  actionLabel,
  buttonLabel,
  formatDateTime,
  isClosed,
  nextStatuses,
  priorityClass,
  priorityLabel,
  statusClass,
  statusLabel,
  typeLabel,
} from '../incident-rules';
import { HistoryEntry, Incident, IncidentStatus } from '../incident.model';

/** Détail d'un incident : informations, boutons confirmer / assigner / clore, historique et commentaires. */
@Component({
  selector: 'app-incident-detail',
  imports: [RouterLink, NotificationToasts],
  templateUrl: './incident-detail.html',
  host: { class: 'block' },
})
export class IncidentDetail implements OnInit {
  private api = inject(IncidentApi);
  private auth = inject(AuthService);
  private stream = inject(NotificationStream);
  private route = inject(ActivatedRoute);

  protected readonly incident = signal<Incident | null>(null);
  protected readonly history = signal<HistoryEntry[]>([]);
  protected readonly loaded = signal(false);
  protected readonly notFound = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly assignee = signal('');
  protected readonly commentText = signal('');

  /** Les états que l'on peut atteindre depuis l'état actuel : un bouton par état. */
  protected readonly next = computed(() => {
    const current = this.incident();
    return current ? nextStatuses(current.status) : [];
  });
  protected readonly canComment = computed(() => {
    const current = this.incident();
    return current !== null && !isClosed(current.status);
  });

  private id = 0;
  private lastTick = this.stream.tick();

  constructor() {
    // Une notification arrive : on recharge l'incident (un autre utilisateur l'a peut-être modifié).
    effect(() => {
      const tick = this.stream.tick();
      if (tick !== this.lastTick) {
        this.lastTick = tick;
        if (this.id) {
          this.load();
        }
      }
    });
  }

  ngOnInit(): void {
    this.id = Number(this.route.snapshot.paramMap.get('id'));
    this.load();
  }

  private load(): void {
    this.api.get(this.id).subscribe({
      next: (incident) => {
        this.incident.set(incident);
        this.loaded.set(true);
        this.notFound.set(false);
        if (incident.assignee && !this.assignee()) {
          this.assignee.set(incident.assignee);
        }
      },
      error: (err) => {
        this.loaded.set(true);
        this.notFound.set(err?.status === 404);
        this.error.set(errorMessage(err, "Impossible de charger l'incident."));
      },
    });
    this.loadHistory();
  }

  private loadHistory(): void {
    this.api.history(this.id).subscribe({
      next: (entries) => this.history.set(entries),
      error: () => this.history.set([]),
    });
  }

  /** Confirmer, assigner, démarrer, résoudre ou clore. Le serveur refuse une transition interdite (message affiché). */
  protected change(status: IncidentStatus): void {
    const current = this.incident();
    if (!current || this.busy()) {
      return;
    }
    let assignee = current.assignee;
    if (status === 'ASSIGNED') {
      assignee = this.assignee().trim();
      if (!assignee) {
        this.error.set('Indique le technicien à assigner.');
        return;
      }
    }
    this.error.set(null);
    this.busy.set(true);
    this.api.update(current.id, { status, assignee, actor: this.auth.username || null }).subscribe({
      next: (updated) => {
        this.incident.set(updated);
        this.busy.set(false);
        this.loadHistory();
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
        this.loadHistory();
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(errorMessage(err, "Commentaire refusé."));
      },
    });
  }

  protected actionClass(status: IncidentStatus): string {
    return status === 'CLOSED'
      ? 'bg-surface-container text-on-surface hover:bg-surface-container-high'
      : 'bg-primary text-on-primary hover:brightness-110';
  }

  protected readonly statusLabel = statusLabel;
  protected readonly statusClass = statusClass;
  protected readonly priorityLabel = priorityLabel;
  protected readonly priorityClass = priorityClass;
  protected readonly typeLabel = typeLabel;
  protected readonly actionLabel = actionLabel;
  protected readonly buttonLabel = buttonLabel;
  protected readonly formatDateTime = formatDateTime;
}
