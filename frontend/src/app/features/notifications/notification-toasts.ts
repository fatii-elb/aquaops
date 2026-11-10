import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationSeverity, NotificationStream } from '../../core/notification-stream';

/**
 * Bulles de notification en temps réel (coin haut droit). À placer une fois par page :
 * la connexion au flux est partagée par toute l'application.
 */
@Component({
  selector: 'app-notification-toasts',
  imports: [RouterLink],
  templateUrl: './notification-toasts.html',
})
export class NotificationToasts {
  protected stream = inject(NotificationStream);

  constructor() {
    this.stream.start();
  }

  protected cardClass(severity: NotificationSeverity): string {
    return severity === 'critique'
      ? 'border-error bg-error-container text-on-error-container'
      : severity === 'alerte'
        ? 'border-amber-500 bg-amber-50 text-amber-900'
        : 'border-primary bg-surface-container-lowest text-on-surface';
  }

  protected icon(severity: NotificationSeverity): string {
    return severity === 'critique' ? 'error' : severity === 'alerte' ? 'warning' : 'notifications';
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }
}
