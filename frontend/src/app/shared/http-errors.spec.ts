import { describe, expect, it } from 'vitest';
import { describeHttpError } from './http-errors';

describe('describeHttpError', () => {
  it('service injoignable (code 0) : réessayable', () => {
    const e = describeHttpError({ status: 0 }, 'les anomalies');
    expect(e.kind).toBe('network');
    expect(e.retryable).toBe(true);
    expect(e.message).toContain('les anomalies');
  });

  it('401 : session expirée, non réessayable', () => {
    const e = describeHttpError({ status: 401 });
    expect(e.kind).toBe('auth');
    expect(e.retryable).toBe(false);
    expect(e.message).toContain('session');
  });

  it('403 : accès refusé selon le rôle', () => {
    const e = describeHttpError({ status: 403 }, 'les anomalies');
    expect(e.kind).toBe('forbidden');
    expect(e.message).toContain('rôle');
  });

  it('404 et 400', () => {
    expect(describeHttpError({ status: 404 }).kind).toBe('not-found');
    const bad = describeHttpError({ status: 400, error: { message: 'size trop grand' } });
    expect(bad.kind).toBe('invalid');
    expect(bad.message).toContain('size trop grand');
    expect(describeHttpError({ status: 400, error: null }).message.includes('()')).toBe(false);
  });

  it('5xx : erreur serveur réessayable', () => {
    const e = describeHttpError({ status: 503 });
    expect(e.kind).toBe('server');
    expect(e.retryable).toBe(true);
  });

  it('erreur inattendue ou valeur absurde : ne plante pas', () => {
    expect(describeHttpError({ status: 418 }).kind).toBe('unknown');
    expect(describeHttpError(undefined).kind).toBe('network');
    expect(describeHttpError('boom').kind).toBe('network');
  });
});
