import { Component, computed, input, output } from '@angular/core';
import { AssetRef, CellValue, detailFields, relationsOf } from '../assets-rules';
import { Asset, AssetsSnapshot, CategoryKey, categoryMeta } from '../assets.model';

/** Fiche détaillée d'un actif : propriétés, puis relations cliquables avec les autres actifs. */
@Component({
  selector: 'app-asset-detail',
  templateUrl: './asset-detail.html',
  host: { '(document:keydown.escape)': 'closed.emit()' },
})
export class AssetDetail {
  readonly category = input.required<CategoryKey>();
  readonly id = input.required<number>();
  readonly snapshot = input.required<AssetsSnapshot>();
  readonly canWrite = input(false);

  readonly closed = output<void>();
  readonly edit = output<void>();
  readonly remove = output<void>();
  readonly openRef = output<AssetRef>();

  protected readonly meta = computed(() => categoryMeta(this.category()));
  protected readonly item = computed<Asset | undefined>(() =>
    (this.snapshot()[this.category()] as Asset[]).find((a) => a.id === this.id()),
  );
  protected readonly fields = computed(() => detailFields(this.snapshot(), this.category(), this.id()));
  protected readonly groups = computed(() => relationsOf(this.snapshot(), this.category(), this.id()));

  protected text(value: CellValue): string {
    if (value === null || value === '') {
      return '—';
    }
    return typeof value === 'number' ? value.toLocaleString('fr-FR') : value;
  }

  protected icon(ref: AssetRef): string {
    return categoryMeta(ref.category).icon;
  }
}
