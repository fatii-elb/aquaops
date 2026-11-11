import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../core/auth.service';
import { InterventionList } from './intervention-list';

const planned = { id: 3, incidentId: 7, team: null, assignedTo: null, status: 'PLANNED', createdAt: '2026-11-11T10:00:00Z' };

describe('InterventionList', () => {
  let http: HttpTestingController;

  function create(canManage: boolean, closed = false) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'tech1', hasAnyRole: () => canManage } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(InterventionList);
    fixture.componentRef.setInput('incidentId', 7);
    fixture.componentRef.setInput('incidentClosed', closed);
    fixture.detectChanges();
    http.expectOne((r) => r.url === '/api/assets/interventions' && r.params.get('incidentId') === '7').flush([planned]);
    fixture.detectChanges();
    return fixture;
  }

  it('superviseur : voit Créer et Affecter', () => {
    const el: HTMLElement = create(true).nativeElement;
    expect(el.querySelector('[data-testid="create-intervention"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="assign-intervention"]')).not.toBeNull();
    expect(el.querySelector('[data-testid="intervention-status"]')?.textContent).toContain('Planifiée');
  });

  it('autre utilisateur : ne voit ni Créer ni Affecter, mais voit Démarrer', () => {
    const el: HTMLElement = create(false).nativeElement;
    expect(el.querySelector('[data-testid="create-intervention"]')).toBeNull();
    expect(el.querySelector('[data-testid="assign-intervention"]')).toBeNull();
    expect(el.querySelector('[data-testid="advance-intervention"]')).not.toBeNull();
  });

  it('incident clos : pas de bouton Créer', () => {
    const el: HTMLElement = create(true, true).nativeElement;
    expect(el.querySelector('[data-testid="create-intervention"]')).toBeNull();
  });

  it('Démarrer envoie PUT IN_PROGRESS ; un 403 affiche le message de refus', () => {
    const fixture = create(false);
    const el: HTMLElement = fixture.nativeElement;
    (el.querySelector('[data-testid="advance-intervention"]') as HTMLButtonElement).click();
    const req = http.expectOne('/api/assets/interventions/3/status');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'IN_PROGRESS' });
    req.flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="intervention-error"]')?.textContent).toContain('Action refusée');
  });
});