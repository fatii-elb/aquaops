import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { SEVERITY_LABELS, STATUS_LABELS } from './badge-labels';
import { SeverityBadge } from './severity-badge';
import { StatusBadge } from './status-badge';

describe('badges', () => {
  it('libellés français des quatre gravités et des deux statuts', () => {
    expect(SEVERITY_LABELS).toEqual({ LOW: 'Faible', MEDIUM: 'Moyenne', HIGH: 'Haute', CRITICAL: 'Critique' });
    expect(STATUS_LABELS).toEqual({ OPEN: 'Ouverte', RESOLVED: 'Résolue' });
  });

  it('SeverityBadge affiche la gravité, en rouge pour critique', () => {
    const f = TestBed.createComponent(SeverityBadge);
    f.componentRef.setInput('severity', 'CRITICAL');
    f.detectChanges();
    const badge = f.nativeElement.querySelector('[data-testid="severity-badge"]') as HTMLElement;
    expect(badge.textContent).toContain('Critique');
    expect(badge.className).toContain('bg-error-container');
  });

  it('StatusBadge affiche le statut', () => {
    const f = TestBed.createComponent(StatusBadge);
    f.componentRef.setInput('status', 'RESOLVED');
    f.detectChanges();
    expect(f.nativeElement.querySelector('[data-testid="status-badge"]').textContent).toContain('Résolue');
  });
});
