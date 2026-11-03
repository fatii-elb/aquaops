import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, switchMap, timer } from 'rxjs';
import { ZoneDataSource } from '../data/zone-data-source';
import { REFRESH_MS } from '../data/zone-thresholds';
import { ZoneState, ZoneSummary } from '../data/zone.model';

/** Une carte par zone (normal / alerte / critique), rafraîchie toutes les 5 secondes. */
@Component({
  selector: 'app-zone-cards',
  templateUrl: './zone-cards.html',
})
export class ZoneCards {
  private source = inject(ZoneDataSource);

  zones = signal<ZoneSummary[]>([]);
  error = signal(false);
  loaded = signal(false);

  constructor() {
    timer(0, REFRESH_MS)
      .pipe(
        // L'erreur est capturée dans le flux interne : le rafraîchissement continue après une panne.
        switchMap(() =>
          this.source.getZones().pipe(
            catchError(() => {
              this.error.set(true);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((zones) => {
        this.zones.set(zones);
        this.error.set(false);
        this.loaded.set(true);
      });
  }

  label(state: ZoneState): string {
    return { normal: 'Normal', alerte: 'Alerte', critique: 'Critique' }[state];
  }

  icon(state: ZoneState): string {
    return { normal: 'check_circle', alerte: 'warning', critique: 'error' }[state];
  }

  // Classes de la charte « Hydro Precision » (styles.css) : une bordure gauche colorée par état.
  cardClass(state: ZoneState): string {
    return {
      normal: 'border-tertiary',
      alerte: 'border-amber-500',
      critique: 'border-error',
    }[state];
  }

  badgeClass(state: ZoneState): string {
    return {
      normal: 'bg-tertiary-fixed/30 text-tertiary',
      alerte: 'bg-amber-100 text-amber-800',
      critique: 'bg-error-container text-on-error-container',
    }[state];
  }

  value(v: number | null, unit: string): string {
    return v === null ? '—' : `${v.toFixed(1)} ${unit}`;
  }

  time(iso: string): string {
    return new Date(iso).toLocaleTimeString('fr-FR');
  }
}
