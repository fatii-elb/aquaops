import { firstValueFrom } from 'rxjs';
import { MockSensorDataSource } from './mock-sensor-data-source';

describe('MockSensorDataSource', () => {
  it('uses the configured silence threshold for the demo sensor states', async () => {
    const sensors = await firstValueFrom(new MockSensorDataSource().getSensors(10));

    expect(sensors.filter((sensor) => sensor.state === 'active')).toHaveLength(3);
    expect(sensors.filter((sensor) => sensor.state === 'silent')).toHaveLength(1);
    expect(sensors.find((sensor) => sensor.id === 7)?.latestValue).toBe(18.6);
  });
});
