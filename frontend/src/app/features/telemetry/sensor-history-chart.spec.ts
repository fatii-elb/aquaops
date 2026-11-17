import { describe, expect, it } from 'vitest';
import { SensorReading } from './sensor.model';
import { sensorHistoryChart } from './sensor-history-chart';

describe('sensorHistoryChart', () => {
  it('orders numeric measurements and ignores pump states and invalid dates', () => {
    const readings: SensorReading[] = [
      { value: null, unit: null, pumpState: 'NORMAL', measuredAt: '2026-11-17T10:00:00Z' },
      { value: 3, unit: 'bar', pumpState: null, measuredAt: '2026-11-17T12:00:00Z' },
      { value: 1, unit: 'bar', pumpState: null, measuredAt: '2026-11-17T11:00:00Z' },
      { value: 2, unit: 'bar', pumpState: null, measuredAt: 'not-a-date' },
    ];

    const chart = sensorHistoryChart(readings);

    expect(chart.dots.map((dot) => dot.value)).toEqual([1, 3]);
    expect(chart.min).toBe(1);
    expect(chart.max).toBe(3);
    expect(chart.points).toBe('32,160 584,12');
  });

  it('centers a single or constant measurement and returns an empty chart without numeric data', () => {
    const single = sensorHistoryChart([
      { value: 4, unit: 'm', pumpState: null, measuredAt: '2026-11-17T10:00:00Z' },
    ]);
    expect(single.dots).toHaveLength(1);
    expect(single.dots[0].y).toBe(86);

    const constant = sensorHistoryChart([
      { value: 4, unit: 'm', pumpState: null, measuredAt: '2026-11-17T10:00:00Z' },
      { value: 4, unit: 'm', pumpState: null, measuredAt: '2026-11-17T11:00:00Z' },
    ]);
    expect(constant.dots[0].y).toBe(constant.dots[1].y);
    expect(
      sensorHistoryChart([
        { value: null, unit: null, pumpState: 'FAULT', measuredAt: '2026-11-17T10:00:00Z' },
      ]).dots,
    ).toEqual([]);
  });

  it('bounds large series while preserving endpoints and local extrema', () => {
    const readings = Array.from({ length: 10_000 }, (_, index) => ({
      value: index === 4_321 ? 100 : index === 5_432 ? -10 : 1,
      unit: 'bar',
      pumpState: null,
      measuredAt: new Date(Date.parse('2026-11-17T00:00:00Z') + index * 1000).toISOString(),
    }));

    const chart = sensorHistoryChart(readings);

    expect(chart.dots.length).toBeLessThanOrEqual(600);
    expect(chart.dots[0].measuredAt).toBe(readings[0].measuredAt);
    expect(chart.dots[chart.dots.length - 1].measuredAt).toBe(
      readings[readings.length - 1].measuredAt,
    );
    expect(chart.dots.some((dot) => dot.value === 100)).toBe(true);
    expect(chart.dots.some((dot) => dot.value === -10)).toBe(true);
  });
});
