import { firstValueFrom } from 'rxjs';
import { MockSensorDataSource } from './mock-sensor-data-source';

describe('MockSensorDataSource', () => {
  it('uses the configured silence threshold for the demo sensor states', async () => {
    const sensors = await firstValueFrom(new MockSensorDataSource().getSensors(10));

    expect(sensors.filter((sensor) => sensor.state === 'active')).toHaveLength(3);
    expect(sensors.filter((sensor) => sensor.state === 'silent')).toHaveLength(1);
    expect(sensors.find((sensor) => sensor.id === 7)?.latestValue).toBe(18.6);
  });

  it('provides range-bounded history for numeric and pump-state sensors', async () => {
    const from = new Date('2026-11-17T00:00:00Z');
    const to = new Date('2026-11-18T00:00:00Z');
    const source = new MockSensorDataSource();
    const numeric = await firstValueFrom(source.getHistory(1, from, to));
    const pump = await firstValueFrom(source.getHistory(3, from, to));

    expect(numeric).toHaveLength(24);
    expect(numeric[0].measuredAt).toBe(from.toISOString());
    expect(numeric[numeric.length - 1].measuredAt).toBe(to.toISOString());
    expect(numeric.every((reading) => reading.value !== null && reading.unit === 'bar')).toBe(true);
    expect(pump.some((reading) => reading.pumpState === 'FAULT')).toBe(true);
    expect(await firstValueFrom(source.getHistory(999, from, to))).toEqual([]);
  });
});
