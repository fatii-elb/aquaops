import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AssetsApi } from './assets-api';
import {
  AssetsError,
  countByCategory,
  deleteVerdict,
  networkTotals,
  sensorsByType,
  toAssetsError,
  totalAssets,
} from './assets-rules';
import { Asset, AssetFormValue, AssetsSnapshot, CategoryKey, EMPTY_SNAPSHOT } from './assets.model';

/** Rappels appelés une fois l'opération terminée. */
export interface MutationCallbacks<T = void> {
  done: (result: T) => void;
  fail?: (error: AssetsError) => void;
}

/**
 * État de la page « Réseau & Actifs » : les cinq listes, leur chargement, et les opérations d'écriture.
 * Fourni par la page elle-même (providers du composant) : il disparaît avec elle.
 */
@Injectable()
export class AssetsStore {
  private api = inject(AssetsApi);
  private destroyRef = inject(DestroyRef);

  readonly snapshot = signal<AssetsSnapshot>(EMPTY_SNAPSHOT);
  /** Vrai dès que les listes ont été reçues au moins une fois. */
  readonly loaded = signal(false);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  /** Une écriture (création, modification, suppression) est en cours : bloque les doubles envois. */
  readonly saving = signal(false);
  /** Dernière erreur d'écriture, affichée dans le formulaire ou la boîte de confirmation. */
  readonly mutationError = signal<AssetsError | null>(null);

  readonly counts = computed(() => countByCategory(this.snapshot()));
  readonly total = computed(() => totalAssets(this.snapshot()));
  readonly totals = computed(() => networkTotals(this.snapshot()));
  readonly sensorTypes = computed(() => sensorsByType(this.snapshot()));

  /** (Re)charge les cinq listes. Appelé à l'ouverture, au clic sur « Actualiser » et après chaque écriture. */
  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .loadAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.snapshot.set(snapshot);
          this.loaded.set(true);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          // Le message dépend de la catégorie uniquement pour les refus 400, sans objet en lecture.
          this.error.set(toAssetsError(err, 'zones').message);
          this.loading.set(false);
        },
      });
  }

  clearMutationError(): void {
    this.mutationError.set(null);
  }

  /** Crée (id nul) ou modifie un actif, puis recharge les listes. Ignoré si une écriture est déjà en cours. */
  save(value: AssetFormValue, id: number | null, callbacks: MutationCallbacks<Asset>): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.mutationError.set(null);
    this.api
      .save(value, id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (saved) => {
          this.saving.set(false);
          this.load();
          callbacks.done(saved);
        },
        error: (err: unknown) => this.failed(err, value.category, callbacks),
      });
  }

  /**
   * Supprime un actif, puis recharge les listes. Le backend accepte de supprimer un actif qui a encore
   * des dépendances (il laisse des orphelins) : la règle de blocage est donc appliquée ici aussi.
   */
  remove(category: CategoryKey, id: number, callbacks: MutationCallbacks): void {
    if (this.saving()) {
      return;
    }
    const verdict = deleteVerdict(this.snapshot(), category, id);
    if (!verdict.allowed) {
      this.mutationError.set({ kind: 'validation', status: 0, message: verdict.reason ?? 'Suppression impossible.' });
      return;
    }
    this.saving.set(true);
    this.mutationError.set(null);
    this.api
      .remove(category, id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.load();
          callbacks.done();
        },
        error: (err: unknown) => this.failed(err, category, callbacks),
      });
  }

  private failed(err: unknown, category: CategoryKey, callbacks: { fail?: (e: AssetsError) => void }): void {
    const error = toAssetsError(err, category);
    this.saving.set(false);
    this.mutationError.set(error);
    // Une zone ou un actif a pu disparaître entre-temps : on recharge pour que les sélecteurs soient à jour.
    if (error.kind === 'validation' || error.kind === 'not-found') {
      this.load();
    }
    callbacks.fail?.(error);
  }
}
