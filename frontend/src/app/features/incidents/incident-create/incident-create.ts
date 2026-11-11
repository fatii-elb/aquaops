import { Component, inject, input, output, signal } from '@angular/core';
import { AuthService } from '../../../core/auth.service';
import { IncidentApi, errorMessage } from '../incident-api';
import { INCIDENT_TYPES, PRIORITIES, priorityLabel, typeLabel } from '../incident-rules';
import { Incident, Priority, ZoneInfo } from '../incident.model';

/** Fenêtre « Déclarer un incident manuel ». */
@Component({
  selector: 'app-incident-create',
  templateUrl: './incident-create.html',
})
export class IncidentCreate {
  private api = inject(IncidentApi);
  private auth = inject(AuthService);

  readonly zones = input<ZoneInfo[]>([]);
  readonly created = output<Incident>();
  readonly cancelled = output<void>();

  protected readonly types = INCIDENT_TYPES;
  protected readonly priorities = PRIORITIES;
  protected readonly zoneId = signal(0);
  protected readonly type = signal(INCIDENT_TYPES[0]);
  protected readonly priority = signal<Priority>('MEDIUM');
  protected readonly description = signal('');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected submit(): void {
    if (this.busy()) {
      return;
    }
    if (!(this.zoneId() > 0)) {
      this.error.set('Choisis la zone concernée.');
      return;
    }
    this.error.set(null);
    this.busy.set(true);
    this.api
      .create({
        zoneId: this.zoneId(),
        type: this.type(),
        priority: this.priority(),
        description: this.description().trim() || null,
        actor: this.auth.username || 'anonyme',
      })
      .subscribe({
        next: (incident) => {
          this.busy.set(false);
          this.created.emit(incident);
        },
        error: (err) => {
          this.busy.set(false);
          this.error.set(errorMessage(err, "Création impossible."));
        },
      });
  }

  protected readonly typeLabel = typeLabel;
  protected readonly priorityLabel = priorityLabel;
}
