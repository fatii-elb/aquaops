import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EVENT_SOURCE_FACTORY, EventSourceLike, NOTIFICATION_STREAM_URL, NotificationStream, parseNotification } from './notification-stream';

class FakeEventSource implements EventSourceLike {
  onopen: ((e: Event) => void) | null = null;
  onerror: ((e: Event) => void) | null = null;
  onmessage: ((e: MessageEvent) => void) | null = null;
  closed = false;
  close() {
    this.closed = true;
  }
  push(data: string) {
    this.onmessage?.({ data } as MessageEvent);
  }
}

describe('NotificationStream', () => {
  let source: FakeEventSource;
  let urls: string[];
  let stream: NotificationStream;

  beforeEach(() => {
    vi.useFakeTimers();
    urls = [];
    source = new FakeEventSource();
    TestBed.configureTestingModule({
      providers: [{ provide: EVENT_SOURCE_FACTORY, useValue: (url: string) => (urls.push(url), source) }],
    });
    stream = TestBed.inject(NotificationStream);
  });
  afterEach(() => vi.useRealTimers());

  it('ouvre /api/notifications/subscribe une seule fois', () => {
    stream.start();
    stream.start();
    expect(urls).toEqual([NOTIFICATION_STREAM_URL]);
    expect(NOTIFICATION_STREAM_URL).toBe('/api/notifications/subscribe');
  });

  it('une notification reçue est affichée tout de suite', () => {
    stream.start();
    source.push('Fuite détectée zone 3');
    expect(stream.toasts().length).toBe(1);
    expect(stream.items()[0].text).toBe('Fuite détectée zone 3');
    expect(stream.tick()).toBe(1);
  });

  it("la bulle disparaît après 8 s et reste dans l'historique", () => {
    stream.start();
    source.push('Test');
    vi.advanceTimersByTime(7999);
    expect(stream.toasts().length).toBe(1);
    vi.advanceTimersByTime(2);
    expect(stream.toasts().length).toBe(0);
    expect(stream.items().length).toBe(1);
  });

  it('garde 4 bulles et 20 notifications au maximum, les plus récentes en premier', () => {
    stream.start();
    for (let i = 1; i <= 25; i++) {
      source.push(`msg ${i}`);
    }
    expect(stream.toasts().length).toBe(4);
    expect(stream.items().length).toBe(20);
    expect(stream.items()[0].text).toBe('msg 25');
  });

  it('dismiss retire la bulle', () => {
    stream.start();
    source.push('a');
    stream.dismiss(stream.toasts()[0].id);
    expect(stream.toasts().length).toBe(0);
  });

  it("suit l'état de la connexion et ferme à l'arrêt", () => {
    stream.start();
    source.onopen?.(new Event('open'));
    expect(stream.connected()).toBe(true);
    source.onerror?.(new Event('error'));
    expect(stream.connected()).toBe(false);
    stream.stop();
    expect(source.closed).toBe(true);
  });
});

describe('parseNotification', () => {
  it('lit un événement incident-events (JSON)', () => {
    const n = parseNotification(
      JSON.stringify({ eventVersion: '1', eventType: 'INCIDENT_CREATED', incidentId: 42, zoneId: 3, status: 'OPEN', priority: 'CRITICAL' }),
    );
    expect(n.text).toBe('Incident #42 : nouvel incident (zone 3)');
    expect(n.severity).toBe('critique');
    expect(n.incidentId).toBe(42);
  });

  it('garde un texte simple tel quel et y repère un numéro d\'incident', () => {
    const n = parseNotification('Incident #7 assigné à test-technicien');
    expect(n.text).toBe('Incident #7 assigné à test-technicien');
    expect(n.incidentId).toBe(7);
    expect(n.severity).toBe('info');
  });

  it('tolère un JSON invalide et un message vide', () => {
    expect(parseNotification('{pas du json').text).toBe('{pas du json');
    expect(parseNotification('').text).toBe('Notification');
  });

  it('reconnaît les messages critiques', () => {
    expect(parseNotification('Fuite probable en zone 3').severity).toBe('critique');
  });
});
