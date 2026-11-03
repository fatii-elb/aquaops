import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ZoneDataSource } from '../data/zone-data-source';
import { ZoneSummary } from '../data/zone.model';
import { ZoneCards } from './zone-cards';

const zone = (id: number, state: ZoneSummary['state']): ZoneSummary => ({
  id,
  name: `Zone ${id}`,
  pressureBar: 2.5,
  flowM3h: 30,
  levelM: 3,
  pumpState: 'NORMAL',
  state,
  updatedAt: '2026-11-02T10:00:00Z',
});

class StubSource extends ZoneDataSource {
  calls = 0;
  fail = false;
  getZones(): Observable<ZoneSummary[]> {
    this.calls++;
    return this.fail ? throwError(() => new Error('API down')) : of([zone(1, 'normal'), zone(2, 'critique')]);
  }
}

describe('ZoneCards', () => {
  let source: StubSource;

  beforeEach(() => {
    vi.useFakeTimers();
    source = new StubSource();
    TestBed.configureTestingModule({
      providers: [{ provide: ZoneDataSource, useValue: source }],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  function create() {
    const fixture = TestBed.createComponent(ZoneCards);
    fixture.detectChanges();
    vi.advanceTimersByTime(0);
    fixture.detectChanges();
    return fixture;
  }

  it('affiche une carte par zone avec son état', () => {
    const el: HTMLElement = create().nativeElement;
    const cards = el.querySelectorAll('article');
    expect(cards).toHaveLength(2);
    expect(cards[1].getAttribute('data-state')).toBe('critique');
    expect(cards[1].textContent).toContain('Critique');
  });

  it('rafraîchit toutes les 5 secondes', () => {
    create();
    expect(source.calls).toBe(1);
    vi.advanceTimersByTime(5000);
    expect(source.calls).toBe(2);
    vi.advanceTimersByTime(10000);
    expect(source.calls).toBe(4);
  });

  it('affiche un message quand la source est en panne, puis continue', () => {
    source.fail = true;
    const fixture = create();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Données indisponibles');
    source.fail = false;
    vi.advanceTimersByTime(5000);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).not.toContain('Données indisponibles');
    expect(el.querySelectorAll('article')).toHaveLength(2);
  });
});
