import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../../../core/auth.service';
import { AppShell } from '../../../core/layout/app-shell';
import { AssetDetail } from '../asset-detail/asset-detail';
import { AssetForm } from '../asset-form/asset-form';
import { AssetTable } from '../asset-table/asset-table';
import { DeleteDialog, DeleteTarget } from '../delete-dialog/delete-dialog';
import {
  AssetFilters,
  AssetRef,
  AssetRow,
  NO_FILTERS,
  SortState,
  buildRows,
  deleteVerdict,
  filterRows,
  hasActiveFilters,
  nextSort,
  paginate,
  sortRows,
} from '../assets-rules';
import { AssetsStore } from '../assets-store';
import {
  ASSET_KINDS,
  ASSET_KIND_LABELS,
  Asset,
  AssetFormValue,
  AssetKind,
  CATEGORIES,
  CategoryKey,
  SENSOR_TYPES,
  SENSOR_TYPE_LABELS,
  SensorType,
  categoryMeta,
} from '../assets.model';

interface Toast {
  kind: 'success' | 'error';
  text: string;
}

interface FormState {
  category: CategoryKey;
  /** Élément en cours de modification ; nul en création. */
  item: Asset | null;
}

/** Couleurs des segments de la répartition, dans l'ordre des catégories. */
const SEGMENT_COLORS = ['bg-primary', 'bg-primary-container', 'bg-tertiary', 'bg-tertiary-container', 'bg-secondary'];

/**
 * Page « Réseau & Actifs » (FE-3). La route est réservée au rôle administrateur ; les actions d'écriture
 * sont en plus conditionnées au rôle ici. Le serveur reste l'autorité : un 403 est affiché proprement.
 */
@Component({
  selector: 'app-assets-page',
  imports: [AppShell, AssetTable, AssetDetail, AssetForm, DeleteDialog],
  providers: [AssetsStore],
  templateUrl: './assets-page.html',
})
export class AssetsPage implements OnInit {
  protected store = inject(AssetsStore);
  private auth = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  protected readonly canWrite = this.auth.hasAnyRole(['administrateur']);
  protected readonly categories = CATEGORIES;
  protected readonly sensorTypes = SENSOR_TYPES;
  protected readonly sensorTypeLabels = SENSOR_TYPE_LABELS;
  protected readonly assetKinds = ASSET_KINDS;
  protected readonly assetKindLabels = ASSET_KIND_LABELS;
  protected readonly pageSizes = [10, 25, 50];

  protected readonly category = signal<CategoryKey>('zones');
  protected readonly filters = signal<AssetFilters>(NO_FILTERS);
  protected readonly sort = signal<SortState | null>({ key: 'id', direction: 'asc' });
  protected readonly pageIndex = signal(1);
  protected readonly pageSize = signal(10);

  protected readonly selected = signal<AssetRef | null>(null);
  protected readonly formState = signal<FormState | null>(null);
  protected readonly deleteTarget = signal<DeleteTarget | null>(null);
  protected readonly toast = signal<Toast | null>(null);
  private toastTimer: ReturnType<typeof setTimeout> | undefined;

  protected readonly meta = computed(() => categoryMeta(this.category()));
  protected readonly filtersActive = computed(() => hasActiveFilters(this.filters()));
  protected readonly rows = computed(() =>
    sortRows(filterRows(buildRows(this.store.snapshot(), this.category()), this.filters()), this.sort()),
  );
  protected readonly page = computed(() => paginate(this.rows(), this.pageIndex(), this.pageSize()));
  /** La fiche ne s'affiche que si l'élément existe encore (il peut avoir disparu après un rechargement). */
  protected readonly detailVisible = computed(() => {
    const sel = this.selected();
    return sel !== null && (this.store.snapshot()[sel.category] as Asset[]).some((a) => a.id === sel.id);
  });
  protected readonly inCategory = computed(() => this.store.counts()[this.category()]);

  protected readonly distribution = computed(() => {
    const counts = this.store.counts();
    const total = this.store.total();
    return CATEGORIES.map((c, i) => ({
      key: c.key,
      label: c.label,
      count: counts[c.key],
      pct: total === 0 ? 0 : (counts[c.key] / total) * 100,
      color: SEGMENT_COLORS[i],
    }));
  });

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => clearTimeout(this.toastTimer));
    this.store.load();
  }

  /* ---- navigation et filtres ---- */

  protected selectCategory(key: CategoryKey): void {
    if (key === this.category()) {
      return;
    }
    this.category.set(key);
    this.filters.set(NO_FILTERS);
    this.sort.set({ key: 'id', direction: 'asc' });
    this.pageIndex.set(1);
  }

  private patchFilters(patch: Partial<AssetFilters>): void {
    this.filters.update((f) => ({ ...f, ...patch }));
    this.pageIndex.set(1);
  }

  protected setQuery(value: string): void {
    this.patchFilters({ query: value });
  }

  protected setZone(value: string): void {
    this.patchFilters({ zoneId: value === '' ? null : Number(value) });
  }

  protected setSensorType(value: string): void {
    this.patchFilters({ sensorType: value === '' ? null : (value as SensorType) });
  }

  protected setAssetKind(value: string): void {
    this.patchFilters({ assetKind: value === '' ? null : (value as AssetKind) });
  }

  protected resetFilters(): void {
    this.filters.set(NO_FILTERS);
    this.pageIndex.set(1);
  }

  protected toggleSort(key: string): void {
    this.sort.set(nextSort(this.sort(), key));
    this.pageIndex.set(1);
  }

  protected setPage(index: number): void {
    this.pageIndex.set(index);
  }

  protected setPageSize(value: string): void {
    this.pageSize.set(Number(value));
    this.pageIndex.set(1);
  }

  /* ---- fiche détaillée ---- */

  protected view(ref: { category: CategoryKey; id: number; name: string }): void {
    this.selected.set({ category: ref.category, id: ref.id, name: ref.name });
  }

  protected viewRow(row: AssetRow): void {
    this.view(row);
  }

  protected closeDetail(): void {
    this.selected.set(null);
  }

  /* ---- création et modification ---- */

  protected openCreate(): void {
    this.store.clearMutationError();
    this.formState.set({ category: this.category(), item: null });
  }

  protected openEdit(category: CategoryKey, id: number): void {
    const item = (this.store.snapshot()[category] as Asset[]).find((a) => a.id === id);
    if (!item) {
      return;
    }
    this.store.clearMutationError();
    this.selected.set(null);
    this.formState.set({ category, item });
  }

  protected closeForm(): void {
    this.formState.set(null);
    this.store.clearMutationError();
  }

  protected onSave(value: AssetFormValue): void {
    const editing = this.formState()?.item ?? null;
    this.store.save(value, editing?.id ?? null, {
      done: (saved) => {
        this.formState.set(null);
        const meta = categoryMeta(value.category);
        const label = meta.singular.charAt(0).toUpperCase() + meta.singular.slice(1);
        const verb = editing ? 'modifié' : 'créé';
        this.notify('success', `${label} « ${saved.name} » ${verb}${meta.feminine ? 'e' : ''}.`);
        if (editing) {
          this.selected.set({ category: value.category, id: saved.id, name: saved.name });
        } else {
          // On montre l'élément créé : bonne catégorie, sans filtre, dernière page (tri par identifiant).
          this.category.set(value.category);
          this.filters.set(NO_FILTERS);
          this.sort.set({ key: 'id', direction: 'asc' });
          this.pageIndex.set(Number.MAX_SAFE_INTEGER);
        }
      },
      // Les refus de validation restent affichés dans le formulaire ; les autres échecs sont signalés en plus.
      fail: (error) => {
        if (error.kind !== 'validation') {
          this.notify('error', error.message);
        }
      },
    });
  }

  /* ---- suppression ---- */

  protected askDelete(ref: { category: CategoryKey; id: number; name: string }): void {
    this.store.clearMutationError();
    this.selected.set(null);
    this.deleteTarget.set({
      ref: { category: ref.category, id: ref.id, name: ref.name },
      verdict: deleteVerdict(this.store.snapshot(), ref.category, ref.id),
    });
  }

  protected cancelDelete(): void {
    this.deleteTarget.set(null);
    this.store.clearMutationError();
  }

  protected openFromDialog(ref: AssetRef): void {
    this.cancelDelete();
    this.view(ref);
  }

  protected confirmDelete(): void {
    const target = this.deleteTarget();
    if (!target) {
      return;
    }
    this.store.remove(target.ref.category, target.ref.id, {
      done: () => {
        this.deleteTarget.set(null);
        const meta = categoryMeta(target.ref.category);
        const label = meta.singular.charAt(0).toUpperCase() + meta.singular.slice(1);
        this.notify('success', `${label} « ${target.ref.name} » supprimé${meta.feminine ? 'e' : ''}.`);
      },
      fail: (error) => {
        if (error.kind !== 'validation') {
          this.notify('error', error.message);
        }
      },
    });
  }

  /* ---- notifications ---- */

  protected notify(kind: Toast['kind'], text: string): void {
    clearTimeout(this.toastTimer);
    this.toast.set({ kind, text });
    this.toastTimer = setTimeout(() => this.toast.set(null), 6000);
  }

  protected dismissToast(): void {
    clearTimeout(this.toastTimer);
    this.toast.set(null);
  }
}
