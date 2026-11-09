import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { AuditDataSource } from './audit-data-source';
import { AuditEntry, AuditPage, AuditQuery } from './audit.model';

const ACTORS: Array<[string, string]> = [
  ['test-admin', 'administrateur'],
  ['test-superviseur', 'superviseur'],
  ['test-technicien', 'technicien'],
];

/** 57 lignes, une toutes les 17 minutes en remontant depuis le 2 décembre 2026, 12 h (UTC). */
function buildEntries(): AuditEntry[] {
  const end = Date.UTC(2026, 11, 2, 12, 0, 0);
  const kinds: Array<Pick<AuditEntry, 'action' | 'entityType' | 'details'>> = [
    { action: 'CREATE', entityType: 'ZONE', details: 'nom : Zone Nord' },
    { action: 'UPDATE', entityType: 'SENSOR', details: 'nom : P-12, type PRESSURE, zone 3, actif PIPE 7' },
    { action: 'DELETE', entityType: 'PUMP', details: 'nom : Pompe 2, zone 1' },
    { action: 'INCIDENT_CONFIRMED', entityType: 'INCIDENT', details: 'zone 3, statut CONFIRMED, priorite CRITICAL' },
    { action: 'ASSIGN', entityType: 'INTERVENTION', details: 'equipe A, technicien test-technicien' },
    { action: 'STATUS_CHANGE', entityType: 'INTERVENTION', details: 'PLANNED -> IN_PROGRESS' },
    { action: 'INCIDENT_CLOSED', entityType: 'INCIDENT', details: 'zone 3, statut CLOSED' },
  ];
  return Array.from({ length: 57 }, (_, i) => {
    const kind = kinds[i % kinds.length];
    const [actor, actorRole] = kind.entityType === 'INCIDENT' ? ['system', ''] : ACTORS[i % ACTORS.length];
    return {
      id: 57 - i,
      occurredAt: new Date(end - i * 17 * 60_000).toISOString(),
      actor,
      actorRole: actorRole || null,
      action: kind.action,
      entityType: kind.entityType,
      entityId: String(1 + (i % 9)),
      details: kind.details,
      source: kind.entityType === 'INCIDENT' ? 'incident-events' : 'assets-service',
    };
  });
}

/** Données simulées, mêmes règles de filtre et de pagination que l'API. */
@Injectable()
export class MockAuditDataSource extends AuditDataSource {
  private entries = buildEntries(); // déjà triées, les plus récentes d'abord

  search(query: AuditQuery): Observable<AuditPage> {
    const actor = query.actor?.trim().toLowerCase();
    const from = query.from ? Date.parse(query.from) : null;
    const to = query.to ? Date.parse(query.to) : null;
    const matching = this.entries.filter((e) => {
      const at = Date.parse(e.occurredAt);
      return (
        (!actor || e.actor.toLowerCase().includes(actor)) &&
        (!query.action || e.action === query.action) &&
        (from === null || at >= from) &&
        (to === null || at <= to)
      );
    });
    const start = query.page * query.size;
    return of({
      content: matching.slice(start, start + query.size),
      page: query.page,
      size: query.size,
      totalElements: matching.length,
      totalPages: Math.ceil(matching.length / query.size),
    });
  }

  actions(): Observable<string[]> {
    return of([...new Set(this.entries.map((e) => e.action))].sort());
  }
}
