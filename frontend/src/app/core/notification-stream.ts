import { Injectable, InjectionToken, inject, signal } from '@angular/core';

/** Le minimum dont on a besoin d'un EventSource (permet de le remplacer dans les tests). */
export interface EventSourceLike {
  onopen: ((event: Event) => void) | null;
  onerror: ((event: Event) => void) | null;
  onmessage: ((event: MessageEvent) => void) | null;
  close(): void;
}

export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSourceLike>('EVENT_SOURCE_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url),
});

/** Flux temps réel du service des notifications (SSE), via la passerelle. */
export const NOTIFICATION_STREAM_URL = '/api/notifications/subscribe';

export type NotificationSeverity = 'info' | 'alerte' | 'critique';

export interface AppNotification {
  id: number;
  text: string;
  severity: NotificationSeverity;
  /** Renseigné quand le message annonce un incident précis. */
  incidentId: number | null;
  receivedAt: string;
}

const EVENT_TEXT: Record<string, string> = {
  INCIDENT_CREATED: 'nouvel incident',
  INCIDENT_CONFIRMED: 'incident confirmé',
  INCIDENT_ASSIGNED: 'incident assigné',
  INCIDENT_STATUS_CHANGED: "changement d'état",
  INCIDENT_RESOLVED: 'incident résolu',
  INCIDENT_CLOSED: 'incident clos',
};

/**
 * Transforme le texte reçu en notification affichable. Le service des notifications envoie du texte ;
 * si c'est un événement JSON (contrat incident-events.v1) on en tire une phrase, sinon on garde le texte tel quel.
 */
export function parseNotification(raw: string): Pick<AppNotification, 'text' | 'severity' | 'incidentId'> {
  const text = String(raw ?? '').trim();
  if (text.startsWith('{')) {
    try {
      const event = JSON.parse(text) as Record<string, unknown>;
      if (typeof event['incidentId'] === 'number') {
        const kind = EVENT_TEXT[String(event['eventType'])] ?? 'mise à jour';
        const zone = event['zoneId'] !== undefined ? ` (zone ${String(event['zoneId'])})` : '';
        const priority = String(event['priority'] ?? '');
        return {
          text: `Incident #${event['incidentId']} : ${kind}${zone}`,
          severity: priority === 'CRITICAL' ? 'critique' : priority === 'HIGH' ? 'alerte' : 'info',
          incidentId: event['incidentId'],
        };
      }
    } catch {
      // pas du JSON valide : on traite comme du texte
    }
  }
  const idMatch = /incident\s*#?\s*(\d+)/i.exec(text);
  return {
    text: text || 'Notification',
    severity: /critique|critical|fuite|leak/i.test(text) ? 'critique' : /alerte|alert|warning|haute|high/i.test(text) ? 'alerte' : 'info',
    incidentId: idMatch ? Number(idMatch[1]) : null,
  };
}

/** Garde la connexion SSE ouverte et expose les notifications reçues sous forme de signaux. */
@Injectable({ providedIn: 'root' })
export class NotificationStream {
  private factory = inject(EVENT_SOURCE_FACTORY);
  private source: EventSourceLike | null = null;
  private nextId = 1;
  private timers = new Map<number, ReturnType<typeof setTimeout>>();

  /** Durée d'affichage d'une bulle, en millisecondes. */
  readonly toastDurationMs = 8000;

  /** Les plus récentes d'abord (20 au maximum). */
  readonly items = signal<AppNotification[]>([]);
  /** Bulles actuellement affichées (4 au maximum). */
  readonly toasts = signal<AppNotification[]>([]);
  readonly connected = signal(false);
  /** Augmente à chaque message reçu : les écrans s'en servent pour se rafraîchir. */
  readonly tick = signal(0);

  /** Ouvre la connexion une seule fois, quel que soit le nombre d'écrans qui la demandent. */
  start(): void {
    if (this.source) {
      return;
    }
    const source = this.factory(NOTIFICATION_STREAM_URL);
    source.onopen = () => this.connected.set(true);
    // EventSource se reconnecte tout seul : on signale seulement que le flux est coupé.
    source.onerror = () => this.connected.set(false);
    source.onmessage = (event) => this.receive(String(event.data));
    this.source = source;
  }

  stop(): void {
    this.source?.close();
    this.source = null;
    this.connected.set(false);
    this.timers.forEach((timer) => clearTimeout(timer));
    this.timers.clear();
  }

  dismiss(id: number): void {
    const timer = this.timers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.timers.delete(id);
    }
    this.toasts.update((list) => list.filter((n) => n.id !== id));
  }

  private receive(raw: string): void {
    const notification: AppNotification = {
      id: this.nextId++,
      ...parseNotification(raw),
      receivedAt: new Date().toISOString(),
    };
    this.items.update((list) => [notification, ...list].slice(0, 20));
    this.toasts.update((list) => [notification, ...list].slice(0, 4));
    this.timers.set(
      notification.id,
      setTimeout(() => this.dismiss(notification.id), this.toastDurationMs),
    );
    this.tick.update((n) => n + 1);
  }
}
