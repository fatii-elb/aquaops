import { computeZoneState } from './zone-state';
import { ZoneValues } from './zone.model';

const normal: ZoneValues = { pressureBar: 2.6, flowM3h: 30, levelM: 3.5, pumpState: 'NORMAL' };

describe('computeZoneState', () => {
  it('renvoie normal quand tout est dans les limites', () => {
    expect(computeZoneState(normal)).toBe('normal');
  });

  it('ignore les valeurs absentes', () => {
    expect(
      computeZoneState({ pressureBar: null, flowM3h: null, levelM: null, pumpState: null }),
    ).toBe('normal');
  });

  it('renvoie alerte pour une pression basse', () => {
    expect(computeZoneState({ ...normal, pressureBar: 1.8 })).toBe('alerte');
  });

  it('renvoie alerte pour un débit élevé', () => {
    expect(computeZoneState({ ...normal, flowM3h: 42 })).toBe('alerte');
  });

  it('renvoie critique pour une pression très basse', () => {
    expect(computeZoneState({ ...normal, pressureBar: 1.1 })).toBe('critique');
  });

  it('renvoie critique pour un débit très élevé', () => {
    expect(computeZoneState({ ...normal, flowM3h: 48 })).toBe('critique');
  });

  it('renvoie critique pour un réservoir presque vide', () => {
    expect(computeZoneState({ ...normal, levelM: 0.5 })).toBe('critique');
  });

  it('renvoie critique pour une pompe en défaut', () => {
    expect(computeZoneState({ ...normal, pumpState: 'FAULT' })).toBe('critique');
  });

  it("une valeur exactement sur le seuil n'est pas en alerte", () => {
    expect(computeZoneState({ ...normal, pressureBar: 2.0 })).toBe('normal');
  });
});
