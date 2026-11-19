import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth.service';
import { AppShell } from '../../core/layout/app-shell';
import { HttpErrorInfo, describeHttpError } from '../../shared/http-errors';
import { StateBlock } from '../../shared/state-block/state-block';
import { ZoneNames } from '../incidents/zone-names';
import { ThresholdForm } from './threshold-form/threshold-form';
import { ThresholdApi } from './thresholds-api';
import { THRESHOLD_FIELDS, ThresholdValues, ThresholdsResponse } from './thresholds.model';
import { FieldErrors, ThresholdRow, buildRows, formatValue, parseServerErrors } from './thresholds.rules';

/**
 * Seuils de détection par zone (FR-13). Route réservée à l'administrateur ; la modification est en plus
 * conditionnée au rôle ici. Le serveur reste l'autorité : un 403 est affiché proprement.
 */
@Component({
  selector: 'app-thresholds-page',
  imports: [AppShell, StateBlock, ThresholdForm],
  templateUrl: './thresholds-page.html',
})
export class ThresholdsPage implements OnInit {
  private api = inject(ThresholdApi);
  private zoneNames = inject(ZoneNames);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  protected readonly canWrite = this.auth.hasAnyRole(['administrateur']);
  protected readonly fields = THRESHOLD_FIELDS;
  protected readonly format = formatValue;

  protected readonly response = signal<ThresholdsResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<HttpErrorInfo | null>(null);

  protected readonly editing = signal<ThresholdRow | null>(null);
  protected readonly saving = signal(false);
  protected readonly serverErrors = signal<FieldErrors>({});
  protected readonly formError = signal<string | null>(null);
  protected readonly toast = signal<{ kind: 'success' | 'error'; text: string } | null>(null);
  private toastTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly rows = computed(() => {
    const response = this.response();
    return response ? buildRows(this.zoneNames.zones(), response) : [];
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => clearTimeout(this.toastTimer));
    this.zoneNames.load();
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.response.set(response);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.error.set(describeHttpError(err, 'les seuils'));
          this.loading.set(false);
        },
      });
  }

  protected edit(row: ThresholdRow): void {
    this.serverErrors.set({});
    this.formError.set(null);
    this.editing.set(row);
  }

  protected closeForm(): void {
    this.editing.set(null);
  }

  protected save(values: ThresholdValues): void {
    const row = this.editing();
    if (!row || this.saving()) {
      return;
    }
    this.saving.set(true);
    this.serverErrors.set({});
    this.formError.set(null);
    this.api
      .save(row.zoneId, values)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.editing.set(null);
          this.notify('success', `Seuils de « ${row.name} » enregistrés. Le moteur de détection les applique sous 15 s (30 s au plus), sans redémarrage.`);
          this.load();
        },
        error: (err: unknown) => {
          this.saving.set(false);
          const fieldErrors = parseServerErrors(err);
          this.serverErrors.set(fieldErrors);
          const info = describeHttpError(err, 'les seuils');
          if (Object.keys(fieldErrors).length > 0) {
            this.formError.set('Certaines valeurs ont été refusées par le serveur. Corrigez-les puis réessayez.');
          } else if (info.kind === 'forbidden') {
            this.formError.set('Action refusée : le rôle administrateur est requis pour modifier les seuils.');
          } else {
            const text =
              info.kind === 'network'
                ? 'Le service de détection est injoignable. Les seuils n’ont pas été enregistrés ; réessayez.'
                : info.kind === 'server'
                  ? 'Le service de détection a rencontré une erreur. Les seuils n’ont pas été enregistrés ; réessayez dans un instant.'
                  : info.message;
            this.formError.set(text);
            this.notify('error', text);
          }
        },
      });
  }

  protected date(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString('fr-FR') : '—';
  }

  protected notify(kind: 'success' | 'error', text: string): void {
    clearTimeout(this.toastTimer);
    this.toast.set({ kind, text });
    this.toastTimer = setTimeout(() => this.toast.set(null), 7000);
  }

  protected dismissToast(): void {
    clearTimeout(this.toastTimer);
    this.toast.set(null);
  }
}
