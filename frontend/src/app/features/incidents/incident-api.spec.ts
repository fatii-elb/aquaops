import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { IncidentApi, errorMessage } from './incident-api';

describe('IncidentApi', () => {
  let api: IncidentApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(IncidentApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('liste sans filtre : GET /api/incidents', () => {
    api.list().subscribe();
    const req = http.expectOne('/api/incidents');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('liste avec filtre : status en paramètre', () => {
    api.list('OPEN').subscribe();
    const req = http.expectOne((r) => r.url === '/api/incidents' && r.params.get('status') === 'OPEN');
    req.flush([]);
  });

  it("lit un incident et son historique", () => {
    api.get(7).subscribe();
    http.expectOne('/api/incidents/7').flush({});
    api.history(7).subscribe();
    http.expectOne('/api/incidents/7/history').flush([]);
  });

  it('confirme : PUT avec status, assignee et actor', () => {
    api.update(7, { status: 'CONFIRMED', assignee: null, actor: 'test-superviseur' }).subscribe();
    const req = http.expectOne('/api/incidents/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'CONFIRMED', assignee: null, actor: 'test-superviseur' });
    req.flush({});
  });

  it('commente : POST /comments avec text et author', () => {
    api.addComment(7, 'RAS', 'test-superviseur').subscribe();
    const req = http.expectOne('/api/incidents/7/comments');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ text: 'RAS', author: 'test-superviseur' });
    req.flush({});
  });

  it("errorMessage lit { message } du serveur, sinon le texte par défaut", () => {
    expect(errorMessage({ error: { message: 'Transition interdite' } }, 'x')).toBe('Transition interdite');
    expect(errorMessage({ error: {} }, 'x')).toBe('x');
    expect(errorMessage(null, 'x')).toBe('x');
  });
});
