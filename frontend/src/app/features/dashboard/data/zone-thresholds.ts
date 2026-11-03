/**
 * Seuils d'affichage (valeurs provisoires, à aligner avec les règles du service de détection, E3).
 * Tout est regroupé ici : aucune valeur en dur dans les composants.
 */
export interface ZoneThresholds {
  pressureMinBar: number; // en dessous : alerte
  pressureCriticalBar: number; // en dessous : critique
  flowMaxM3h: number; // au dessus : alerte
  flowCriticalM3h: number; // au dessus : critique
  levelMinM: number; // en dessous : alerte
  levelCriticalM: number; // en dessous : critique
}

export const DEFAULT_THRESHOLDS: ZoneThresholds = {
  pressureMinBar: 2.0,
  pressureCriticalBar: 1.5,
  flowMaxM3h: 40,
  flowCriticalM3h: 45,
  levelMinM: 2.0,
  levelCriticalM: 1.0,
};

/** Délai entre deux rafraîchissements du tableau de bord (exigence FR-07 : 5 s au plus). */
export const REFRESH_MS = 5000;
