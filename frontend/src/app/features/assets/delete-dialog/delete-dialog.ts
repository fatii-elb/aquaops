import { Component, computed, input, output } from '@angular/core';
import { AssetRef, DeleteVerdict } from '../assets-rules';
import { categoryMeta } from '../assets.model';

export interface DeleteTarget {
  ref: AssetRef;
  verdict: DeleteVerdict;
}

/** Confirmation de suppression, ou explication du blocage quand des éléments dépendent encore de l'actif. */
@Component({
  selector: 'app-delete-dialog',
  templateUrl: './delete-dialog.html',
  host: { '(document:keydown.escape)': 'cancelled.emit()' },
})
export class DeleteDialog {
  readonly target = input.required<DeleteTarget>();
  readonly busy = input(false);
  /** Erreur renvoyée par l'API lors de la dernière tentative. */
  readonly error = input<string | null>(null);

  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
  readonly openRef = output<AssetRef>();

  /** Nombre maximal de dépendances listées ; le reste est résumé. */
  private readonly limit = 8;

  protected readonly singular = computed(() => categoryMeta(this.target().ref.category).singular);
  protected readonly shown = computed(() => this.target().verdict.blockers.slice(0, this.limit));
  protected readonly hidden = computed(() => Math.max(0, this.target().verdict.blockers.length - this.limit));

  protected icon(ref: AssetRef): string {
    return categoryMeta(ref.category).icon;
  }
}
