import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EVENT_SOURCE_FACTORY, EventSourceLike } from '../../../core/notification-stream';
import { Incident } from '../incident.model';
import { IncidentList } from './incident-list';

class FakeEventSource implements EventSourceLike {
  onopen = null;
  onerror = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  close() {}
}

const base: Incident = {
  id: 1,
  anomalyId: null,
  zoneId: 3,
  sensorId: null,
  type: 'LEAK_SUSPECTED',
  status: 'OPEN',
  priority: 'CRITICAL',
  description: null,
  assignee: null,
  measuredAt: null,
  createdAt: '2026-11-09T10:00:00Z',
  updatedAt: '2026-11-09T10:00:00Z',
};

describe('IncidentList', () => {
  let http: HttpTestingController;
  let source: FakeEventSource;

  function create() {
    const fixture = TestBed.createComponent(IncidentList);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => {
    source = new FakeEventSource();
    TestBed.configureTestingModule({
      imports: [IncidentList],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: EVENT_SOURCE_FACTORY, useValue: () => source },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  it('affiche état, priorité et zone de chaque incident, le plus critique en premier', async () => {
    const fixture = create();
    http.expectOne('/api/incidents').flush([
      { ...base, id: 1, priority: 'LOW', status: 'CONFIRMED', zoneId: 5, type: 'LOW_LEVEL' },
      { ...base, id: 2, priority: 'CRITICAL', status: 'OPEN', zoneId: 3 },
    ]);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const rows = Array.from(el.querySelectorAll('tbody tr'));
    expect(rows.length).toBe(2);
    expect(rows[0].getAttribute('data-incident')).toBe('2');
    expect(rows[0].textContent).toContain('Zone 3');
    expect(rows[0].querySelector('[data-col="priority"]')?.textContent).toContain('Critique');
    expect(rows[0].querySelector('[data-col="status"]')?.textContent).toContain('Ouvert');
    expect(rows[1].querySelector('[data-col="status"]')?.textContent).toContain('Confirmé');
    expect(el.querySelector('[data-testid="critical-count"]')?.textContent).toContain('1');
    expect(el.querySelector('[data-testid="active-count"]')?.textContent).toContain('2');
    expect(rows[0].querySelector('a')?.getAttribute('href')).toBe('/incidents/2');
  });

  it('le filtre par état relance la requête avec status', () => {
    const fixture = create();
    http.expectOne('/api/incidents').flush([]);
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#status-filter');
    select.value = 'OPEN';
    select.dispatchEvent(new Event('change'));
    http.expectOne((r) => r.url === '/api/incidents' && r.params.get('status') === 'OPEN').flush([]);
  });

  it('affiche un message quand le service est indisponible', () => {
    const fixture = create();
    http.expectOne('/api/incidents').flush('boom', { status: 502, statusText: 'Bad Gateway' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain('indisponibles');
  });

  it('recharge la liste dès qu\'une notification arrive', async () => {
    const fixture = create();
    http.expectOne('/api/incidents').flush([]);
    fixture.detectChanges();
    source.onmessage?.({ data: 'Incident #9 créé' } as MessageEvent);
    fixture.detectChanges();
    await fixture.whenStable();
    http.expectOne('/api/incidents').flush([{ ...base, id: 9 }]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });
});
