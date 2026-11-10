import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { AuditDataSource } from './audit-data-source';
import { Audit } from './audit';
import { AuditEntry, AuditPage, AuditQuery } from './audit.model';

const entry = (id: number): AuditEntry => ({
  id,
  occurredAt: '2026-12-02T10:00:00Z',
  actor: `user-${id}`,
  actorRole: 'superviseur',
  action: 'CREATE',
  entityType: 'ZONE',
  entityId: String(id),
  details: `nom : Zone ${id}`,
  source: 'assets-service',
});

class StubSource extends AuditDataSource {
  queries: AuditQuery[] = [];
  failWith: HttpErrorResponse | null = null;
  total = 45; // 3 pages de 20

  search(query: AuditQuery): Observable<AuditPage> {
    this.queries.push(query);
    if (this.failWith) return throwError(() => this.failWith);
    const count = Math.min(query.size, this.total - query.page * query.size);
    return of({
      content: Array.from({ length: Math.max(count, 0) }, (_, i) => entry(query.page * query.size + i + 1)),
      page: query.page,
      size: query.size,
      totalElements: this.total,
      totalPages: Math.ceil(this.total / query.size),
    });
  }

  actions(): Observable<string[]> {
    return of(['CREATE', 'DELETE']);
  }
}

async function render(source: StubSource) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      { provide: AuditDataSource, useValue: source },
      { provide: AuthService, useValue: { username: 'sup', logout: () => undefined } },
    ],
  });
  const fixture = TestBed.createComponent(Audit);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  const root = fixture.nativeElement as HTMLElement;
  const click = async (testId: string) => {
    (root.querySelector(`[data-testid="${testId}"]`) as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
  };
  return { fixture, root, click };
}

describe('Audit', () => {
  it('affiche la première page : 20 lignes et le résumé', async () => {
    const source = new StubSource();
    const { root } = await render(source);
    expect(root.querySelectorAll('[data-testid="row"]')).toHaveLength(20);
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toContain('Page 1 sur 3');
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toContain('45 entrées');
    expect(source.queries[0]).toMatchObject({ page: 0, size: 20 });
  });

  it('propose les types d\u2019action de l\u2019API dans le filtre', async () => {
    const { root } = await render(new StubSource());
    const options = Array.from(root.querySelectorAll('[data-testid="filter-action"] option')).map((o) => o.textContent?.trim());
    expect(options).toEqual(['Toutes', 'Création', 'Suppression']);
  });

  it('Suivant et Précédent changent de page', async () => {
    const source = new StubSource();
    const { root, click } = await render(source);
    await click('next');
    expect(source.queries.at(-1)?.page).toBe(1);
    expect(root.querySelector('[data-testid="summary"]')?.textContent).toContain('Page 2 sur 3');
    await click('previous');
    expect(source.queries.at(-1)?.page).toBe(0);
  });

  it('Précédent est désactivé sur la première page', async () => {
    const { root } = await render(new StubSource());
    expect((root.querySelector('[data-testid="previous"]') as HTMLButtonElement).disabled).toBe(true);
    expect((root.querySelector('[data-testid="next"]') as HTMLButtonElement).disabled).toBe(false);
  });

  it('Filtrer envoie les filtres saisis et revient à la page 1', async () => {
    const source = new StubSource();
    const { fixture, click } = await render(source);
    const audit = fixture.componentInstance;
    audit.actor = '  alice ';
    audit.action = 'DELETE';
    audit.from = '2026-12-01';
    audit.to = '2026-12-02';
    await click('apply');
    const query = source.queries.at(-1) as AuditQuery;
    expect(query.page).toBe(0);
    expect(query.actor).toBe('alice');
    expect(query.action).toBe('DELETE');
    expect(query.from).toBeDefined();
    expect(query.to).toBeDefined();
  });

  it('refuse une période dont le début suit la fin, sans appeler l\u2019API', async () => {
    const source = new StubSource();
    const { fixture, root, click } = await render(source);
    const calls = source.queries.length;
    fixture.componentInstance.from = '2026-12-05';
    fixture.componentInstance.to = '2026-12-01';
    await click('apply');
    expect(source.queries).toHaveLength(calls);
    expect(root.querySelector('[data-testid="error"]')?.textContent).toContain('date de début');
  });

  it('Réinitialiser efface les filtres', async () => {
    const source = new StubSource();
    const { fixture, click } = await render(source);
    fixture.componentInstance.actor = 'alice';
    await click('reset');
    expect(fixture.componentInstance.actor).toBe('');
    expect(source.queries.at(-1)?.actor).toBeUndefined();
  });

  it('403 : affiche un message clair et aucune ligne', async () => {
    const source = new StubSource();
    source.failWith = new HttpErrorResponse({ status: 403 });
    const { root } = await render(source);
    expect(root.querySelector('[data-testid="error"]')?.textContent).toContain('Accès refusé (403)');
    expect(root.querySelectorAll('[data-testid="row"]')).toHaveLength(0);
  });

  it('journal vide : message dédié', async () => {
    const source = new StubSource();
    source.total = 0;
    const { root } = await render(source);
    expect(root.querySelector('[data-testid="empty"]')).not.toBeNull();
  });
});
