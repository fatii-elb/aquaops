import { ZoneInfo } from '../incidents/incident.model';
import {
  THRESHOLD_FIELDS,
  ThresholdField,
  ThresholdKey,
  ThresholdValues,
  ThresholdsResponse,
} from './thresholds.model';

export type FieldErrors = Partial<Record<ThresholdKey, string>>;

/** Message d'erreur d'un champ, ou nul s'il est valide. Mêmes règles que le serveur. */
export function validateField(field: ThresholdField, value: number | null | undefined): string | null {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return 'Ce seuil est obligatoire.';
  }
  if (!Number.isFinite(value)) {
    return 'Ce seuil doit être un nombre.';
  }
  if (field.integer && !Number.isInteger(value)) {
    return 'Saisissez un nombre entier.';
  }
  if (field.minIncluded ? value < field.min : value <= field.min) {
    return field.minIncluded ? `Minimum ${field.min}.` : 'Ce seuil doit être strictement positif.';
  }
  if (value > field.max) {
    return `Maximum ${field.max}.`;
  }
  return null;
}

export function validateAll(values: Partial<Record<ThresholdKey, number | null>>): FieldErrors {
  const errors: FieldErrors = {};
  for (const field of THRESHOLD_FIELDS) {
    const message = validateField(field, values[field.key]);
    if (message) {
      errors[field.key] = message;
    }
  }
  return errors;
}

export interface ThresholdRow {
  zoneId: number;
  name: string;
  custom: boolean;
  thresholds: ThresholdValues;
  updatedAt: string | null;
  updatedBy: string | null;
}

/**
 * Une ligne par zone connue du service des actifs, avec ses seuils personnalisés ou, à défaut, les valeurs par défaut.
 * Une zone qui a des seuils mais que le service des actifs ne connaît plus reste affichée (« Zone N »).
 */
export function buildRows(zones: ZoneInfo[], response: ThresholdsResponse): ThresholdRow[] {
  const custom = new Map(response.zones.map((z) => [z.zoneId, z]));
  const rows: ThresholdRow[] = zones.map((zone) => {
    const own = custom.get(zone.id);
    return {
      zoneId: zone.id,
      name: zone.name,
      custom: own?.custom ?? false,
      thresholds: own?.thresholds ?? response.defaults,
      updatedAt: own?.updatedAt ?? null,
      updatedBy: own?.updatedBy ?? null,
    };
  });
  const known = new Set(zones.map((z) => z.id));
  for (const own of response.zones) {
    if (!known.has(own.zoneId)) {
      rows.push({ zoneId: own.zoneId, name: `Zone ${own.zoneId}`, custom: own.custom, thresholds: own.thresholds,
        updatedAt: own.updatedAt, updatedBy: own.updatedBy });
    }
  }
  return rows.sort((a, b) => a.zoneId - b.zoneId);
}

export function sameValues(a: ThresholdValues, b: ThresholdValues): boolean {
  return THRESHOLD_FIELDS.every((f) => a[f.key] === b[f.key]);
}

/** Erreurs de champ renvoyées par le serveur en 400 : {"errors":[{"field":"…","message":"…"}]}. */
export function parseServerErrors(err: unknown): FieldErrors {
  const list = (err as { error?: { errors?: unknown } } | null)?.error?.errors;
  const result: FieldErrors = {};
  if (!Array.isArray(list)) {
    return result;
  }
  const keys = new Set<string>(THRESHOLD_FIELDS.map((f) => f.key));
  for (const item of list as Array<{ field?: unknown; message?: unknown }>) {
    if (typeof item?.field === 'string' && keys.has(item.field) && typeof item.message === 'string') {
      result[item.field as ThresholdKey] = item.message;
    }
  }
  return result;
}

export function formatValue(field: ThresholdField, value: number): string {
  const text = value.toLocaleString('fr-FR', { maximumFractionDigits: 2 });
  return field.unit ? `${text} ${field.unit}` : text;
}
