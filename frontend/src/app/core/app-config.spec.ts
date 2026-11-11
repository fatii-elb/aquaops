import { afterEach, describe, expect, it, vi } from 'vitest';
import { loadAppConfig } from './app-config';

const DEFAUTS = {
  issuer: 'http://localhost:8180/realms/aquaops',
  clientId: 'aquaops-frontend',
};

describe('loadAppConfig', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('retourne les valeurs de config.json quand la réponse est valide', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ issuer: 'https://kc.exemple/realms/aquaops', clientId: 'autre-client' }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const cfg = await loadAppConfig();

    expect(cfg).toEqual({ issuer: 'https://kc.exemple/realms/aquaops', clientId: 'autre-client' });
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