import { firstValueFrom } from 'rxjs';
import { MockZoneDataSource } from './mock-zone-data-source';

describe('MockZoneDataSource', () => {
  it('renvoie 5 zones avec un état valide', async () => {
    const zones = await firstValueFrom(new MockZoneDataSource().getZones());
    expect(zones).toHaveLength(5);
    for (const zone of zones) {
      expect(['normal', 'alerte', 'critique']).toContain(zone.state);
    }
  });

  it('change de valeurs à chaque appel', async () => {
    const source = new MockZoneDataSource();
    const first = await firstValueFrom(source.getZones());
    const second = await firstValueFrom(source.getZones());
    expect(second.map((z) => z.pressureBar)).not.toEqual(first.map((z) => z.pressureBar));
  });

  it('simule une fuite critique dans la zone 3 au bout de quelques appels', async () => {
    const source = new MockZoneDataSource();
    let zone3 = (await firstValueFrom(source.getZones()))[2];
    for (let i = 0; i < 12 && zone3.state !== 'critique'; i++) {
      zone3 = (await firstValueFrom(source.getZones()))[2];
    }
    expect(zone3.state).toBe('critique');
  });
});
