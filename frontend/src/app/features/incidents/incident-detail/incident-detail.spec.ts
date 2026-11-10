import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { EVENT_SOURCE_FACTORY, EventSourceLike } from '../../../core/notification-stream';
import { HistoryEntry, Incident } from '../incident.model';
import { IncidentDetail } from './incident-detail';

class FakeEventSource implements EventSourceLike {
  onopen = null;
  onerror = null;
  onmessage = null;
  close() {}
}

const incident: Incident = {
  id: 7,
  anomalyId: null,
  zoneId: 3,
  sensorId: null,
  type: 'LEAK_SUSPECTED',
  status: 'OPEN',
  priority: 'CRITICAL',
  description: 'Chute de pression',
  assignee: null,
  measuredAt: null,
  createdAt: '2026-11-09T10:00:00Z',
  updatedAt: '2026-11-09T10:00:00Z',
};

const entry: HistoryEntry = {
  id: 1,
  incidentId: 7,
  action: 'CONFIRMED',
  fromStatus: 'OPEN',
  toStatus: 'CONFIRMED',
  author: 'test-superviseur',
  comment: null,
  occurredAt: '2026-11-09T10:05:00Z',
};

describe('IncidentDetail', () => {
  let http: HttpTestingController;

  function create(data: Incident = incident, history: HistoryEntry[] = []) {
    const fixture = TestBed.createComponent(IncidentDetail);
    fixture.detectChanges();
    http.expectOne('/api/incidents/7').flush(data);
    http.expectOne('/api/incidents/7/history').flush(history);
    fixture.detectChanges();
    return fixture;
  }

  const button = (el: HTMLElement, action: string) => el.querySelector<HTMLButtonElement>(`[data-action="${action}"]`);

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [IncidentDetail],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'test-superviseur' } },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => new FakeEventSource() },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it("montre l'incident, son état, sa priorité et son historique", () => {
    const fixture = create(incident, [entry]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Incident #7');
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Ouvert');
    expect(el.querySelector('[data-testid="priority"]')?.textContent).toContain('Critique');
    expect(el.querySelector('[data-testid="history"]')?.textContent).toContain('test-superviseur');
    expect(el.querySelector('[data-testid="history"]')?.textContent).toContain('Ouvert → Confirmé');
  });

  it("incident ouvert : propose Confirmer et Clore, pas Assigner", () => {
    const el: HTMLElement = create().nativeElement;
    expect(button(el, 'CONFIRMED')).toBeTruthy();
    expect(button(el, 'CLOSED')).toBeTruthy();
    expect(button(el, 'ASSIGNED')).toBeNull();
  });

  it("Confirmer envoie PUT {status: CONFIRMED}, met l'écran à jour et recharge l'historique", () => {
    const fixture = create();
    button(fixture.nativeElement, 'CONFIRMED')!.click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/incidents/7');
    expect(put.request.body).toEqual({ status: 'CONFIRMED', assignee: null, actor: 'test-superviseur' });
    put.flush({ ...incident, status: 'CONFIRMED' });
    http.expectOne('/api/incidents/7/history').flush([entry]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Confirmé');
    expect(button(el, 'ASSIGNED')).toBeTruthy();
    expect(button(el, 'CONFIRMED')).toBeNull();
  });

  it('Assigner sans nom : aucune requête, message affiché', () => {
    const fixture = create({ ...incident, status: 'CONFIRMED' });
    button(fixture.nativeElement, 'ASSIGNED')!.click();
    http.expectNone((r) => r.method === 'PUT');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('technicien');
  });

  it('Assigner avec un nom envoie PUT {status: ASSIGNED, assignee}', () => {
    const fixture = create({ ...incident, status: 'CONFIRMED' });
    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector<HTMLInputElement>('#assignee')!;
    input.value = '  test-technicien ';
    input.dispatchEvent(new Event('input'));
    button(el, 'ASSIGNED')!.click();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body).toEqual({ status: 'ASSIGNED', assignee: 'test-technicien', actor: 'test-superviseur' });
    put.flush({ ...incident, status: 'ASSIGNED', assignee: 'test-technicien' });
    http.expectOne('/api/incidents/7/history').flush([]);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="assignee"]')?.textContent).toContain('test-technicien');
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Assigné');
  });

  it('une transition refusée par le serveur affiche son message', () => {
    const fixture = create();
    button(fixture.nativeElement, 'CONFIRMED')!.click();
    http.expectOne((r) => r.method === 'PUT').flush({ message: 'Transition interdite : OPEN -> CONFIRMED' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Transition interdite');
    expect(fixture.nativeElement.querySelector('[data-testid="status"]')?.textContent).toContain('Ouvert');
  });

  it('un commentaire est envoyé avec le nom de l\'utilisateur, puis le champ est vidé', () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    const area = el.querySelector<HTMLTextAreaElement>('#comment')!;
    area.value = 'Équipe prévenue';
    area.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('[data-testid="send-comment"]')!.click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === '/api/incidents/7/comments');
    expect(post.request.body).toEqual({ text: 'Équipe prévenue', author: 'test-superviseur' });
    post.flush({ ...entry, action: 'COMMENTED', comment: 'Équipe prévenue' });
    http.expectOne('/api/incidents/7/history').flush([]);
    fixture.detectChanges();
    expect(area.value).toBe('');
  });

  it("incident clos : aucune action, pas de formulaire de commentaire", () => {
    const fixture = create({ ...incident, status: 'CLOSED' });
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('aucune action possible');
    expect(el.querySelector('#comment')).toBeNull();
  });

  it("incident inconnu (404) : message clair", () => {
    const fixture = TestBed.createComponent(IncidentDetail);
    fixture.detectChanges();
    http.expectOne('/api/incidents/7').flush({ message: 'Incident 7 introuvable' }, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/incidents/7/history').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("n'existe pas");
  });
});
