import { Component, input, output } from '@angular/core';

export type StateKind = 'loading' | 'empty' | 'error';

/** Bloc d'état d'une liste : chargement (squelette), absence de données ou erreur avec bouton « Réessayer ». */
@Component({
  selector: 'app-state-block',
  templateUrl: './state-block.html',
})
export class StateBlock {
  readonly kind = input.required<StateKind>();
  readonly title = input('');
  readonly message = input('');
  readonly icon = input('inbox');
  /** Nombre de lignes du squelette de chargement. */
  readonly rows = input(5);
  /** Affiche le bouton « Réessayer » en mode erreur. */
  readonly retryable = input(true);

  readonly retry = output<void>();

  protected skeleton(): number[] {
    return Array.from({ length: this.rows() }, (_, i) => i);
  }
}
