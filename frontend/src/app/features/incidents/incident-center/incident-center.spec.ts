import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { EVENT_SOURCE_FACTORY, NotificationStream } from '../../../core/notification-stream';
import { Incident } from '../incident.model';
import { FakeEventSource, sampleIncident } from '../test-helpers';
import { IncidentCenter } from './incident-center';

const incidents: Incident[] = [
  { ...sampleIncident, id: 1, priority: 'LOW', status: 'CONFIRMED', zoneId: 5, type: 'LOW_LEVEL', description: null, createdAt: '2026-11-09T09:00:00Z' },
  { ...sampleIncident, id: 2, priority: 'CRITICAL', status: 'OPEN', zoneId: 3 },
  { ...sampleIncident, id: 3, priority: 'HIGH', status: 'IN_PROGRESS', zoneId: 3, type: 'PUMP_FAULT', assignee: 'tech1', description: null },
];

describe('IncidentCenter', () => {
  let http: HttpTestingController;
  let source: FakeEventSource;

  let params: Record<string, string> = {};

  function create(routeParams: Record<string, string> = {}) {
    params = routeParams;
    const fixture = TestBed.createComponent(IncidentCenter);
    fixture.detectChanges();
    http.expectOne('/api/incidents').flush(incidents);
    fixture.detectChanges();
    return fixture;
  }

  /** Les requêtes de l'incident choisi (détail + historique). */
  function flushPanel(id: number, status: Incident['status'] = 'OPEN') {
    http.expectOne(`/api/incidents/${id}`).flush({ ...incidents.find((i) => i.id === id)!, status });
    http.expectOne(`/api/incidents/${id}/history`).flush([]);
  }

  const ids = (el: HTMLElement) => Array.from(el.querySelectorAll('[data-testid="incident-list"] [data-incident]')).map((n) => n.getAttribute('data-incident'));

  beforeEach(() => {
    params = {};
    source = new FakeEventSource();
    TestBed.configureTestingModule({
      imports: [IncidentCenter],
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            get snapshot() {
              return { paramMap: convertToParamMap(params) };
            },
            get paramMap() {
              return of(convertToParamMap(params));
            },
          },
        },
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { username: 'test-superviseur', logout() {} } },
        { provide: EVENT_SOURCE_FACTORY, useValue: () => source },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('liste les incidents, le plus critique en premier, avec code, priorité, état et zone', () => {
    const fixture = create();
    const el: HTMLElement = fixture.nativeElement;
    http.match('/api/assets/zones').forEach((r) => r.flush([{ id: 3, name: 'Gueliz' }]));
    flushPanel(2);
    fixture.detectChanges();
    expect(ids(el)).toEqual(['2', '3', '1']);
    const first = el.querySelector('[data-incident="2"]')!;
    expect(first.querySelector('[data-col="priority"]')?.textContent).toContain('Critique');
    expect(first.querySelector('[data-col="status"]')?.textContent).toContain('Ouvert');
    expect(first.querySelector('[data-col="code"]')?.textContent).toContain('#INC-2026-002');
    expect(first.textContent).toContain('Gueliz');
    expect(el.querySelector('[data-incident="1"]')?.textContent).toContain('Zone 5');
    expect(el.querySelector('[data-incident="1"]')?.textContent).toContain("En attente d'affectation");
  });

  it("choisit tout seul le premier incident et affiche son panneau", () => {
    const fixture = create();
    flushPanel(2);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-incident-panel [data-testid="code"]')?.textContent).toContain('INC-2026-002');
  });

  it("la barre de pipeline compte chaque étape et l'incident actif est compté", () => {
    const fixture = create();
    flushPanel(2);
    const el: HTMLElement = fixture.nativeElement;
    const count = (status: string) => el.querySelector(`[data-stage="${status}"] [data-testid="stage-count"]`)?.textContent?.trim();
    expect(count('OPEN')).toBe('1');
    expect(count('CONFIRMED')).toBe('1');
    expect(count('IN_PROGRESS')).toBe('1');
    expect(count('CLOSED')).toBe('0');
    expect(el.querySelector('[data-testid="active-count"]')?.textContent).toContain('3');
  });

  it("cliquer une étape filtre la liste, cliquer encore retire le filtre", () => {
    const fixture = create();
    flushPanel(2);
    const el: HTMLElement = fixture.nativeElement;
    el.querySelector<HTMLButtonElement>('[data-stage="IN_PROGRESS"]')!.click();
    fixture.detectChanges();
    expect(ids(el)).toEqual(['3']);
    el.querySelector<HTMLButtonElement>('[data-stage="IN_PROGRESS"]')!.click();
    fixture.detectChanges();
    expect(ids(el).length).toBe(3);
  });

  it('les pastilles de priorité filtrent et affichent leur nombre', () => {
    const fixture = create();
    flushPanel(2);
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-priority="CRITICAL"]')?.textContent).toContain('Critique (1)');
    expect(el.querySelector('[data-priority="ALL"]')?.textContent).toContain('Tous (3)');
    el.querySelector<HTMLButtonElement>('[data-priority="LOW"]')!.click();
    fixture.detectChanges();
    expect(ids(el)).toEqual(['1']);
  });

  it('la recherche filtre par code, type ou technicien', () => {
    const fixture = create();
    flushPanel(2);
    const el: HTMLElement = fixture.nativeElement;
    const box = el.querySelector<HTMLInputElement>('#incident-search')!;
    box.value = 'tech1';
    box.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(ids(el)).toEqual(['3']);
    box.value = 'inc-2026-001';
    box.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(ids(el)).toEqual(['1']);
    box.value = 'zzz';
    box.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el.textContent).toContain('Aucun incident pour ce filtre.');
  });

  it('cliquer une carte ouvre cet incident à droite', () => {
    const fixture = create();
    flushPanel(2);
    fixture.nativeElement.querySelector('[data-incident="3"]').click();
    fixture.detectChanges();
    flushPanel(3, 'IN_PROGRESS');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-incident-panel [data-testid="code"]')?.textContent).toContain('INC-2026-003');
    expect(fixture.nativeElement.querySelector('[data-incident="3"]')?.getAttribute('aria-current')).toBe('true');
  });

  it("l'adresse /incidents/:id choisit l'incident demandé", () => {
    const fixture = create({ id: '1' });
    flushPanel(1, 'CONFIRMED');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-incident-panel [data-testid="code"]')?.textContent).toContain('INC-2026-001');
  });

  it('une action dans le panneau recharge la liste', () => {
    const fixture = create();
    flushPanel(2);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-action="CONFIRMED"]').click();
    http.expectOne((r) => r.method === 'PUT').flush({ ...incidents[1], status: 'CONFIRMED' });
    http.expectOne('/api/incidents/2/history').flush([]);
    http.expectOne('/api/incidents').flush(incidents.map((i) => (i.id === 2 ? { ...i, status: 'CONFIRMED' as const } : i)));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-incident="2"] [data-col="status"]')?.textContent).toContain('Confirmé');
  });

  it("affiche un message quand le service est indisponible", () => {
    const fixture = TestBed.createComponent(IncidentCenter);
    fixture.detectChanges();
    http.expectOne('/api/incidents').flush('boom', { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('indisponibles');
  });

  it("recharge la liste dès qu'une notification arrive", async () => {
    const fixture = create();
    flushPanel(2);
    fixture.detectChanges();
    source.push('Incident #9 créé');
    fixture.detectChanges();
    await fixture.whenStable();
    http.match('/api/incidents').forEach((r) => r.flush([...incidents, { ...sampleIncident, id: 9 }]));
    http.match((r) => r.url.startsWith('/api/incidents/')).forEach((r) => r.flush([]));
    fixture.detectChanges();
    expect(ids(fixture.nativeElement).length).toBe(4);
  });

  it("« Déclarer un incident manuel » ouvre la fenêtre, envoie le POST et sélectionne le nouvel incident", () => {
    const fixture = create();
    flushPanel(2);
    const el: HTMLElement = fixture.nativeElement;
    el.querySelector<HTMLButtonElement>('[data-testid="open-create"]')!.click();
    fixture.detectChanges();
    const zone = el.querySelector<HTMLInputElement>('#new-zone')!;
    zone.value = '4';
    zone.dispatchEvent(new Event('input'));
    const type = el.querySelector<HTMLSelectElement>('#new-type')!;
    type.value = 'PUMP_FAULT';
    type.dispatchEvent(new Event('change'));
    const priority = el.querySelector<HTMLSelectElement>('#new-priority')!;
    priority.value = 'HIGH';
    priority.dispatchEvent(new Event('change'));
    const desc = el.querySelector<HTMLTextAreaElement>('#new-description')!;
    desc.value = 'Bruit anormal';
    desc.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('[data-testid="submit-create"]')!.click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url === '/api/incidents');
    expect(post.request.body).toEqual({ zoneId: 4, type: 'PUMP_FAULT', priority: 'HIGH', description: 'Bruit anormal', actor: 'test-superviseur' });
    post.flush({ ...sampleIncident, id: 11, zoneId: 4, type: 'PUMP_FAULT', priority: 'HIGH' });
    http.expectOne('/api/incidents').flush([...incidents, { ...sampleIncident, id: 11, zoneId: 4, priority: 'HIGH', type: 'PUMP_FAULT' }]);
    fixture.detectChanges();
    http.expectOne('/api/incidents/11').flush({ ...sampleIncident, id: 11 });
    http.expectOne('/api/incidents/11/history').flush([]);
    fixture.detectChanges();
    expect(el.querySelector('#new-zone')).toBeNull();
    expect(el.querySelector('app-incident-panel [data-testid="code"]')?.textContent).toContain('INC-2026-011');
  });

  it('la création sans zone affiche un message et ne part pas', () => {
    const fixture = create();
    flushPanel(2);
    const el: HTMLElement = fixture.nativeElement;
    el.querySelector<HTMLButtonElement>('[data-testid="open-create"]')!.click();
    fixture.detectChanges();
    el.querySelector<HTMLButtonElement>('[data-testid="submit-create"]')!.click();
    http.expectNone((r) => r.method === 'POST');
    fixture.detectChanges();
    expect(el.querySelector('[role="dialog"] [role="alert"]')?.textContent).toContain('zone');
    el.querySelector<HTMLButtonElement>('[data-testid="cancel-create"]')!.click();
    fixture.detectChanges();
    expect(el.querySelector('[role="dialog"][aria-modal]')).toBeNull();
  });

  it("l'état du temps réel s'affiche dans la barre du haut et la cloche compte les notifications", () => {
    const fixture = create();
    flushPanel(2);
    TestBed.inject(NotificationStream).start();
    source.onopen?.(new Event('open'));
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('[data-testid="live-state"]')?.textContent).toContain('Temps réel connecté');
    source.push('Fuite en zone 3');
    fixture.detectChanges();
    expect(el.querySelector('[data-testid="bell-count"]')?.textContent).toContain('1');
    http.match(() => true);
  });
});
