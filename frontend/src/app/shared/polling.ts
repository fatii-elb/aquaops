import { Observable, concat, filter, interval, map, of } from 'rxjs';

/**
 * Émet immédiatement (chargement initial), puis toutes les `intervalMs` millisecondes, sauf quand l'onglet est masqué.
 * À combiner avec switchMap (pas de requêtes qui se chevauchent) et takeUntilDestroyed.
 */
export function pollEvery(intervalMs: number, isVisible: () => boolean = () => !document.hidden): Observable<void> {
  return concat(
    of(undefined),
    interval(intervalMs).pipe(
      filter(() => isVisible()),
      map(() => undefined),
    ),
  );
}
