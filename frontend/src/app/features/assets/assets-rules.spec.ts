import { describe, expect, it } from 'vitest';
import {
  NO_FILTERS,
  assetLabelOfSensor,
  buildRows,
  countByCategory,
  deleteVerdict,
  detailFields,
  dependentsOf,
  filterRows,
  hasActiveFilters,
  networkTotals,
  nextSort,
  relationsOf,
  paginate,
  sensorsByType,
  sortRows,
  toAssetsError,
  totalAssets,
  zoneName,
} from './assets-rules';
import { AssetsSnapshot } from './assets.model';

/** Jeu de données calqué sur deploy/demo/demo-assets.sql, avec une troisième zone vide. */
const snapshot: AssetsSnapshot = {
  zones: [
    { id: 1, name: 'Zone 1', description: 'Zone de démonstration 1' },
    { id: 2, name: 'Zone 2', description: null },
    { id: 3, name: 'Zone vide', description: null },
  ],
  pipes: [
    { id: 1, name: 'Conduite Z1', zoneId: 1, lengthM: 500, diameterMm: 200 },
    { id: 2, name: 'Conduite Z2', zoneId: 2, lengthM: null, diameterMm: 150 },
  ],
  tanks: [{ id: 1, name: 'Réservoir Z1', zoneId: 1, capacityM3: 1000 }],
  pumps: [
    { id: 1, name: 'Pompe Z1', zoneId: 1 },
    { id: 2, name: 'Pompe Z2', zoneId: 2 },
  ],
  sensors: [
    { id: 1, name: 'Pression Z1', type: 'PRESSURE', zoneId: 1, assetType: 'PIPE', assetId: 1 },
    { id: 2, name: 'Débit Z1', type: 'FLOW', zoneId: 1, assetType: 'PIPE', assetId: 1 },
    { id: 3, name: 'Niveau Z1', type: 'LEVEL', zoneId: 1, assetType: 'TANK', assetId: 1 },
    { id: 4, name: 'Pompe Z1 état', type: 'PUMP_STATE', zoneId: 1, assetType: 'PUMP', assetId: 1 },
    { id: 5, name: 'Pression Z2', type: 'PRESSURE', zoneId: 2, assetType: 'PIPE', assetId: 2 },
  ],
};

describe('indicateurs', () => {
  it('compte chaque catégorie et le total à partir des listes', () => {
    expect(countByCategory(snapshot)).toEqual({ zones: 3, pipes: 2, tanks: 1, pumps: 2, sensors: 5 });
    expect(totalAssets(snapshot)).toBe(13);
  });

  it('additionne longueurs et capacités en ignorant les valeurs absentes', () => {
    expect(networkTotals(snapshot)).toEqual({ pipeLengthM: 500, tankCapacityM3: 1000 });
  });

  it('répartit les capteurs par type', () => {
    expect(sensorsByType(snapshot)).toEqual({ PRESSURE: 2, FLOW: 1, LEVEL: 1, PUMP_STATE: 1 });
  });
});

describe('relations', () => {
  it('donne le nom de la zone, ou signale une zone introuvable', () => {
    expect(zoneName(snapshot, 2)).toBe('Zone 2');
    expect(zoneName(snapshot, 99)).toBe('Zone 99 (introuvable)');
    expect(zoneName(snapshot, null)).toBe('—');
  });

  it('décrit l’actif d’un capteur, même s’il a disparu', () => {
    expect(assetLabelOfSensor(snapshot, snapshot.sensors[0])).toBe('Conduite · Conduite Z1');
    expect(assetLabelOfSensor(snapshot, { ...snapshot.sensors[0], assetId: 42 })).toBe('Conduite n° 42 (introuvable)');
  });

  it('une zone dépend de tous ses équipements et capteurs', () => {
    const deps = dependentsOf(snapshot, 'zones', 1);
    expect(deps.map((d) => `${d.category}:${d.id}`)).toEqual([
      'pipes:1', 'tanks:1', 'pumps:1', 'sensors:1', 'sensors:2', 'sensors:3', 'sensors:4',
    ]);
  });

  it('une conduite dépend des capteurs qui y sont rattachés, pas de ceux d’un réservoir de même numéro', () => {
    expect(dependentsOf(snapshot, 'pipes', 1).map((d) => d.id)).toEqual([1, 2]);
    expect(dependentsOf(snapshot, 'tanks', 1).map((d) => d.id)).toEqual([3]);
    expect(dependentsOf(snapshot, 'sensors', 1)).toEqual([]);
  });
});

describe('blocage de suppression (décision FE-3)', () => {
  it('refuse de supprimer une zone qui contient encore des éléments', () => {
    const verdict = deleteVerdict(snapshot, 'zones', 1);
    expect(verdict.allowed).toBe(false);
    expect(verdict.blockers).toHaveLength(7);
    expect(verdict.reason).toContain('7');
  });

  it('refuse de supprimer une conduite équipée de capteurs', () => {
    expect(deleteVerdict(snapshot, 'pipes', 1).allowed).toBe(false);
  });

  it('autorise une zone vide, une pompe sans capteur et tout capteur', () => {
    expect(deleteVerdict(snapshot, 'zones', 3)).toEqual({ allowed: true, blockers: [], reason: null });
    expect(deleteVerdict(snapshot, 'pumps', 2).allowed).toBe(true);
    expect(deleteVerdict(snapshot, 'sensors', 3).allowed).toBe(true);
  });
});

describe('lignes, filtres et tri', () => {
  it('construit les colonnes propres aux conduites avec leurs capteurs', () => {
    const row = buildRows(snapshot, 'pipes')[0];
    expect(row.cells).toMatchObject({ id: 1, name: 'Conduite Z1', zone: 'Zone 1', lengthM: 500, diameterMm: 200, sensors: 2 });
  });

  it('compte les équipements de chaque zone', () => {
    const zone1 = buildRows(snapshot, 'zones')[0];
    expect(zone1.cells).toMatchObject({ pipes: 1, tanks: 1, pumps: 1, sensors: 4 });
  });

  it('la recherche porte sur le nom, la zone, le type et l’actif, sans tenir compte de la casse ni des accents', () => {
    const rows = buildRows(snapshot, 'sensors');
    expect(filterRows(rows, { ...NO_FILTERS, query: 'DÉBIT' }).map((r) => r.id)).toEqual([2]);
    expect(filterRows(rows, { ...NO_FILTERS, query: 'debit' }).map((r) => r.id)).toEqual([2]);
    expect(filterRows(rows, { ...NO_FILTERS, query: 'zone 2' }).map((r) => r.id)).toEqual([5]);
    expect(filterRows(rows, { ...NO_FILTERS, query: 'reservoir' }).map((r) => r.id)).toEqual([3]);
  });

  it('combine zone, type de capteur et type d’actif', () => {
    const rows = buildRows(snapshot, 'sensors');
    expect(filterRows(rows, { ...NO_FILTERS, zoneId: 1 }).map((r) => r.id)).toEqual([1, 2, 3, 4]);
    expect(filterRows(rows, { ...NO_FILTERS, zoneId: 1, sensorType: 'PRESSURE' }).map((r) => r.id)).toEqual([1]);
    expect(filterRows(rows, { ...NO_FILTERS, assetKind: 'PIPE', sensorType: 'PRESSURE' }).map((r) => r.id)).toEqual([1, 5]);
  });

  it('le filtre de zone n’a pas d’effet sur l’onglet des zones', () => {
    expect(filterRows(buildRows(snapshot, 'zones'), { ...NO_FILTERS, zoneId: 1 })).toHaveLength(3);
  });

  it('détecte les filtres actifs', () => {
    expect(hasActiveFilters(NO_FILTERS)).toBe(false);
    expect(hasActiveFilters({ ...NO_FILTERS, query: '  ' })).toBe(false);
    expect(hasActiveFilters({ ...NO_FILTERS, zoneId: 2 })).toBe(true);
  });

  it('trie en nombres et place les valeurs absentes à la fin dans les deux sens', () => {
    const rows = buildRows(snapshot, 'pipes');
    expect(sortRows(rows, { key: 'lengthM', direction: 'asc' }).map((r) => r.id)).toEqual([1, 2]);
    expect(sortRows(rows, { key: 'lengthM', direction: 'desc' }).map((r) => r.id)).toEqual([1, 2]);
    expect(sortRows(rows, { key: 'diameterMm', direction: 'asc' }).map((r) => r.id)).toEqual([2, 1]);
    expect(sortRows(rows, { key: 'diameterMm', direction: 'desc' }).map((r) => r.id)).toEqual([1, 2]);
  });

  it('trie les textes selon l’ordre français et ne modifie pas la liste d’origine', () => {
    const rows = buildRows(snapshot, 'sensors');
    const sorted = sortRows(rows, { key: 'name', direction: 'asc' }).map((r) => r.name);
    expect(sorted[0]).toBe('Débit Z1');
    expect(rows[0].name).toBe('Pression Z1');
    expect(sortRows(rows, null)).toBe(rows);
  });

  it('le clic sur une colonne enchaîne croissant, décroissant, puis aucun tri', () => {
    const asc = nextSort(null, 'name');
    expect(asc).toEqual({ key: 'name', direction: 'asc' });
    const desc = nextSort(asc, 'name');
    expect(desc).toEqual({ key: 'name', direction: 'desc' });
    expect(nextSort(desc, 'name')).toBeNull();
    expect(nextSort(desc, 'zone')).toEqual({ key: 'zone', direction: 'asc' });
  });
});

describe('pagination', () => {
  const items = Array.from({ length: 25 }, (_, i) => i + 1);

  it('découpe en pages et indique la plage affichée', () => {
    const p = paginate(items, 3, 10);
    expect(p.items).toEqual([21, 22, 23, 24, 25]);
    expect(p).toMatchObject({ page: 3, pageCount: 3, total: 25, from: 21, to: 25 });
  });

  it('ramène une page hors bornes dans les bornes', () => {
    expect(paginate(items, 99, 10).page).toBe(3);
    expect(paginate(items, 0, 10).page).toBe(1);
  });

  it('gère la liste vide', () => {
    expect(paginate([], 1, 10)).toMatchObject({ items: [], page: 1, pageCount: 1, total: 0, from: 0, to: 0 });
  });
});

describe('erreurs HTTP', () => {
  it('traduit la raison « Zone inexistante » du capteur refusé (400)', () => {
    const error = toAssetsError({ status: 400, error: { message: 'Zone inexistante' } }, 'sensors');
    expect(error.kind).toBe('validation');
    expect(error.message).toContain("La zone choisie n'existe pas");
  });

  it('traduit la raison « Actif inexistant »', () => {
    expect(toAssetsError({ status: 400, error: { message: 'Actif inexistant' } }, 'sensors').message).toContain("L'actif rattaché");
  });

  it('un 400 sans texte du serveur reste compréhensible (Spring masque le message par défaut)', () => {
    const error = toAssetsError({ status: 400, error: { status: 400, error: 'Bad Request' } }, 'sensors');
    expect(error.kind).toBe('validation');
    expect(error.message).toContain('la zone et l’actif doivent exister');
    expect(toAssetsError({ status: 400, error: null }, 'pipes').message).toContain('zone');
  });

  it('distingue réseau, session, droits, absence, conflit et erreur serveur', () => {
    expect(toAssetsError({ status: 0 }, 'zones').kind).toBe('network');
    expect(toAssetsError({ status: 401 }, 'zones').kind).toBe('auth');
    expect(toAssetsError({ status: 403 }, 'zones').message).toContain('administrateur');
    expect(toAssetsError({ status: 404 }, 'zones').kind).toBe('not-found');
    expect(toAssetsError({ status: 409 }, 'zones').kind).toBe('conflict');
    expect(toAssetsError({ status: 503 }, 'zones').kind).toBe('server');
  });

  it('ne plante pas sur une erreur inattendue', () => {
    expect(toAssetsError(undefined, 'zones').kind).toBe('network');
    expect(toAssetsError('boom', 'zones').kind).toBe('network');
    expect(toAssetsError({ status: 418 }, 'zones').kind).toBe('unknown');
  });
});

describe('relations et fiche détaillée', () => {
  const titles = (category: Parameters<typeof relationsOf>[1], id: number) =>
    relationsOf(snapshot, category, id).map((g) => `${g.key}:${g.items.length}`);

  it('une zone liste ses équipements avec leurs capteurs', () => {
    const groups = relationsOf(snapshot, 'zones', 1);
    expect(groups.map((g) => g.key)).toEqual(['pipes', 'tanks', 'pumps']);
    const pipe = groups[0].items[0];
    expect(pipe.label).toBe('Conduite Z1');
    expect(pipe.children.map((c) => c.name)).toEqual(['Pression Z1', 'Débit Z1']);
  });

  it('une zone vide n’a aucune relation', () => {
    expect(relationsOf(snapshot, 'zones', 3)).toEqual([]);
  });

  it('signale les capteurs d’une zone rattachés à un équipement d’une autre zone', () => {
    const odd: AssetsSnapshot = {
      ...snapshot,
      sensors: [...snapshot.sensors, { id: 9, name: 'Capteur croisé', type: 'FLOW', zoneId: 3, assetType: 'PIPE', assetId: 1 }],
    };
    const groups = relationsOf(odd, 'zones', 3);
    expect(groups.map((g) => g.key)).toEqual(['loose-sensors']);
    expect(groups[0].items[0].label).toContain('Conduite · Conduite Z1');
  });

  it('une conduite montre sa zone et ses capteurs', () => {
    expect(titles('pipes', 1)).toEqual(['zone:1', 'sensors:2']);
    expect(titles('pumps', 2)).toEqual(['zone:1']);
  });

  it('un capteur montre sa zone et son actif, ou signale un actif disparu', () => {
    expect(titles('sensors', 3)).toEqual(['zone:1', 'asset:1']);
    expect(relationsOf(snapshot, 'sensors', 3)[1].items[0].ref).toEqual({ category: 'tanks', id: 1, name: 'Réservoir Z1' });
    const broken: AssetsSnapshot = { ...snapshot, tanks: [] };
    const asset = relationsOf(broken, 'sensors', 3)[1].items[0];
    expect(asset.ref).toBeNull();
    expect(asset.label).toContain('introuvable');
  });

  it('renvoie une liste vide pour un actif inconnu', () => {
    expect(relationsOf(snapshot, 'pipes', 99)).toEqual([]);
    expect(detailFields(snapshot, 'pipes', 99)).toEqual([]);
  });

  it('la fiche reprend les colonnes de la catégorie avec les valeurs', () => {
    const fields = detailFields(snapshot, 'pipes', 2);
    expect(fields.map((f) => f.label)).toEqual(['ID', 'Nom', 'Zone', 'Longueur (m)', 'Diamètre (mm)', 'Capteurs']);
    expect(fields[3].value).toBeNull();
    expect(fields[4].value).toBe(150);
  });
});
