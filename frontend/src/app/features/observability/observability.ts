import { Component, DestroyRef, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { switchMap, startWith } from 'rxjs/operators';
import { AppShell } from '../../core/layout/app-shell';
import { describeHttpError } from '../../shared/http-errors';
import { ServiceStatus } from './service-status.model';
import { ServiceStatusDataSource } from './service-status-data-source';

/**
 * Observabilité (FE-11) : état des services supervisés, rafraîchi périodiquement.
 * Réservé aux rôles superviseur et administrateur (voir app.routes.ts).
 * Construite sur un mock ; branchement sur une vraie route d'état à faire plus tard.
 */
@Component({
  selector: 'app-observability',
  imports: [AppShell],
  templateUrl: './observability.html',
  styleUrl: './observability.css',
  encapsulation: ViewEncapsulation.None,
  host: { class: 'block' },
})
export class Observability implements OnInit {
  private source = inject(ServiceStatusDataSource);
  private destroyRef = inject(DestroyRef);

  /** Intervalle de rafraîchissement : 10 s (critère FE-11 : DOWN en 30 s au plus). */
  readonly refreshMs = 10_000;

  readonly statuses = signal<ServiceStatus[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  /** Dernière mise à jour réussie, pour l'horodatage global. */
  readonly lastCheckedAt = signal<string | null>(null);

  ngOnInit(): void {
    interval(this.refreshMs)
      .pipe(
        startWith(0),
        switchMap(() => this.source.getStatuses()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (statuses) => {
          this.statuses.set(statuses);
          this.error.set(null);
          this.loading.set(false);
          this.lastCheckedAt.set(statuses[0]?.checkedAt ?? new Date().toISOString());
        },
        error: (err: unknown) => {
          this.error.set(`Supervision indisponible — ${describeHttpError(err, 'l’état des services').message}`);
          this.loading.set(false);
        },
      });
  }

  /** Rechargement manuel (bouton « Réessayer »). */
  retry(): void {
    this.loading.set(true);
    this.error.set(null);
    this.source.getStatuses().subscribe({
      next: (statuses) => {
        this.statuses.set(statuses);
        this.lastCheckedAt.set(statuses[0]?.checkedAt ?? new Date().toISOString());
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(`Supervision indisponible — ${describeHttpError(err, 'l’état des services').message}`);
        this.loading.set(false);
      },
    });
  }

  /** Classe CSS d'une pastille selon l'état. */
  stateClass(state: ServiceStatus['state']): string {
    return state === 'UP' ? 'state-up' : state === 'DOWN' ? 'state-down' : 'state-unknown';
  }

  /** Libellé affiché de l'état. */
  stateLabel(state: ServiceStatus['state']): string {
    return state === 'UP' ? 'UP' : state === 'DOWN' ? 'DOWN' : 'INCONNU';
  }
}