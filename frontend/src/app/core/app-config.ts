export interface AppConfig {
  issuer: string;
  clientId: string;
}

// Valeurs par défaut valables en local si config.json est absent.
const DEFAULTS: AppConfig = {
  issuer: 'http://localhost:8180/realms/aquaops',
  clientId: 'aquaops-frontend',
};

/** Lit /config.json au démarrage (et non à la construction de l'image). */
export async function loadAppConfig(): Promise<AppConfig> {
  try {
    const res = await fetch('/config.json', { cache: 'no-store' });
    if (!res.ok) {
      return DEFAULTS;
    }
    return { ...DEFAULTS, ...(await res.json()) };
  } catch {
    return DEFAULTS;
  }
}