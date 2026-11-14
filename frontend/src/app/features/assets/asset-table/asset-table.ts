import { Component, computed, input, output } from '@angular/core';
import { AssetRow, COLUMNS, CellValue, ColumnDef, SortState } from '../assets-rules';
import { CategoryKey, Sensor, SensorType } from '../assets.model';

const TYPE_CLASSES: Record<SensorType, string> = {
  PRESSURE: 'bg-primary-fixed text-on-primary-fixed-variant',
  FLOW: 'bg-tertiary-fixed/60 text-on-tertiary-fixed-variant',
  LEVEL: 'bg-secondary-container text-on-secondary-container',
  PUMP_STATE: 'bg-surface-container-high text-on-surface',
};

/** Tableau des actifs d'une catégorie : en-têtes triables, lignes cliquables, actions de ligne. */
@Component({
  selector: 'app-asset-table',
  templateUrl: './asset-table.html',
})
export class AssetTable {
  readonly rows = input.required<AssetRow[]>();
  readonly category = input.required<CategoryKey>();
  readonly sort = input<SortState | null>(null);
  readonly canWrite = input(false);

  readonly sortChange = output<string>();
  readonly view = output<AssetRow>();
  readonly edit = output<AssetRow>();
  readonly remove = output<AssetRow>();

  protected readonly columns = computed<readonly ColumnDef[]>(() => COLUMNS[this.category()]);

  /** Entrée sur la ligne elle-même ouvre la fiche ; sur un bouton d'action, c'est le bouton qui agit. */
  protected rowKey(event: Event, row: AssetRow): void {
    if (event.target === event.currentTarget) {
      this.view.emit(row);
    }
  }

  protected ariaSort(key: string): 'ascending' | 'descending' | 'none' {
    const s = this.sort();
    if (!s || s.key !== key) {
      return 'none';
    }
    return s.direction === 'asc' ? 'ascending' : 'descending';
  }

  protected sortIcon(key: string): string {
    const s = this.sort();
    if (!s || s.key !== key) {
      return 'unfold_more';
    }
    return s.direction === 'asc' ? 'arrow_upward' : 'arrow_downward';
  }

  protected text(value: CellValue | undefined): string {
    if (value === null || value === undefined || value === '') {
      return '—';
    }
    return typeof value === 'number' ? value.toLocaleString('fr-FR') : value;
  }

  protected typeClass(row: AssetRow): string {
    return TYPE_CLASSES[(row.raw as Sensor).type];
  }

  /** Colonnes de comptage (équipements ou capteurs liés), affichées en pastille. */
  protected isCount(key: string): boolean {
    return key === 'sensors' || key === 'pipes' || key === 'tanks' || key === 'pumps';
  }
}
