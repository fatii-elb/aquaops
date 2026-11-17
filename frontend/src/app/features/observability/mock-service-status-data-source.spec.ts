import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { MockServiceStatusDataSource } from './mock-service-status-data-source';
import { SERVICE_LABELS } from './service-status.model';

describe('MockServiceStatusDataSource', () => {
  function make(): MockServiceStatusDataSource {
    TestBed.configureTestingModule({ providers: [MockServiceStatusDataSource] });
    return TestBed.inject(MockServiceStatusDataSource);
  }

  it('renvoie les 6 services avec un état et un horodatage', () => {
    const source = make();
    let statuses: ReturnType<typeof Array.prototype.slice> = [];
    source.getStatuses().subscribe((s) => (statuses = s));
    expect(statuses).toHaveLength(Object.keys(SERVICE_LABELS).length);
    for (const s of statuses) {
      expect(['UP', 'DOWN', 'UNKNOWN']).toContain(s.state);
      expect(s.checkedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    }
  });

  it('permet de forcer un DOWN et un UNKNOWN', () => {
    const source = make();
    source.forcedState = { key: 'telemetry', state: 'DOWN' };
    let statuses: ReturnType<typeof Array.prototype.slice> = [];
    source.getStatuses().subscribe((s) => (statuses = s));
    expect(statuses.find((s) => s.key === 'telemetry')?.state).toBe('DOWN');
  });

  it('échoue en mode unavailable', () => {
    const source = make();
    source.mode = 'unavailable';
    let error: unknown = null;
    source.getStatuses().subscribe({ error: (e) => (error = e) });
    expect(error).not.toBeNull();
  });
});