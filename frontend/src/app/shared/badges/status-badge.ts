import { Component, computed, input } from '@angular/core';
import { AnomalyStatus, STATUS_CLASSES, STATUS_LABELS } from './badge-labels';

/** Pastille de statut d'une anomalie (ouverte, résolue). */
@Component({
  selector: 'app-status-badge',
  template: `<span
    class="inline-flex items-center gap-space-2xs rounded-full px-space-sm py-space-2xs font-label-sm text-label-sm font-semibold {{ classes() }}"
    data-testid="status-badge">
    <span class="h-1.5 w-1.5 rounded-full bg-current"></span>{{ label() }}</span>`,
})
export class StatusBadge {
  readonly status = input.required<AnomalyStatus>();
  protected readonly label = computed(() => STATUS_LABELS[this.status()] ?? this.status());
  protected readonly classes = computed(() => STATUS_CLASSES[this.status()] ?? STATUS_CLASSES.OPEN);
}
