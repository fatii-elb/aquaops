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
  roles?: string[];
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
  /** Nombre d'anomalies ouvertes, affiché sur l'entrée « Alertes & Anomalies » (rien si nul ou absent). */
  readonly alertsCount = input<number | null>(null);

  protected readonly userName = this.auth.username || 'Utilisateur';
  protected readonly bellOpen = signal(false);

  /** Même menu, même ordre et mêmes droits que le tableau de bord. */
  private readonly allNav: NavItem[] = [
    { key: 'dashboard', icon: 'grid_view', label: 'Tableau de bord', link: '/dashboard' },
    { key: 'assets', icon: 'hub', label: 'Réseau & Actifs', link: '/assets', roles: ['administrateur'] },
    { key: 'telemetry', icon: 'sensors', label: 'Télémétrie & Capteurs', link: '/telemetry' },
    { key: 'alerts', icon: 'warning', label: 'Alertes & Anomalies', link: '/alerts' },
    { key: 'incidents', icon: 'emergency', label: 'Incidents', link: '/incidents' },
    { key: 'interventions', icon: 'engineering', label: 'Interventions' },
    { key: 'reports', icon: 'analytics', label: 'Rapports & Analyses', link: '/reports', roles: ['superviseur', 'administrateur'] },
    { key: 'notifications', icon: 'notifications', label: 'Notifications' },
    { key: 'observability', icon: 'monitor_heart', label: 'Observabilité', link: '/observability' },
    { key: 'admin', icon: 'admin_panel_settings', label: 'Administration', link: '/administration', roles: ['administrateur'] },
    { key: 'audit', icon: 'history', label: "Journal d'audit", link: '/audit', roles: ['superviseur', 'administrateur'] },
  ];

  protected readonly nav: NavItem[] = this.allNav.filter((item) => !item.roles || this.auth.hasAnyRole(item.roles));

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
