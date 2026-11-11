import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { InterventionApi } from './intervention-api';
import { nextStatus } from './intervention.model';

describe('InterventionApi', () => {
  let api: InterventionApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(InterventionApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it("liste les interventions d'un incident : incidentId en paramètre", () => {
    api.list(7).subscribe();
    const req = http.expectOne((r) => r.url === '/api/assets/interventions' && r.params.get('incidentId') === '7');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('crée : POST avec incidentId', () => {
    api.create(7).subscribe();
    const req = http.expectOne('/api/assets/interventions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ incidentId: 7 });
    req.flush({});
  });

  it('affecte : PUT /assign avec team et assignedTo', () => {
    api.assign(3, { team: 'Equipe A', assignedTo: 'tech1' }).subscribe();
    const req = http.expectOne('/api/assets/interventions/3/assign');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ team: 'Equipe A', assignedTo: 'tech1' });
    req.flush({});
  });

  it('change le statut : PUT /status', () => {
    api.changeStatus(3, 'IN_PROGRESS').subscribe();
    const req = http.expectOne('/api/assets/interventions/3/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'IN_PROGRESS' });
    req.flush({});
  });

  it('la seule suite permise est Planifiée -> En cours -> Terminée', () => {
    expect(nextStatus('PLANNED')).toBe('IN_PROGRESS');
    expect(nextStatus('IN_PROGRESS')).toBe('DONE');
    expect(nextStatus('DONE')).toBeNull();
  });
});