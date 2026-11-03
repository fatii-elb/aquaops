import { ZoneState, ZoneValues } from './zone.model';
import { DEFAULT_THRESHOLDS, ZoneThresholds } from './zone-thresholds';

/**
 * Calcule l'état d'une zone à partir de ses dernières valeurs.
 * Fonction pure : provisoire, jusqu'à ce que l'état vienne des incidents (SCRUM-37).
 * - critique : pompe en défaut, ou un seuil critique franchi
 * - alerte   : un seuil d'alerte franchi
 * - normal   : sinon (une valeur absente est ignorée)
 */
export function computeZoneState(
  v: ZoneValues,
  t: ZoneThresholds = DEFAULT_THRESHOLDS,
): ZoneState {
  const below = (value: number | null, limit: number) => value !== null && value < limit;
  const above = (value: number | null, limit: number) => value !== null && value > limit;

  if (
    v.pumpState === 'FAULT' ||
    below(v.pressureBar, t.pressureCriticalBar) ||
    above(v.flowM3h, t.flowCriticalM3h) ||
    below(v.levelM, t.levelCriticalM)
  ) {
    return 'critique';
  }
  if (
    below(v.pressureBar, t.pressureMinBar) ||
    above(v.flowM3h, t.flowMaxM3h) ||
    below(v.levelM, t.levelMinM)
  ) {
    return 'alerte';
  }
  return 'normal';
}
