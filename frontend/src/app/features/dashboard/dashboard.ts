import { NotificationToasts } from '../notifications/notification-toasts';
import { ZoneCards } from './zone-cards/zone-cards';
import { Component, OnDestroy, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, ZoneCards, NotificationToasts],
  templateUrl: './dashboard.html',
  host: { class: 'block' },
})
export class Dashboard implements OnDestroy {
  private auth = inject(AuthService);

  // Le journal d'audit est reserve aux superviseurs et administrateurs (FR-24).
  canSeeAudit = this.auth.hasAnyRole(['superviseur', 'administrateur']);

  // Nom de l'utilisateur connecté (lu dans le jeton Keycloak).
  userName = this.auth.username || 'GouloJK';

  now = signal(Dashboard.formatNow());
  refreshing = signal(false);

  private timer = setInterval(() => this.now.set(Dashboard.formatNow()), 30000);

  private static formatNow(): string {
    const d = new Date();
    const date = d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
    const time = d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return `${date} • ${time}`;
  }

  refresh() {
    this.refreshing.set(true);
    setTimeout(() => this.refreshing.set(false), 800);
  }

  logout() {
    this.auth.logout();
  }

  ngOnDestroy() {
    clearInterval(this.timer);
  }
}
