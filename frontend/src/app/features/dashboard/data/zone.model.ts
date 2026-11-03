/** État affiché d'une zone sur le tableau de bord. */
export type ZoneState = 'normal' | 'alerte' | 'critique';

export type PumpState = 'NORMAL' | 'FAULT';

/** Dernières valeurs mesurées dans une zone (null = pas de capteur ou pas de mesure). */
export interface ZoneValues {
  pressureBar: number | null;
  flowM3h: number | null;
  levelM: number | null;
  pumpState: PumpState | null;
}

export interface ZoneSummary extends ZoneValues {
  id: number;
  name: string;
  state: ZoneState;
  /** Horodatage de la dernière mise à jour, UTC, ISO 8601. */
  updatedAt: string;
}
