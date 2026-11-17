import {
  ASSET_KIND_CATEGORY,
  ASSET_KIND_LABELS,
  Asset,
  AssetKind,
  AssetsSnapshot,
  CATEGORIES,
  CategoryKey,
  Pipe,
  Pump,
  SENSOR_TYPE_LABELS,
  Sensor,
  SensorType,
  Tank,
} from './assets.model';

/* ------------------------------------------------------------------ */
/*  Indicateurs (KPI)                                                  */
/* ------------------------------------------------------------------ */

export function countByCategory(s: AssetsSnapshot): Record<CategoryKey, number> {
  return {
    zones: s.zones.length,
    pipes: s.pipes.length,
    tanks: s.tanks.length,
    pumps: s.pumps.length,
    sensors: s.sensors.length,
  };
}

export function totalAssets(s: AssetsSnapshot): number {
  const c = countByCategory(s);
  return c.zones + c.pipes + c.tanks + c.pumps + c.sensors;
}

/** Longueur cumulée des conduites (m) et capacité cumulée des réservoirs (m³), valeurs absentes ignorées. */
export function networkTotals(s: AssetsSnapshot): { pipeLengthM: number; tankCapacityM3: number } {
  return {
    pipeLengthM: s.pipes.reduce((sum, p) => sum + (p.lengthM ?? 0), 0),
    tankCapacityM3: s.tanks.reduce((sum, t) => sum + (t.capacityM3 ?? 0), 0),
  };
}

export function sensorsByType(s: AssetsSnapshot): Record<SensorType, number> {
  const out: Record<SensorType, number> = { PRESSURE: 0, FLOW: 0, LEVEL: 0, PUMP_STATE: 0 };
  for (const sensor of s.sensors) {
    out[sensor.type] += 1;
  }
  return out;
}

/* ------------------------------------------------------------------ */
/*  Relations                                                          */
/* ------------------------------------------------------------------ */

/** Référence légère vers un actif, pour les listes de relations et de blocages. */
export interface AssetRef {
  category: CategoryKey;
  id: number;
  name: string;
}

export function zoneName(s: AssetsSnapshot, zoneId: number | null | undefined): string {
  if (zoneId === null || zoneId === undefined) {
    return '—';
  }
  return s.zones.find((z) => z.id === zoneId)?.name ?? `Zone ${zoneId} (introuvable)`;
}

/** Équipement (conduite, réservoir ou pompe) auquel un capteur est rattaché, s'il existe. */
export function assetOfSensor(s: AssetsSnapshot, sensor: Sensor): Pipe | Tank | Pump | undefined {
  switch (sensor.assetType) {
    case 'PIPE':
      return s.pipes.find((p) => p.id === sensor.assetId);
    case 'TANK':
      return s.tanks.find((t) => t.id === sensor.assetId);
    case 'PUMP':
      return s.pumps.find((p) => p.id === sensor.assetId);
  }
}

/** « Conduite · Conduite Z1 », ou « Conduite n° 9 (introuvable) » si l'actif n'existe plus. */
export function assetLabelOfSensor(s: AssetsSnapshot, sensor: Sensor): string {
  const kind = ASSET_KIND_LABELS[sensor.assetType];
  const asset = assetOfSensor(s, sensor);
  return asset ? `${kind} · ${asset.name}` : `${kind} n° ${sensor.assetId} (introuvable)`;
}

export function sensorsOfAsset(s: AssetsSnapshot, kind: AssetKind, assetId: number): Sensor[] {
  return s.sensors.filter((x) => x.assetType === kind && x.assetId === assetId);
}

const SENSOR_HOST_KIND: Partial<Record<CategoryKey, AssetKind>> = { pipes: 'PIPE', tanks: 'TANK', pumps: 'PUMP' };

/**
 * Éléments qui dépendent d'un actif :
 * - une zone : toutes les conduites, tous les réservoirs, toutes les pompes et tous les capteurs de la zone ;
 * - une conduite, un réservoir ou une pompe : les capteurs qui y sont rattachés ;
 * - un capteur : aucun.
 */
export function dependentsOf(s: AssetsSnapshot, category: CategoryKey, id: number): AssetRef[] {
  const ref = (c: CategoryKey) => (a: { id: number; name: string }): AssetRef => ({ category: c, id: a.id, name: a.name });
  if (category === 'zones') {
    return [
      ...s.pipes.filter((x) => x.zoneId === id).map(ref('pipes')),
      ...s.tanks.filter((x) => x.zoneId === id).map(ref('tanks')),
      ...s.pumps.filter((x) => x.zoneId === id).map(ref('pumps')),
      ...s.sensors.filter((x) => x.zoneId === id).map(ref('sensors')),
    ];
  }
  const kind = SENSOR_HOST_KIND[category];
  return kind ? sensorsOfAsset(s, kind, id).map(ref('sensors')) : [];
}

export interface DeleteVerdict {
  allowed: boolean;
  blockers: AssetRef[];
  /** Message à afficher quand la suppression est refusée. */
  reason: string | null;
}

/**
 * Décision FE-3 : le backend ne vérifie pas les dépendances (pas de clé étrangère, DELETE répond 204
 * et laisse des éléments orphelins). L'interface refuse donc la suppression tant que des éléments
 * en dépendent. Cette protection est uniquement côté frontend : le backend doit appliquer la même règle.
 */
export function deleteVerdict(s: AssetsSnapshot, category: CategoryKey, id: number): DeleteVerdict {
  const blockers = dependentsOf(s, category, id);
  if (blockers.length === 0) {
    return { allowed: true, blockers, reason: null };
  }
  const n = blockers.length;
  const what = category === 'zones' ? 'équipement(s) ou capteur(s)' : 'capteur(s)';
  return {
    allowed: false,
    blockers,
    reason: `Suppression impossible : ${n} ${what} dépendent encore de cet élément. Supprimez ou déplacez-les d'abord.`,
  };
}

/* ------------------------------------------------------------------ */
/*  Lignes de tableau : recherche, filtres, tri, pagination            */
/* ------------------------------------------------------------------ */

export type CellValue = string | number | null;

export interface AssetRow {
  category: CategoryKey;
  id: number;
  name: string;
  /** Zone de rattachement ; null pour une zone. */
  zoneId: number | null;
  /** Valeurs affichables et triables, indexées par clé de colonne. */
  cells: Record<string, CellValue>;
  /** Texte normalisé (minuscules, sans accents) utilisé par la recherche rapide. */
  search: string;
  raw: Asset;
}

export interface ColumnDef {
  key: string;
  label: string;
  /** Colonne numérique : alignée à droite et triée numériquement. */
  numeric?: boolean;
}

/** Colonnes de chaque catégorie, adaptées aux propriétés réelles renvoyées par l'API. */
export const COLUMNS: Record<CategoryKey, readonly ColumnDef[]> = {
  zones: [
    { key: 'id', label: 'ID', numeric: true },
    { key: 'name', label: 'Nom' },
    { key: 'description', label: 'Description' },
    { key: 'pipes', label: 'Conduites', numeric: true },
    { key: 'tanks', label: 'Réservoirs', numeric: true },
    { key: 'pumps', label: 'Pompes', numeric: true },
    { key: 'sensors', label: 'Capteurs', numeric: true },
  ],
  pipes: [
    { key: 'id', label: 'ID', numeric: true },
    { key: 'name', label: 'Nom' },
    { key: 'zone', label: 'Zone' },
    { key: 'lengthM', label: 'Longueur (m)', numeric: true },
    { key: 'diameterMm', label: 'Diamètre (mm)', numeric: true },
    { key: 'sensors', label: 'Capteurs', numeric: true },
  ],
  tanks: [
    { key: 'id', label: 'ID', numeric: true },
    { key: 'name', label: 'Nom' },
    { key: 'zone', label: 'Zone' },
    { key: 'capacityM3', label: 'Capacité (m³)', numeric: true },
    { key: 'sensors', label: 'Capteurs', numeric: true },
  ],
  pumps: [
    { key: 'id', label: 'ID', numeric: true },
    { key: 'name', label: 'Nom' },
    { key: 'zone', label: 'Zone' },
    { key: 'sensors', label: 'Capteurs', numeric: true },
  ],
  sensors: [
    { key: 'id', label: 'ID', numeric: true },
    { key: 'name', label: 'Nom' },
    { key: 'type', label: 'Type' },
    { key: 'zone', label: 'Zone' },
    { key: 'asset', label: 'Actif rattaché' },
  ],
};

/** Minuscules et sans accents : « DEBIT » retrouve « Débit ». */
export function normalize(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

function makeRow(
  s: AssetsSnapshot,
  category: CategoryKey,
  item: Asset,
  zoneId: number | null,
  cells: Record<string, CellValue>,
): AssetRow {
  const text = [item.id, item.name, ...Object.values(cells)]
    .filter((v) => v !== null && v !== undefined)
    .join(' ');
  return { category, id: item.id, name: item.name, zoneId, cells: { id: item.id, name: item.name, ...cells }, search: normalize(text), raw: item };
}

/** Construit les lignes du tableau d'une catégorie à partir de la photographie des listes. */
export function buildRows(s: AssetsSnapshot, category: CategoryKey): AssetRow[] {
  switch (category) {
    case 'zones':
      return s.zones.map((z) =>
        makeRow(s, category, z, null, {
          description: z.description,
          pipes: s.pipes.filter((x) => x.zoneId === z.id).length,
          tanks: s.tanks.filter((x) => x.zoneId === z.id).length,
          pumps: s.pumps.filter((x) => x.zoneId === z.id).length,
          sensors: s.sensors.filter((x) => x.zoneId === z.id).length,
        }),
      );
    case 'pipes':
      return s.pipes.map((p) =>
        makeRow(s, category, p, p.zoneId, {
          zone: zoneName(s, p.zoneId),
          lengthM: p.lengthM,
          diameterMm: p.diameterMm,
          sensors: sensorsOfAsset(s, 'PIPE', p.id).length,
        }),
      );
    case 'tanks':
      return s.tanks.map((t) =>
        makeRow(s, category, t, t.zoneId, {
          zone: zoneName(s, t.zoneId),
          capacityM3: t.capacityM3,
          sensors: sensorsOfAsset(s, 'TANK', t.id).length,
        }),
      );
    case 'pumps':
      return s.pumps.map((p) =>
        makeRow(s, category, p, p.zoneId, {
          zone: zoneName(s, p.zoneId),
          sensors: sensorsOfAsset(s, 'PUMP', p.id).length,
        }),
      );
    case 'sensors':
      return s.sensors.map((x) =>
        makeRow(s, category, x, x.zoneId, {
          type: SENSOR_TYPE_LABELS[x.type],
          zone: zoneName(s, x.zoneId),
          asset: assetLabelOfSensor(s, x),
        }),
      );
  }
}

export interface AssetFilters {
  query: string;
  /** Limite aux éléments d'une zone (sans effet sur l'onglet des zones). */
  zoneId: number | null;
  /** Capteurs uniquement. */
  sensorType: SensorType | null;
  /** Capteurs uniquement : type d'actif rattaché. */
  assetKind: AssetKind | null;
}

export const NO_FILTERS: AssetFilters = { query: '', zoneId: null, sensorType: null, assetKind: null };

export function hasActiveFilters(f: AssetFilters): boolean {
  return f.query.trim() !== '' || f.zoneId !== null || f.sensorType !== null || f.assetKind !== null;
}

/** Filtres combinables : tous les critères renseignés doivent être satisfaits. */
export function filterRows(rows: AssetRow[], f: AssetFilters): AssetRow[] {
  const q = normalize(f.query.trim());
  return rows.filter((row) => {
    if (q && !row.search.includes(q)) {
      return false;
    }
    if (f.zoneId !== null && row.category !== 'zones' && row.zoneId !== f.zoneId) {
      return false;
    }
    if (row.category === 'sensors') {
      const sensor = row.raw as Sensor;
      if (f.sensorType !== null && sensor.type !== f.sensorType) {
        return false;
      }
      if (f.assetKind !== null && sensor.assetType !== f.assetKind) {
        return false;
      }
    }
    return true;
  });
}

export interface SortState {
  key: string;
  direction: 'asc' | 'desc';
}

const collator = new Intl.Collator('fr', { numeric: true, sensitivity: 'base' });

/** Tri stable ; les valeurs absentes sont toujours placées en fin de liste. */
export function sortRows(rows: AssetRow[], sort: SortState | null): AssetRow[] {
  if (!sort) {
    return rows;
  }
  const factor = sort.direction === 'asc' ? 1 : -1;
  return rows
    .map((row, index) => ({ row, index }))
    .sort((a, b) => {
      const x = a.row.cells[sort.key] ?? null;
      const y = b.row.cells[sort.key] ?? null;
      if (x === null && y === null) {
        return a.index - b.index;
      }
      if (x === null) {
        return 1;
      }
      if (y === null) {
        return -1;
      }
      const result =
        typeof x === 'number' && typeof y === 'number' ? x - y : collator.compare(String(x), String(y));
      return result !== 0 ? result * factor : a.index - b.index;
    })
    .map((entry) => entry.row);
}

/** Passe de « aucun tri » à croissant, puis décroissant, puis retour à aucun tri. */
export function nextSort(current: SortState | null, key: string): SortState | null {
  if (!current || current.key !== key) {
    return { key, direction: 'asc' };
  }
  return current.direction === 'asc' ? { key, direction: 'desc' } : null;
}

export interface Page<T> {
  items: T[];
  /** Page effectivement affichée (ramenée dans les bornes), à partir de 1. */
  page: number;
  pageCount: number;
  total: number;
  /** Rang du premier et du dernier élément affichés (0 si la liste est vide). */
  from: number;
  to: number;
}

export function paginate<T>(items: T[], page: number, pageSize: number): Page<T> {
  const total = items.length;
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const current = Math.min(Math.max(1, Math.floor(page) || 1), pageCount);
  const start = (current - 1) * pageSize;
  const slice = items.slice(start, start + pageSize);
  return {
    items: slice,
    page: current,
    pageCount,
    total,
    from: total === 0 ? 0 : start + 1,
    to: start + slice.length,
  };
}

/* ------------------------------------------------------------------ */
/*  Fiche détaillée et relations                                       */
/* ------------------------------------------------------------------ */

export interface RelationItem {
  /** Lien vers l'actif ; nul quand la cible n'existe plus. */
  ref: AssetRef | null;
  label: string;
  /** Capteurs rattachés à cet équipement (relation zone → équipement → capteur). */
  children: AssetRef[];
}

export interface RelationGroup {
  key: string;
  title: string;
  icon: string;
  items: RelationItem[];
}

const refOf = (category: CategoryKey, a: { id: number; name: string }): AssetRef => ({ category, id: a.id, name: a.name });

function equipmentItems(s: AssetsSnapshot, category: 'pipes' | 'tanks' | 'pumps', zoneId: number): RelationItem[] {
  const kind = SENSOR_HOST_KIND[category] as AssetKind;
  return s[category]
    .filter((a) => a.zoneId === zoneId)
    .map((a) => ({
      ref: refOf(category, a),
      label: a.name,
      children: sensorsOfAsset(s, kind, a.id).map((x) => refOf('sensors', x)),
    }));
}

/**
 * Relations d'un actif, calculées uniquement à partir des listes de l'API :
 * - zone : ses conduites, réservoirs et pompes, chacun avec ses capteurs, puis les capteurs de la zone
 *   rattachés à un équipement d'une autre zone ou disparu ;
 * - conduite, réservoir, pompe : sa zone et ses capteurs ;
 * - capteur : sa zone et l'actif auquel il est rattaché.
 */
export function relationsOf(s: AssetsSnapshot, category: CategoryKey, id: number): RelationGroup[] {
  const groups: RelationGroup[] = [];
  const zoneItem = (zoneId: number): RelationItem => {
    const zone = s.zones.find((z) => z.id === zoneId);
    return { ref: zone ? refOf('zones', zone) : null, label: zoneName(s, zoneId), children: [] };
  };

  if (category === 'zones') {
    const meta: Array<['pipes' | 'tanks' | 'pumps', string, string]> = [
      ['pipes', 'Conduites', 'plumbing'],
      ['tanks', 'Réservoirs', 'water'],
      ['pumps', 'Pompes', 'water_pump'],
    ];
    const shown = new Set<number>();
    for (const [key, title, icon] of meta) {
      const items = equipmentItems(s, key, id);
      items.forEach((item) => item.children.forEach((c) => shown.add(c.id)));
      groups.push({ key, title, icon, items });
    }
    const loose = s.sensors.filter((x) => x.zoneId === id && !shown.has(x.id));
    groups.push({
      key: 'loose-sensors',
      title: 'Capteurs rattachés à un équipement hors zone',
      icon: 'sensors',
      items: loose.map((x) => ({ ref: refOf('sensors', x), label: `${x.name} — ${assetLabelOfSensor(s, x)}`, children: [] })),
    });
    return groups.filter((g) => g.items.length > 0);
  }

  if (category === 'sensors') {
    const sensor = s.sensors.find((x) => x.id === id);
    if (!sensor) {
      return [];
    }
    groups.push({ key: 'zone', title: 'Zone', icon: 'map', items: [zoneItem(sensor.zoneId)] });
    const asset = assetOfSensor(s, sensor);
    groups.push({
      key: 'asset',
      title: 'Actif rattaché',
      icon: 'link',
      items: [
        {
          ref: asset ? refOf(ASSET_KIND_CATEGORY[sensor.assetType], asset) : null,
          label: assetLabelOfSensor(s, sensor),
          children: [],
        },
      ],
    });
    return groups;
  }

  const own = (s[category] as Array<Pipe | Tank | Pump>).find((a) => a.id === id);
  if (!own) {
    return [];
  }
  groups.push({ key: 'zone', title: 'Zone', icon: 'map', items: [zoneItem(own.zoneId)] });
  const sensors = dependentsOf(s, category, id);
  if (sensors.length > 0) {
    groups.push({
      key: 'sensors',
      title: 'Capteurs rattachés',
      icon: 'sensors',
      items: sensors.map((r) => ({ ref: r, label: r.name, children: [] })),
    });
  }
  return groups;
}

export interface DetailField {
  label: string;
  value: CellValue;
}

/** Propriétés affichées dans la fiche d'un actif : les colonnes du tableau, avec leurs valeurs. */
export function detailFields(s: AssetsSnapshot, category: CategoryKey, id: number): DetailField[] {
  const row = buildRows(s, category).find((r) => r.id === id);
  return row ? COLUMNS[category].map((col) => ({ label: col.label, value: row.cells[col.key] ?? null })) : [];
}

/* ------------------------------------------------------------------ */
/*  Erreurs HTTP                                                       */
/* ------------------------------------------------------------------ */

export type AssetsErrorKind = 'network' | 'auth' | 'forbidden' | 'not-found' | 'validation' | 'conflict' | 'server' | 'unknown';

export interface AssetsError {
  kind: AssetsErrorKind;
  status: number;
  /** Message compréhensible, prêt à être affiché à l'utilisateur. */
  message: string;
}

/** Raisons de refus (400) renvoyées par l'assets-service, traduites en conseils d'action. */
const BAD_REQUEST_REASONS: Record<string, string> = {
  'zone inexistante': "La zone choisie n'existe pas ou plus. Actualisez la liste, puis sélectionnez une zone valide.",
  'actif inexistant': "L'actif rattaché n'existe pas ou plus. Actualisez la liste, puis sélectionnez un actif valide.",
};

/** Conseils affichés quand le serveur répond 400 sans raison exploitable. */
const BAD_REQUEST_HINTS: Record<CategoryKey, string> = {
  zones: 'Le nom de la zone est obligatoire.',
  pipes: 'Vérifiez le nom, la zone (obligatoire et existante), la longueur et le diamètre (strictement positifs).',
  tanks: 'Vérifiez le nom, la zone (obligatoire et existante) et la capacité (strictement positive).',
  pumps: 'Vérifiez le nom et la zone (obligatoire et existante).',
  sensors: 'Vérifiez le nom, le type, la zone et l’actif rattaché : la zone et l’actif doivent exister.',
};

function serverMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const m = (error as { message: unknown }).message;
    return typeof m === 'string' ? m.trim() : '';
  }
  return '';
}

/**
 * Transforme n'importe quelle erreur d'appel HTTP en message lisible.
 * L'assets-service ne renvoie pas toujours de texte d'erreur (Spring le masque par défaut) :
 * le message se déduit donc d'abord du code HTTP, et du texte du serveur quand il existe.
 */
export function toAssetsError(err: unknown, category: CategoryKey): AssetsError {
  const raw = err as { status?: unknown; error?: unknown } | null;
  const status = typeof raw?.status === 'number' ? raw.status : 0;

  if (status === 0) {
    return { kind: 'network', status, message: "Le service d'actifs est injoignable. Vérifiez la connexion puis réessayez." };
  }
  if (status === 400) {
    const known = BAD_REQUEST_REASONS[serverMessage(raw?.error).toLowerCase()];
    if (known) {
      return { kind: 'validation', status, message: known };
    }
    return { kind: 'validation', status, message: `La demande a été refusée par le serveur (400). ${BAD_REQUEST_HINTS[category]}` };
  }
  if (status === 401) {
    return { kind: 'auth', status, message: 'Votre session a expiré. Reconnectez-vous pour continuer.' };
  }
  if (status === 403) {
    return { kind: 'forbidden', status, message: 'Action refusée : le rôle administrateur est requis pour gérer les actifs.' };
  }
  if (status === 404) {
    return { kind: 'not-found', status, message: "Cet élément n'existe plus (il a peut-être déjà été supprimé). La liste va être actualisée." };
  }
  if (status === 409) {
    return { kind: 'conflict', status, message: "L'opération est en conflit avec l'état actuel des données. Actualisez puis réessayez." };
  }
  if (status >= 500) {
    return { kind: 'server', status, message: "Le service d'actifs a rencontré une erreur. Réessayez dans un instant." };
  }
  return { kind: 'unknown', status, message: `Erreur inattendue (code ${status}).` };
}

/** Pour les tests et l'affichage : libellé de catégorie d'un type d'actif de capteur. */
export function categoryOfKind(kind: AssetKind): CategoryKey {
  return ASSET_KIND_CATEGORY[kind];
}

export const CATEGORY_KEYS: readonly CategoryKey[] = CATEGORIES.map((c) => c.key);
