import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadAppConfig } from './app-config';

const DEFAUTS = {
  issuer: 'http://localhost:8180/realms/aquaops',
  clientId: 'aquaops-frontend',
  assetsBaseUrl: '/api/assets',
  telemetryBaseUrl: '/api/telemetry',
  sensorDataMode: 'mock',
  sensorSilenceThresholdSeconds: 10,
  sensorLookbackSeconds: 120,
};

describe('loadAppConfig', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('retourne les valeurs de config.json quand la réponse est valide', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        issuer: 'https://kc.exemple/realms/aquaops',
        clientId: 'autre-client',
        sensorDataMode: 'api',
        sensorSilenceThresholdSeconds: '15',
      }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const cfg = await loadAppConfig();

    expect(cfg).toEqual({
      ...DEFAUTS,
      issuer: 'https://kc.exemple/realms/aquaops',
      clientId: 'autre-client',
      sensorDataMode: 'api',
      sensorSilenceThresholdSeconds: 15,
    });
    expect(fetchMock).toHaveBeenCalledWith('/config.json', { cache: 'no-store' });
  });

  it('complète avec les valeurs par défaut si une clé manque', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ issuer: 'https://kc.exemple/realms/aquaops' }),
    }));

    const cfg = await loadAppConfig();

    expect(cfg.issuer).toBe('https://kc.exemple/realms/aquaops');
    expect(cfg.clientId).toBe(DEFAUTS.clientId);
    expect(cfg.assetsBaseUrl).toBe(DEFAUTS.assetsBaseUrl);
  });

  it('utilise les valeurs par défaut pour un seuil invalide', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ sensorSilenceThresholdSeconds: -2, sensorLookbackSeconds: 'invalide' }),
    }));

    const cfg = await loadAppConfig();

    expect(cfg.sensorSilenceThresholdSeconds).toBe(10);
    expect(cfg.sensorLookbackSeconds).toBe(120);
  });

  it('retourne les valeurs par défaut si fetch échoue', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('réseau')));

    expect(await loadAppConfig()).toEqual(DEFAUTS);
  });

  it('retourne les valeurs par défaut si la réponse n’est pas OK (404)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }));

    expect(await loadAppConfig()).toEqual(DEFAUTS);
  });

  it('retourne les valeurs par défaut si le JSON est invalide', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => {
        throw new SyntaxError('JSON invalide');
      },
    }));

    expect(await loadAppConfig()).toEqual(DEFAUTS);
  });
});