import { Component, OnDestroy, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  host: { class: 'block' },
})
export class Dashboard implements OnDestroy {
  userName = 'GouloJK';

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

  ngOnDestroy() {
    clearInterval(this.timer);
  }
}
