import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthService } from '../../../core/auth.service';
import { EVENT_SOURCE_FACTORY, NotificationStream } from '../../../core/notification-stream';
import { HistoryEntry, Incident } from '../incident.model';
import { FakeEventSource, sampleEntry, sampleIncident } from '../test-helpers';
import { IncidentPanel } from './incident-panel';

describe('IncidentPanel', () => {
  let http: HttpTestingController;
  let source: FakeEventSource;

  function create(data: Incident = sampleIncident, history: HistoryEntry[] = [], id = 7) {
    const fixture = TestBed.createComponent(IncidentPanel);
    fixture.componentRef.setInput('incidentId', id);
    fixture.detectChanges();
    http.expectOne(`/api/incidents/${id}`).flush(data);
    http.expectOne(`/api/incidents/${id}/history`).flush(history);
    fixture.detectChanges();
    return fixture;
  }

  const button = (el: HTMLElement, action: string) => el.querySelector<HTMLButtonElement>(`[data-action="${action}"]`);

  beforeEach(() => {
    source = new FakeEventSource();
    TestBed.configureTestingModule({
      imports: [IncidentPanel],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'test-superviseur', hasAnyRole: () => true } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => source },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it("montre le code, l'état, la priorité, l'origine et la chronologie", () => {
    const fixture = create(sampleIncident, [sampleEntry]);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="code"]')?.textContent).toContain('INC-2026-007');
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Ouvert');
    expect(el.querySelector('[data-testid="priority"]')?.textContent).toContain('Critique');
    expect(el.querySelector('[data-testid="delay"]')?.textContent).toContain('5 s');
    const timeline = el.querySelector('[data-testid="history"]')!.textContent!;
    expect(timeline).toContain('Incident créé');
    expect(timeline).toContain('test-superviseur');
    expect(timeline).toContain('Ouvert → Confirmé');
  });

  it("incident ouvert : propose Confirmer et Clore, pas Affecter", () => {
    const el: HTMLElement = create().nativeElement;
    expect(button(el, 'CONFIRMED')).toBeTruthy();
    expect(button(el, 'CLOSED')).toBeTruthy();
    expect(button(el, 'ASSIGNED')).toBeNull();
  });

  it.each([
    ['OPEN', ['CONFIRMED', 'CLOSED']],
    ['CONFIRMED', ['ASSIGNED', 'CLOSED']],
    ['ASSIGNED', ['IN_PROGRESS', 'CLOSED']],
    ['IN_PROGRESS', ['RESOLVED', 'CLOSED']],
    ['RESOLVED', ['CLOSED']],
    ['CLOSED', []],
  ] as const)('état %s : propose exactement les actions %j (cycle de vie FR-17)', (status, expected) => {
    const el: HTMLElement = create({ ...sampleIncident, status }).nativeElement;
    const shown = Array.from(el.querySelectorAll<HTMLElement>('[data-action]')).map((b) => b.dataset['action']);
    expect([...shown].sort()).toEqual([...expected].sort());
  });

  it("Confirmer envoie PUT {status: CONFIRMED}, met l'écran à jour, recharge l'historique et prévient la liste", () => {
    const fixture = create();
    let changed = 0;
    fixture.componentInstance.changed.subscribe(() => changed++);
    button(fixture.nativeElement, 'CONFIRMED')!.click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/incidents/7');
    expect(put.request.body).toEqual({ status: 'CONFIRMED', assignee: null, actor: 'test-superviseur' });
    put.flush({ ...sampleIncident, status: 'CONFIRMED' });
    http.expectOne('/api/incidents/7/history').flush([sampleEntry]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(changed).toBe(1);
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Confirmé');
    expect(button(el, 'ASSIGNED')).toBeTruthy();
    expect(button(el, 'CONFIRMED')).toBeNull();
  });

  it('Affecter sans nom : aucune requête, message affiché', () => {
    const fixture = create({ ...sampleIncident, status: 'CONFIRMED' });
    button(fixture.nativeElement, 'ASSIGNED')!.click();
    http.expectNone((r) => r.method === 'PUT');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('technicien');
  });

  it('Affecter avec un nom envoie PUT {status: ASSIGNED, assignee}', () => {
    const fixture = create({ ...sampleIncident, status: 'CONFIRMED' });
    const el: HTMLElement = fixture.nativeElement;
    const input = el.querySelector<HTMLInputElement>('#assignee')!;
    input.value = '  test-technicien ';
    input.dispatchEvent(new Event('input'));
    button(el, 'ASSIGNED')!.click();
    const put = http.expectOne((r) => r.method === 'PUT');
    expect(put.request.body).toEqual({ status: 'ASSIGNED', assignee: 'test-technicien', actor: 'test-superviseur' });
    put.flush({ ...sampleIncident, status: 'ASSIGNED', assignee: 'test-technicien' });
    http.expectOne('/api/incidents/7/history').flush([]);
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="assignee"]')?.textContent).toContain('test-technicien');
    expect(el.querySelector('[data-testid="status"]')?.textContent).toContain('Affecté');
    expect(button(el, 'IN_PROGRESS')).toBeTruthy();
  });

  it('une transition refusée par le serveur affiche son message et ne change pas l\'état', () => {
    const fixture = create();
    button(fixture.nativeElement, 'CONFIRMED')!.click();
    http.expectOne((r) => r.method === 'PUT').flush({ message: 'Transition interdite : OPEN -> CONFIRMED' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('Transition interdite');
    expect(fixture.nativeElement.querySelector('[data-testid="status"]')?.textContent).toContain('Ouvert');
  });

  it("un commentaire est envoyé avec le nom de l'utilisateur, apparaît dans le journal, puis le champ est vidé", () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    const field = el.querySelector<HTMLInputElement>('#comment')!;
    field.value = 'Équipe prévenue';
    field.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('[data-testid="send-comment"]')!.click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === '/api/incidents/7/comments');
    expect(post.request.body).toEqual({ text: 'Équipe prévenue', author: 'test-superviseur' });
    const saved: HistoryEntry = { ...sampleEntry, id: 2, action: 'COMMENTED', fromStatus: null, toStatus: null, comment: 'Équipe prévenue' };
    post.flush(saved);
    http.expectOne('/api/incidents/7/history').flush([saved]);
    fixture.detectChanges();
    expect(field.value).toBe('');
    expect(el.querySelector('[data-testid="notes"]')?.textContent).toContain('Équipe prévenue');
    expect(el.querySelector('[data-testid="history"]')?.textContent).not.toContain('Équipe prévenue');
  });

  it('incident clos : aucune action, pas de champ de commentaire', () => {
    const el: HTMLElement = create({ ...sampleIncident, status: 'CLOSED' }).nativeElement;
    expect(el.textContent).toContain('aucune action possible');
    expect(el.querySelector('#comment')).toBeNull();
  });

  it('incident inconnu (404) : message clair', () => {
    const fixture = TestBed.createComponent(IncidentPanel);
    fixture.componentRef.setInput('incidentId', 7);
    fixture.detectChanges();
    http.expectOne('/api/incidents/7').flush({ message: 'introuvable' }, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/incidents/7/history').flush({}, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain("n'existe pas");
  });

  it("change d'incident : recharge le nouvel incident et ignore la réponse de l'ancien", () => {
    const fixture = create();
    fixture.componentRef.setInput('incidentId', 8);
    fixture.detectChanges();
    http.expectOne('/api/incidents/8').flush({ ...sampleIncident, id: 8 });
    http.expectOne('/api/incidents/8/history').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="code"]')?.textContent).toContain('INC-2026-008');
  });

  it('une notification recharge l\'incident', () => {
    const fixture = create();
    TestBed.inject(NotificationStream).start();
    source.push('Incident #7 mis à jour');
    fixture.detectChanges();
    http.expectOne('/api/incidents/7').flush({ ...sampleIncident, status: 'CONFIRMED' });
    http.expectOne('/api/incidents/7/history').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="status"]')?.textContent).toContain('Confirmé');
  });
});
