import { Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SeverityBadge } from '../../../shared/badges/severity-badge';
import { StatusBadge } from '../../../shared/badges/status-badge';
import { Incident } from '../../incidents/incident.model';
import { Anomaly, typeLabel } from '../anomaly.model';
import { formatDuration, secondsBetween } from '../anomaly-rules';

/** Fiche d'une anomalie : identité, horodatages, délais et incident associé. */
@Component({
  selector: 'app-anomaly-detail',
  imports: [RouterLink, SeverityBadge, StatusBadge],
  templateUrl: './anomaly-detail.html',
  host: { '(document:keydown.escape)': 'closed.emit()' },
})
export class AnomalyDetail {
  readonly anomaly = input.required<Anomaly>();
  readonly zoneName = input.required<string>();
  readonly sensorName = input.required<string>();
  readonly incident = input<Incident | null>(null);

  readonly closed = output<void>();

  protected readonly label = computed(() => typeLabel(this.anomaly().type));
  /** Délai entre la mesure et la détection (NFR-02 : 10 s au plus pour 95 % des cas). */
  protected readonly detectionDelay = computed(() =>
    formatDuration(secondsBetween(this.anomaly().measuredAt, this.anomaly().detectedAt)),
  );
  protected readonly duration = computed(() =>
    formatDuration(secondsBetween(this.anomaly().detectedAt, this.anomaly().resolvedAt)),
  );

  protected date(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString('fr-FR') : '—';
  }
}
