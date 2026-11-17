export interface AppConfig {
  issuer: string;
  clientId: string;
  assetsBaseUrl: string;
  telemetryBaseUrl: string;
  sensorDataMode: 'mock' | 'api';
  sensorSilenceThresholdSeconds: number;
  sensorLookbackSeconds: number;
}

// Valeurs par défaut valables en local si config.json est absent.
const DEFAULTS: AppConfig = {
  issuer: 'http://localhost:8180/realms/aquaops',
  clientId: 'aquaops-frontend',
  assetsBaseUrl: '/api/assets',
  telemetryBaseUrl: '/api/telemetry',
  sensorDataMode: 'mock',
  sensorSilenceThresholdSeconds: 10,
  sensorLookbackSeconds: 120,
};

/** Lit /config.json au démarrage (et non à la construction de l'image). */
export async function loadAppConfig(): Promise<AppConfig> {
  try {
    const res = await fetch('/config.json', { cache: 'no-store' });
    if (!res.ok) {
      return DEFAULTS;
    }
    const config = { ...DEFAULTS, ...(await res.json()) };
    return {
      ...config,
      sensorDataMode: config.sensorDataMode === 'api' ? 'api' : 'mock',
      sensorSilenceThresholdSeconds: positiveNumber(
        config.sensorSilenceThresholdSeconds,
        DEFAULTS.sensorSilenceThresholdSeconds,
      ),
      sensorLookbackSeconds: positiveNumber(config.sensorLookbackSeconds, DEFAULTS.sensorLookbackSeconds),
    };
  } catch {
    return DEFAULTS;
  }
}

function positiveNumber(value: unknown, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}