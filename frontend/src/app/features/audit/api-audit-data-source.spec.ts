import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiAuditDataSource } from './api-audit-data-source';

describe('ApiAuditDataSource', () => {
  let source: ApiAuditDataSource;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ApiAuditDataSource, provideHttpClient(), provideHttpClientTesting()],
    });
    source = TestBed.inject(ApiAuditDataSource);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('appelle /api/assets/audit avec la page et la taille', () => {
    source.search({ page: 2, size: 20 }).subscribe();
    const req = http.expectOne((r) => r.url === '/api/assets/audit');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.has('actor')).toBe(false);
    req.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('envoie les filtres renseignés', () => {
    source
      .search({ page: 0, size: 20, actor: 'alice', action: 'DELETE', from: '2026-12-01T00:00:00Z', to: '2026-12-02T00:00:00Z' })
      .subscribe();
    const req = http.expectOne((r) => r.url === '/api/assets/audit');
    expect(req.request.params.get('actor')).toBe('alice');
    expect(req.request.params.get('action')).toBe('DELETE');
    expect(req.request.params.get('from')).toBe('2026-12-01T00:00:00Z');
    expect(req.request.params.get('to')).toBe('2026-12-02T00:00:00Z');
    req.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('laisse passer l\u2019erreur 403 pour que l\u2019écran l\u2019affiche', () => {
    let status = 0;
    source.search({ page: 0, size: 20 }).subscribe({ error: (e) => (status = e.status) });
    http.expectOne((r) => r.url === '/api/assets/audit').flush('refusé', { status: 403, statusText: 'Forbidden' });
    expect(status).toBe(403);
  });

  it('la liste des actions ne casse pas l\u2019écran si elle échoue', () => {
    let result: string[] | null = null;
    source.actions().subscribe((a) => (result = a));
    http.expectOne('/api/assets/audit/actions').flush('erreur', { status: 500, statusText: 'Error' });
    expect(result).toEqual([]);
  });
});
