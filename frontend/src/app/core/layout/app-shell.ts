import { Component, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NotificationToasts } from '../../features/notifications/notification-toasts';
import { AuthService } from '../auth.service';
import { NotificationStream } from '../notification-stream';

interface NavItem {
  key: string;
  icon: string;
  label: string;
  link?: string;
}

/** Cadre commun des pages : menu à gauche, barre du haut (état du temps réel, cloche), contenu au centre. */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, NotificationToasts],
  templateUrl: './app-shell.html',
})
export class AppShell {
  private auth = inject(AuthService);
  protected stream = inject(NotificationStream);

  /** Dernier élément du fil d'Ariane. */
  readonly title = input('Supervision');
  /** Entrée du menu mise en évidence. */
  readonly active = input('incidents');

  protected readonly userName = this.auth.username || 'Utilisateur';
  protected readonly bellOpen = signal(false);

  protected readonly nav: NavItem[] = [
    { key: 'dashboard', icon: 'grid_view', label: 'Tableau de bord', link: '/dashboard' },
    { key: 'assets', icon: 'hub', label: 'Réseau & Actifs', link: '/assets' },
    { key: 'telemetry', icon: 'sensors', label: 'Télémétrie & Capteurs' },
    { key: 'alerts', icon: 'warning', label: 'Alertes & Anomalies' },
    { key: 'incidents', icon: 'emergency', label: 'Incidents', link: '/incidents' },
    { key: 'interventions', icon: 'engineering', label: 'Interventions' },
    { key: 'reports', icon: 'analytics', label: 'Rapports & Analyses', link: '/reports' },
    { key: 'observability', icon: 'monitor_heart', label: 'Observabilité' },
    { key: 'admin', icon: 'admin_panel_settings', label: 'Administration', link: '/administration' },
  ];

  protected badge(): string {
    const n = this.stream.items().length;
    return n > 9 ? '9+' : String(n);
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  }

  protected logout(): void {
    this.auth.logout();
  }
}
