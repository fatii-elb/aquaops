import { HttpErrorResponse } from '@angular/common/http';
import { actionLabel, entityLabel, errorMessage, periodToIso } from './audit-format';

describe('audit-format', () => {
  it('traduit les actions connues et laisse les inconnues telles quelles', () => {
    expect(actionLabel('CREATE')).toBe('Création');
    expect(actionLabel('INCIDENT_CLOSED')).toBe('Incident clos');
    expect(actionLabel('INCIDENT_REOUVERT')).toBe('INCIDENT_REOUVERT');
  });

  it('traduit les objets connus et laisse les inconnus tels quels', () => {
    expect(entityLabel('PUMP')).toBe('Pompe');
    expect(entityLabel('AUTRE')).toBe('AUTRE');
  });

  it('periodToIso : début à minuit, fin à 23:59:59.999, heure locale', () => {
    const { from, to } = periodToIso('2026-12-01', '2026-12-02');
    const start = new Date(from as string);
    const end = new Date(to as string);
    expect([start.getFullYear(), start.getMonth(), start.getDate(), start.getHours(), start.getMinutes()]).toEqual([2026, 11, 1, 0, 0]);
    expect([end.getDate(), end.getHours(), end.getMinutes(), end.getSeconds(), end.getMilliseconds()]).toEqual([2, 23, 59, 59, 999]);
  });

  it('periodToIso : un champ vide donne aucune borne', () => {
    expect(periodToIso('', '')).toEqual({});
    expect(periodToIso('2026-12-01', '').to).toBeUndefined();
  });

  it('errorMessage : 403 explique que le journal est réservé', () => {
    const message = errorMessage(new HttpErrorResponse({ status: 403 }));
    expect(message).toContain('403');
    expect(message).toContain('superviseur');
  });

  it('errorMessage : 401, 400, service injoignable et erreur inconnue', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 401 }))).toContain('reconnectez-vous');
    expect(errorMessage(new HttpErrorResponse({ status: 400 }))).toContain('Filtres invalides');
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('injoignable');
    expect(errorMessage(new Error('boom'))).toContain('Impossible de charger');
    expect(errorMessage(null)).toContain('Impossible de charger');
  });
});
