import { describe, expect, it } from 'vitest';
import { THRESHOLD_FIELDS, ThresholdValues, ThresholdsResponse } from './thresholds.model';
import { buildRows, formatValue, parseServerErrors, sameValues, validateAll, validateField } from './thresholds.rules';

const defaults: ThresholdValues = { leakMinPressure: 2, leakMaxFlow: 40, leakDurationSeconds: 10, silenceThresholdSeconds: 10, tankMinLevel: 2 };
const field = (key: string) => THRESHOLD_FIELDS.find((f) => f.key === key)!;

describe('validateField (mêmes règles que le serveur)', () => {
  it('un champ vide est refusé', () => {
    expect(validateField(field('leakMaxFlow'), null)).toBe('Ce seuil est obligatoire.');
    expect(validateField(field('leakMaxFlow'), Number.NaN)).toBe('Ce seuil est obligatoire.');
  });

  it('pression et niveau : 0 autorisé, négatif refusé, maximum respecté', () => {
    expect(validateField(field('leakMinPressure'), 0)).toBeNull();
    expect(validateField(field('leakMinPressure'), -0.1)).toBe('Minimum 0.');
    expect(validateField(field('leakMinPressure'), 100)).toBeNull();
    expect(validateField(field('leakMinPressure'), 100.1)).toBe('Maximum 100.');
    expect(validateField(field('tankMinLevel'), -1)).toBe('Minimum 0.');
  });

  it('débit : strictement positif', () => {
    expect(validateField(field('leakMaxFlow'), 0)).toBe('Ce seuil doit être strictement positif.');
    expect(validateField(field('leakMaxFlow'), 0.5)).toBeNull();
    expect(validateField(field('leakMaxFlow'), 100001)).toBe('Maximum 100000.');
  });

  it('durées : entiers de 1 à 3600', () => {
    expect(validateField(field('leakDurationSeconds'), 0)).toBe('Minimum 1.');
    expect(validateField(field('leakDurationSeconds'), 10.5)).toBe('Saisissez un nombre entier.');
    expect(validateField(field('silenceThresholdSeconds'), 3600)).toBeNull();
    expect(validateField(field('silenceThresholdSeconds'), 3601)).toBe('Maximum 3600.');
  });

  it('valeur infinie refusée', () => {
    expect(validateField(field('leakMaxFlow'), Number.POSITIVE_INFINITY)).toBe('Ce seuil doit être un nombre.');
  });
});

describe('validateAll', () => {
  it('aucune erreur pour des valeurs correctes', () => {
    expect(validateAll(defaults)).toEqual({});
  });

  it('une erreur par champ invalide, toutes à la fois', () => {
    const errors = validateAll({ leakMinPressure: -1, leakMaxFlow: 0, leakDurationSeconds: 0, silenceThresholdSeconds: 5000, tankMinLevel: null });
    expect(Object.keys(errors).sort()).toEqual(['leakDurationSeconds', 'leakMaxFlow', 'leakMinPressure', 'silenceThresholdSeconds', 'tankMinLevel']);
  });
});

describe('buildRows', () => {
  const response: ThresholdsResponse = {
    defaults,
    zones: [
      { zoneId: 2, custom: true, thresholds: { ...defaults, leakMaxFlow: 55 }, updatedAt: '2026-12-10T10:00:00Z', updatedBy: 'admin' },
      { zoneId: 9, custom: true, thresholds: { ...defaults, tankMinLevel: 4 }, updatedAt: null, updatedBy: null },
    ],
  };
  const zones = [{ id: 1, name: 'Zone Nord' }, { id: 2, name: 'Zone Sud' }];

  it('une ligne par zone : seuils personnalisés ou valeurs par défaut', () => {
    const rows = buildRows(zones, response);
    expect(rows.find((r) => r.zoneId === 1)).toMatchObject({ name: 'Zone Nord', custom: false });
    expect(rows.find((r) => r.zoneId === 1)!.thresholds).toEqual(defaults);
    expect(rows.find((r) => r.zoneId === 2)).toMatchObject({ custom: true, updatedBy: 'admin' });
    expect(rows.find((r) => r.zoneId === 2)!.thresholds.leakMaxFlow).toBe(55);
  });

  it('garde une zone qui a des seuils mais que le service des actifs ne connaît plus', () => {
    const orphan = buildRows(zones, response).find((r) => r.zoneId === 9)!;
    expect(orphan.name).toBe('Zone 9');
    expect(orphan.custom).toBe(true);
  });

  it('trie par identifiant et gère l’absence de zones', () => {
    expect(buildRows(zones, response).map((r) => r.zoneId)).toEqual([1, 2, 9]);
    expect(buildRows([], { defaults, zones: [] })).toEqual([]);
  });
});

describe('divers', () => {
  it('sameValues compare tous les seuils', () => {
    expect(sameValues(defaults, { ...defaults })).toBe(true);
    expect(sameValues(defaults, { ...defaults, tankMinLevel: 3 })).toBe(false);
  });

  it('lit les erreurs de champ renvoyées par le serveur et ignore le reste', () => {
    const errors = parseServerErrors({ error: { errors: [{ field: 'leakMaxFlow', message: 'Ce seuil doit etre strictement positif.' }, { field: 'inconnu', message: 'x' }, { field: 5 }] } });
    expect(errors).toEqual({ leakMaxFlow: 'Ce seuil doit etre strictement positif.' });
    expect(parseServerErrors(null)).toEqual({});
    expect(parseServerErrors({ error: 'texte' })).toEqual({});
  });

  it('formate une valeur avec son unité', () => {
    expect(formatValue(field('leakMinPressure'), 2.5).endsWith(' bar')).toBe(true);
    expect(formatValue(field('leakMaxFlow'), 40)).toBe('40');
  });
});
