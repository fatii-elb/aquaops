import { Component, computed, input } from '@angular/core';
import { SEVERITY_CLASSES, SEVERITY_LABELS, Severity } from './badge-labels';

/** Pastille de gravité (faible, moyenne, haute, critique). */
@Component({
  selector: 'app-severity-badge',
  template: `<span
    class="inline-flex items-center rounded-full px-space-sm py-space-2xs font-label-sm text-label-sm font-semibold {{ classes() }}"
    data-testid="severity-badge">{{ label() }}</span>`,
})
export class SeverityBadge {
  readonly severity = input.required<Severity>();
  protected readonly label = computed(() => SEVERITY_LABELS[this.severity()] ?? this.severity());
  protected readonly classes = computed(() => SEVERITY_CLASSES[this.severity()] ?? SEVERITY_CLASSES.MEDIUM);
}
