/** Seuils de détection d'une zone (FR-13), tels que renvoyés et acceptés par le detection-service. */
export interface ThresholdValues {
  /** Fuite : pression minimale (bar). */
  leakMinPressure: number;
  /** Fuite : débit maximal. */
  leakMaxFlow: number;
  /** Fuite : durée de pression basse avant alerte (s). */
  leakDurationSeconds: number;
  /** Capteur silencieux : délai sans mesure avant alerte (s). */
  silenceThresholdSeconds: number;
  /** Réservoir : niveau minimal (m), en dessous duquel une anomalie est créée. */
  tankMinLevel: number;
}

export type ThresholdKey = keyof ThresholdValues;

export interface ZoneThresholdView {
  zoneId: number;
  /** Faux : la zone utilise les valeurs par défaut. */
  custom: boolean;
  thresholds: ThresholdValues;
  updatedAt: string | null;
  updatedBy: string | null;
}

/** GET /api/detection/thresholds : valeurs par défaut et zones personnalisées. */
export interface ThresholdsResponse {
  defaults: ThresholdValues;
  zones: ZoneThresholdView[];
}

export interface ThresholdField {
  key: ThresholdKey;
  label: string;
  unit: string;
  help: string;
  min: number;
  max: number;
  /** Vrai : la valeur minimale est autorisée (≥) ; faux : elle doit être dépassée (>). */
  minIncluded: boolean;
  integer: boolean;
  step: number;
}

/** Bornes identiques à celles du service (ThresholdValidator.java). */
export const THRESHOLD_FIELDS: readonly ThresholdField[] = [
  { key: 'leakMinPressure', label: 'Pression minimale', unit: 'bar', min: 0, max: 100, minIncluded: true, integer: false, step: 0.1,
    help: 'Fuite probable si la pression reste sous ce seuil…' },
  { key: 'leakDurationSeconds', label: 'Durée de pression basse', unit: 's', min: 1, max: 3600, minIncluded: true, integer: true, step: 1,
    help: '…pendant cette durée…' },
  { key: 'leakMaxFlow', label: 'Débit maximal', unit: '', min: 0, max: 100000, minIncluded: false, integer: false, step: 1,
    help: '…et que le débit dépasse ce seuil.' },
  { key: 'silenceThresholdSeconds', label: 'Délai de silence d’un capteur', unit: 's', min: 1, max: 3600, minIncluded: true, integer: true, step: 1,
    help: 'Capteur silencieux si aucune mesure depuis ce délai.' },
  { key: 'tankMinLevel', label: 'Niveau minimal de réservoir', unit: 'm', min: 0, max: 1000, minIncluded: true, integer: false, step: 0.1,
    help: 'Niveau bas si la mesure est strictement inférieure à ce seuil.' },
];
