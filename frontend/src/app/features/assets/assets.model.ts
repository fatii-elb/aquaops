/** Catégories d'actifs gérées par l'assets-service (FR-01, FR-02). */
export type CategoryKey = 'zones' | 'pipes' | 'tanks' | 'pumps' | 'sensors';

/** Types de capteurs acceptés par l'API (enum SensorType du backend). */
export type SensorType = 'PRESSURE' | 'FLOW' | 'LEVEL' | 'PUMP_STATE';

/** Type d'actif auquel un capteur est rattaché (enum AssetType du backend). */
export type AssetKind = 'PIPE' | 'TANK' | 'PUMP';

export interface Zone {
  id: number;
  name: string;
  description: string | null;
}

export interface Pipe {
  id: number;
  name: string;
  zoneId: number;
  lengthM: number | null;
  diameterMm: number | null;
}

export interface Tank {
  id: number;
  name: string;
  zoneId: number;
  capacityM3: number | null;
}

export interface Pump {
  id: number;
  name: string;
  zoneId: number;
}

export interface Sensor {
  id: number;
  name: string;
  type: SensorType;
  zoneId: number;
  assetType: AssetKind;
  assetId: number;
}

/** Correspondance catégorie → type d'élément renvoyé par l'API. */
export interface AssetMap {
  zones: Zone;
  pipes: Pipe;
  tanks: Tank;
  pumps: Pump;
  sensors: Sensor;
}

/** Corps envoyé en création (POST) et en modification (PUT) : l'identifiant est géré par le serveur. */
export type AssetInput<K extends CategoryKey> = Omit<AssetMap[K], 'id'>;

export type Asset = AssetMap[CategoryKey];

/** Valeur émise par le formulaire : la catégorie et le corps à envoyer à l'API. */
export type AssetFormValue = { [K in CategoryKey]: { category: K; body: AssetInput<K> } }[CategoryKey];

/** Photographie des cinq listes, chargées ensemble pour pouvoir calculer les relations. */
export interface AssetsSnapshot {
  zones: Zone[];
  pipes: Pipe[];
  tanks: Tank[];
  pumps: Pump[];
  sensors: Sensor[];
}

export const EMPTY_SNAPSHOT: AssetsSnapshot = { zones: [], pipes: [], tanks: [], pumps: [], sensors: [] };

export interface CategoryMeta {
  key: CategoryKey;
  /** Libellé de l'onglet (pluriel). */
  label: string;
  /** Libellé au singulier, pour les titres de formulaire et les messages. */
  singular: string;
  /** Accord des messages : « créée » (zone, conduite, pompe) ou « créé » (réservoir, capteur). */
  feminine: boolean;
  /** Icône Material Symbols. */
  icon: string;
  /** Chemin relatif sous /api/assets. */
  path: string;
}

export const CATEGORIES: readonly CategoryMeta[] = [
  { key: 'zones', label: 'Zones du réseau', singular: 'zone', feminine: true, icon: 'map', path: 'zones' },
  { key: 'pipes', label: 'Conduites', singular: 'conduite', feminine: true, icon: 'plumbing', path: 'pipes' },
  { key: 'tanks', label: 'Réservoirs', singular: 'réservoir', feminine: false, icon: 'water', path: 'tanks' },
  { key: 'pumps', label: 'Pompes', singular: 'pompe', feminine: true, icon: 'water_pump', path: 'pumps' },
  { key: 'sensors', label: 'Capteurs', singular: 'capteur', feminine: false, icon: 'sensors', path: 'sensors' },
];

export function categoryMeta(key: CategoryKey): CategoryMeta {
  return CATEGORIES.find((c) => c.key === key) as CategoryMeta;
}

export const SENSOR_TYPES: readonly SensorType[] = ['PRESSURE', 'FLOW', 'LEVEL', 'PUMP_STATE'];

export const SENSOR_TYPE_LABELS: Record<SensorType, string> = {
  PRESSURE: 'Pression',
  FLOW: 'Débit',
  LEVEL: 'Niveau',
  PUMP_STATE: 'État de pompe',
};

export const ASSET_KINDS: readonly AssetKind[] = ['PIPE', 'TANK', 'PUMP'];

export const ASSET_KIND_LABELS: Record<AssetKind, string> = {
  PIPE: 'Conduite',
  TANK: 'Réservoir',
  PUMP: 'Pompe',
};

/** Catégorie dans laquelle se trouve un actif d'un type donné. */
export const ASSET_KIND_CATEGORY: Record<AssetKind, CategoryKey> = {
  PIPE: 'pipes',
  TANK: 'tanks',
  PUMP: 'pumps',
};
