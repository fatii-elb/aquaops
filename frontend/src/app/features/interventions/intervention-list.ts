import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { AuthService } from '../../core/auth.service';
import { errorMessage } from '../incidents/incident-api';
import { formatDateTime } from '../incidents/incident-rules';
import { InterventionApi } from './intervention-api';
import { INTERVENTION_STATUS_LABELS, Intervention, InterventionStatus, nextStatus } from './intervention.model';

/** Interventions d'un incident : créer, affecter, faire avancer le statut (FR-19, FR-20). */
@Component({
  selector: 'app-intervention-list',
  templateUrl: './intervention-list.html',
  host: { class: 'block' },
})
export class InterventionList {
  private api = inject(InterventionApi);
  private auth = inject(AuthService);

  readonly incidentId = input.required<number>();
  readonly incidentClosed = input(false);

  protected readonly items = signal<Intervention[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      const id = this.incidentId();
      untracked(() => this.load(id));
    });
  }

  /** Créer et affecter : réservé au superviseur et à l'administrateur (le serveur vérifie aussi). */
  protected canManage(): boolean {
    return this.auth.hasAnyRole(['superviseur', 'administrateur']);
  }

  protected next(item: Intervention): InterventionStatus | null {
    return nextStatus(item.status);
  }

  protected load(id: number): void {
    this.api.list(id).subscribe({
      next: (list) => id === this.incidentId() && this.items.set(list),
      error: (err) => this.error.set(errorMessage(err, 'Impossible de charger les interventions.')),
    });
  }

  protected create(): void {
    this.run(this.api.create(this.incidentId()), 'Création impossible.');
  }

  protected assign(item: Intervention, team: string, assignedTo: string): void {
    if (!team.trim()) {
      this.error.set("Indique l'équipe à affecter.");
      return;
    }
    this.run(this.api.assign(item.id, { team: team.trim(), assignedTo: assignedTo.trim() || null }), 'Affectation impossible.');
  }

  /** Le bouton est visible pour tous : un autre utilisateur reçoit le 403 du serveur, affiché ici. */
  protected advance(item: Intervention): void {
    const status = nextStatus(item.status);
    if (status) {
      this.run(this.api.changeStatus(item.id, status), 'Changement de statut impossible.');
    }
  }

  private run(call: ReturnType<InterventionApi['create']>, fallback: string): void {
    if (this.busy()) {
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    call.subscribe({
      next: () => {
        this.busy.set(false);
        this.load(this.incidentId());
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(
          err?.status === 403
            ? 'Action refusée : seul le technicien assigné ou un superviseur peut le faire.'
            : errorMessage(err, fallback),
        );
      },
    });
  }

  protected statusClass(status: InterventionStatus): string {
    return status === 'DONE'
      ? 'bg-tertiary text-on-tertiary'
      : status === 'IN_PROGRESS'
        ? 'bg-primary text-on-primary'
        : 'bg-surface-container-high text-on-surface';
  }

  protected readonly labels = INTERVENTION_STATUS_LABELS;
  protected readonly formatDateTime = formatDateTime;
}